package net.democracycraft.elections.internal.ui.common;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.elections.api.ui.AutoDialog;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.internal.data.Dto;
import net.democracycraft.elections.internal.ui.ChildMenuImp;
import net.democracycraft.elections.internal.util.text.MiniMessageUtil;
import net.kyori.adventure.text.Component;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simple error dialog menu that shows one or more error messages and an acknowledge button.
 * When the player acknowledges, the previous menu is reopened and any external state such
 * as ballot selections is preserved by design (this menu does not touch sessions).
 */
public class ErrorMenu extends ChildMenuImp {

    private final List<String> messages;

    /**
     * Configuration properties for the error dialog, stored in a YAML file per menu id.
     */
    public static class Config implements Dto {
        /** Default dialog title if none is configured. */
        public String title = "<red><bold>Error</bold></red>";
        /** Default generic message when no specific message is provided. */
        public String genericMessage = "<red>An error has occurred.</red>";
        /** Label for the acknowledge button. */
        public String acknowledgeButton = "<yellow><bold>OK</bold></yellow>";
        /** Optional prefix format for each extra detail line, placeholder: %detail%. */
        public String detailPrefix = "<gray>- %detail%</gray>";
        /**
         * Header comment describing what placeholders are supported by this menu
         * configuration file.
         */
        public String yamlHeader = "ErrorMenu configuration. Placeholders: %detail%.";
        /** Whether the dialog can be closed with Escape. */
        public boolean canCloseWithEscape = true;

        public Config() {
        }

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(ErrorMenu.Config.class, "ErrorMenu.yml", new ErrorMenu.Config().yamlHeader);
            yml.loadOrCreate(ErrorMenu.Config::new);
        }
    }



    /**
     * Creates an error dialog menu with a single message line.
     *
     * @param player   player to show the dialog to
     * @param parent   parent menu to reopen when acknowledging
     * @param id       unique menu identifier (used for YAML config file)
     * @param message  error message to display; if {@code null}, a generic
     *                 message from the configuration is used
     */
    public ErrorMenu(Player player, ParentMenu parent, String id, String message) {
        this(player, parent, id, message == null ? null : List.of(message));
    }

    /**
     * Creates an error dialog menu with one or more message lines.
     *
     * @param player   player to show the dialog to
     * @param parent   parent menu to reopen when acknowledging
     * @param id       unique menu identifier (used for YAML config file)
     * @param messages list of message components; if {@code null} or empty,
     *                 a generic message from the configuration is used
     */
    public ErrorMenu(Player player, ParentMenu parent, String id, List<String> messages) {
        super(player, parent, id);
        this.messages = messages == null ? List.of() : new ArrayList<>(messages);
        this.setDialog(build());
    }

    /**
     * Builds the dialog that shows the configured error messages and an
     * acknowledge button that reopens the parent menu.
     *
     * @return dialog instance ready to be shown
     */
    private Dialog build() {
        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);
        AutoDialog.Builder builder = getAutoDialogBuilder();

        builder.title(MiniMessageUtil.parseOrPlain(config.title));
        builder.canCloseWithEscape(config.canCloseWithEscape);
        builder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        if (messages.isEmpty()) {
            // Fallback to the generic message from configuration.
            builder.addBody(DialogBody.plainMessage(MiniMessageUtil.parseOrPlain(config.genericMessage)));
        } else {
            // First line: either the first provided message or the generic text
            // followed by optional detail lines.
            boolean first = true;
            for (String raw : messages) {
                if (first) {
                    builder.addBody(DialogBody.plainMessage(MiniMessageUtil.parseOrPlain(raw)));
                    first = false;
                } else {
                    Map<String, String> ph = Map.of("%detail%", raw);
                    Component detail = MiniMessageUtil.parseOrPlain(applyPlaceholders(config.detailPrefix, ph));
                    builder.addBody(DialogBody.plainMessage(detail));
                }
            }
        }

        builder.button(MiniMessageUtil.parseOrPlain(config.acknowledgeButton), ctx -> getParentMenu().open());

        return builder.build();
    }
}
