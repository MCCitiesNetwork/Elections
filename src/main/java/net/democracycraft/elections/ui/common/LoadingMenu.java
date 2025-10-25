package net.democracycraft.elections.ui.common;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.ui.ChildMenuImp;
import net.democracycraft.elections.ui.dialog.AutoDialog;
import org.bukkit.entity.Player;

import java.io.Serializable;

/**
 * Simple loading screen child menu with MiniMessage-configurable texts.
 */
public class LoadingMenu extends ChildMenuImp {

    public LoadingMenu(Player player, ParentMenu parent) {
        super(player, parent, "loading");
        this.setDialog(build());
    }

    public static class Config implements Serializable {
        public String title = "<gold><bold>Loading</bold></gold>";
        public String message = "<gray><italic>Loading…</italic></gray>";
        public String yamlHeader = "LoadingMenu configuration. Placeholders: %player%.";
        public Config() {}
    }

    private Dialog build() {
        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);
        dialogBuilder.title(miniMessage(config.title));
        dialogBuilder.canCloseWithEscape(true);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);
        dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(config.message)));
        return dialogBuilder.build();
    }
}

