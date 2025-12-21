package net.democracycraft.elections.src.ui.common;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.elections.api.ui.AutoDialog;
import net.democracycraft.elections.api.ui.Menu;
import net.democracycraft.elections.src.ui.MenuImp;
import net.democracycraft.elections.src.util.text.MiniMessageUtil;
import org.bukkit.entity.Player;

import java.io.Serializable;
import java.util.function.Consumer;

/**
 * Generic confirmation menu with Confirm and Cancel buttons.
 * Executes a consumer on confirmation and returns to the previous menu on cancel/completion.
 */
public class ConfirmationMenu extends MenuImp {

    private final Menu previousMenu;
    private final String message;
    private final Consumer<Player> onConfirm;

    /**
     * @param player       Player opening the menu
     * @param previousMenu Menu to return to on cancel
     * @param message      Message to display in the dialog body
     * @param onConfirm    Action to execute when confirmed
     */
    public ConfirmationMenu(Player player, Menu previousMenu, String message, Consumer<Player> onConfirm) {
        super(player, "confirmation");
        this.previousMenu = previousMenu;
        this.message = message;
        this.onConfirm = onConfirm;
        this.setDialog(build());
    }

    @Override
    public AutoDialog.Builder getAutoDialogBuilder() {
        return AutoDialog.builder();
    }

    public static class Config implements Serializable {
        public String title = "<red><bold>Confirmation Required</bold></red>";
        public String confirmBtn = "<green><bold>Confirm</bold></green>";
        public String cancelBtn = "<red><bold>Cancel</bold></red>";
        public String defaultMessage = "<gray>Are you sure you want to proceed?</gray>";

        public String yamlHeader = "ConfirmationMenu configuration.";
        public boolean canCloseWithEscape = true;

        public Config() {}

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(Config.class, "ConfirmationMenu.yml", new Config().yamlHeader);
            yml.loadOrCreate(Config::new);
        }
    }

    private Dialog build() {
        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        AutoDialog.Builder builder = AutoDialog.builder();

        builder.title(MiniMessageUtil.parseOrPlain(config.title));
        builder.canCloseWithEscape(config.canCloseWithEscape);
        builder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        builder.addBody(DialogBody.plainMessage(MiniMessageUtil.parseOrPlain(message != null ? message : config.defaultMessage)));

        builder.button(MiniMessageUtil.parseOrPlain(config.confirmBtn), context -> {
            onConfirm.accept(context.player());
        });

        builder.button(MiniMessageUtil.parseOrPlain(config.cancelBtn), context -> {
            previousMenu.open();
        });

        return builder.build();
    }
}

