package net.democracycraft.elections.src.ui.common;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.src.ui.ChildMenuImp;
import net.democracycraft.elections.api.ui.AutoDialog;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.io.Serializable;

/**
 * Simple loading screen child menu with MiniMessage-configurable texts.
 * Now supports providing a custom title and message per usage site.
 */
public class LoadingMenu extends ChildMenuImp {

    private final Component titleOverride;
    private final Component messageOverride;

    /**
     * Creates a LoadingMenu using YAML-configured title and message.
     *
     * @param player the player opening the menu
     * @param parent the parent menu
     */
    public LoadingMenu(Player player, ParentMenu parent) {
        super(player, parent, "loading");
        this.titleOverride = null;
        this.messageOverride = null;
        this.setDialog(build());
    }

    /**
     * Creates a LoadingMenu using a custom title, and YAML-configured message.
     *
     * @param player the player opening the menu
     * @param parent the parent menu
     * @param title custom pre-built Component title
     */
    public LoadingMenu(Player player, ParentMenu parent, Component title) {
        super(player, parent, "loading");
        this.titleOverride = title;
        this.messageOverride = null;
        this.setDialog(build());
    }

    /**
     * Creates a LoadingMenu using a custom title and custom message.
     *
     * @param player the player opening the menu
     * @param parent the parent menu
     * @param title custom pre-built Component title
     * @param message custom pre-built Component message shown in the body
     */
    public LoadingMenu(Player player, ParentMenu parent, Component title, Component message) {
        super(player, parent, "loading");
        this.titleOverride = title;
        this.messageOverride = message;
        this.setDialog(build());
    }

    public static class Config implements Serializable {
        public String title = "<gold><bold>Loading</bold></gold>";
        public String message = "<gray><italic>Loading…</italic></gray>";
        public String yamlHeader = "LoadingMenu configuration, most titles/messages are established per menu. Placeholders: %player%";
        public boolean canCloseWithEscape = true;
        public Config() {}

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(Config.class, "LoadingMenu.yml", new Config().yamlHeader);
            yml.loadOrCreate(Config::new);
        }
    }

    private Dialog build() {
        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);
        // Title: use override if provided
        dialogBuilder.title(titleOverride != null ? titleOverride : miniMessage(config.title));
        dialogBuilder.canCloseWithEscape(true);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);
        dialogBuilder.canCloseWithEscape(config.canCloseWithEscape);
        // Message body: use override if provided
        Component body = messageOverride != null ? messageOverride : miniMessage(config.message);
        dialogBuilder.addBody(DialogBody.plainMessage(body));
        return dialogBuilder.build();
    }
}
