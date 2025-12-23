package net.democracycraft.elections.internal.ui;

import io.papermc.paper.dialog.Dialog;
import net.democracycraft.elections.api.ui.Menu;
import net.democracycraft.elections.internal.util.text.MiniMessageUtil;
import net.democracycraft.elections.internal.util.yml.AutoYML;
import net.democracycraft.elections.internal.util.config.DataFolder;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base implementation for Menus providing common dialog wiring, MiniMessage helpers,
 * per-menu YAML configuration access and simple placeholder resolution.
 */
public abstract class MenuImp implements Menu {
    protected final Player player;
    protected final String id;
    protected Dialog dialog = null;

    // Cache of AutoYML instances by absolute file key to avoid concurrent writers to the same file
    private static final ConcurrentHashMap<String, AutoYML<?>> MENU_YML_CACHE = new ConcurrentHashMap<>();

    /**
     * Constructs a new menu instance.
     *
     * @param player the player opening the menu
     * @param id     the unique id for this menu instance
     */
    public MenuImp(Player player, String id) {
        this.player = player;
        this.id = id;
    }

    @Override
    public String getId(){
        return id;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Nullable
    @Override
    public Dialog getDialog() {
        return dialog;
    }

    @Override
    public void setDialog(Dialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void open() {
        player.showDialog(dialog);
    }

    /**
     * Returns or creates a cached AutoYML instance for a given menu config class.
     * The file will be located under DataFolder.MENUS using the provided fileName.
     *
     * @param clazz    config root class (Serializable POJO)
     * @param fileName base file name without extension
     * @param header   optional YAML header comment
     * @return cached AutoYML instance typed to clazz
     */
    @SuppressWarnings("unchecked")
    protected static  <T extends Serializable> AutoYML<T> getOrCreateMenuYml(Class<T> clazz, String fileName, String header) {
        Objects.requireNonNull(clazz, "clazz");
        Objects.requireNonNull(fileName, "fileName");
        String cacheKey = DataFolder.MENUS.getPath() + "/" + (fileName.endsWith(".yml") ? fileName : fileName + ".yml");
        return (AutoYML<T>) MENU_YML_CACHE.computeIfAbsent(cacheKey,
                k -> AutoYML.create(clazz, fileName, DataFolder.MENUS, header));
    }

    /**
     * Basic placeholder map to all menus.
     * Currently supports:
     * - %player%: player name
     *
     * @return placeholder values
     */
    protected Map<String, String> placeholders() {
        return Map.of(
                "%player%", player.getName()
        );
    }

    /**
     * Resolves placeholders in a template using provided values merged with common placeholders.
     *
     * @param template template string that may contain placeholders like %player%
     * @param extras   additional placeholders, may be null
     * @return component with resolved placeholders
     */
    protected Component miniMessage(String template, @Nullable Map<String, String> extras) {
        return MiniMessageUtil.parseOrPlain(template, extras == null ? placeholders() : merge(placeholders(), extras));
    }

    /**
     * Formats a candidate name with special coloring for specific keywords.
     * "Aye" -> Green Bold
     * "Nay" -> Red Bold
     *
     * @param name the candidate name
     * @return the formatted name as a MiniMessage string, or the original name
     */
    protected String formatCandidateName(String name) {
        if (name == null) return "";
        if (name.equalsIgnoreCase("aye")) {
            return "<green><bold>Aye</bold></green>";
        }
        if (name.equalsIgnoreCase("nay")) {
            return "<red><bold>Nay</bold></red>";
        }
        return name;
    }

    /**
     * Formats the candidate party.
     * If the candidate name is "Aye" or "Nay", the party is hidden (returns empty string).
     * Otherwise, returns the provided party string.
     *
     * @param name  the candidate name
     * @param party the candidate party (already resolved with defaults if needed)
     * @return the formatted party or empty string
     */
    protected String formatCandidateParty(String name, String party) {
        if (name != null && (name.equalsIgnoreCase("aye") || name.equalsIgnoreCase("nay"))) {
                        if (party.equalsIgnoreCase("independent")) {

                return "";
            }

        }
        return party;
    }

    private Map<String, String> merge(Map<String, String> base, Map<String, String> extras) {
        Map<String, String> merged = new ConcurrentHashMap<>(base);
        merged.putAll(extras);
        return merged;
    }

    protected String applyPlaceholders(String template, Map<String, String> placeholdersP) {
        if (template == null) return "";
        String resolved = template;
        // Merge extras over common values
        Map<String, String> all = placeholdersP == null ? placeholders() : merge(placeholders(), placeholdersP);
        // Then apply all placeholders
        for (Map.Entry<String, String> entry : all.entrySet()) {
            String keyToken = entry.getKey();
            String valueText = entry.getValue() == null ? "" : entry.getValue();
            resolved = resolved.replace(keyToken, valueText);
        }
        return resolved;
    }

    /**
     * Parses a MiniMessage string safely, falling back to a plain text component.
     * @param text MiniMessage string
     * @return component
     */
    protected Component miniMessage(String text) {
        return MiniMessageUtil.parseOrPlain(text);
    }
}
