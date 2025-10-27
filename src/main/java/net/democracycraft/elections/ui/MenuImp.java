package net.democracycraft.elections.ui;

import io.papermc.paper.dialog.Dialog;
import net.democracycraft.elections.api.ui.Menu;
import net.democracycraft.elections.util.text.MiniMessageUtil;
import net.democracycraft.elections.util.yml.AutoYML;
import net.democracycraft.elections.util.config.DataFolder;
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
    protected <T extends Serializable> AutoYML<T> getOrCreateMenuYml(Class<T> clazz, String fileName, String header) {
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
     * @return resolved string (never null)
     */
    protected String applyPlaceholders(String template, @Nullable Map<String, String> extras) {
        if (template == null) return "";
        String resolved = template;
        // Merge extras over common values
        Map<String, String> common = placeholders();
        if (extras != null) {
            // First apply extras for menu-specific values
            for (Map.Entry<String, String> entry : extras.entrySet()) {
                String keyToken = entry.getKey();
                String valueText = entry.getValue() == null ? "" : entry.getValue();
                resolved = resolved.replace(keyToken, valueText);
            }
        }
        // Then apply common placeholders
        for (Map.Entry<String, String> entry : common.entrySet()) {
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

    /**
     * Applies placeholders then parses MiniMessage safely, with fallback to plain text.
     * @param template MiniMessage template string
     * @param placeholdersMap placeholders map (may be null)
     * @return component
     */
    protected Component miniMessage(String template, @Nullable Map<String, String> placeholdersMap) {
        return MiniMessageUtil.parseOrPlain(applyPlaceholders(template, placeholdersMap));
    }
}
