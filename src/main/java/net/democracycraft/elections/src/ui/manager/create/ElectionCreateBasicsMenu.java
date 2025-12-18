package net.democracycraft.elections.src.ui.manager.create;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.src.ui.ChildMenuImp;
import net.democracycraft.elections.api.ui.AutoDialog;
import net.democracycraft.elections.src.ui.list.ElectionPreviewMenu;
import org.bukkit.entity.Player;

import java.io.Serializable;

/**
 * Create wizard step: Basics (title). All texts are configurable via data/menus/ElectionCreateBasicsMenu.yml.
 */
public class ElectionCreateBasicsMenu extends ChildMenuImp {

    enum Keys { TITLE }

    private final ElectionCreateWizard wizard;
    private final DraftElection draft;

    /**
     * @param player player opening the dialog
     * @param parent wizard parent
     * @param draft draft state container
     */
    public ElectionCreateBasicsMenu(Player player, ParentMenu parent, DraftElection draft) {
        super(player, parent, "create_basics");
        this.wizard = (ElectionCreateWizard) parent;
        this.draft = draft;
        this.setDialog(build());
    }

    /** Config DTO for this step. */
    public static class Config implements Serializable {
        public String title = "<gold><bold>Create: Basics</bold></gold>";
        public String instruction = "<gray>Enter the election title.</gray>";
        public String titleLabel = "<aqua>Title</aqua>";
        public String nextBtn = "<green><bold>Next</bold></green>";
        public String cancelBtn = "<red><bold>Cancel</bold></red>";
        public String emptyMsg = "<red><bold>Title cannot be empty.</bold></red>";
        public String yamlHeader = "ElectionCreateBasicsMenu configuration. Placeholders: %player%.";
        /** Whether the dialog can be closed with Escape. */
        public boolean canCloseWithEscape = true;
        public Config() {}

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(Config.class, "ElectionCreateBasicsMenu.yml", new Config().yamlHeader);
            yml.loadOrCreate(Config::new);
        }
    }

    private Dialog build() {
        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        dialogBuilder.title(miniMessage(config.title));
        dialogBuilder.canCloseWithEscape(config.canCloseWithEscape);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(config.instruction)));
        dialogBuilder.addInput(DialogInput.text(Keys.TITLE.name(), miniMessage(config.titleLabel)).labelVisible(true).build());

        dialogBuilder.buttonWithPlayer(miniMessage(config.nextBtn), null, (playerActor, response) -> {
            String titleInput = response.getText(Keys.TITLE.name());
            if (titleInput == null || titleInput.isBlank()) {
                playerActor.sendMessage(miniMessage(config.emptyMsg));
                new ElectionCreateBasicsMenu(playerActor, wizard, draft).open();
                return;
            }
            draft.title = titleInput;
            new ElectionCreateDurationMenu(playerActor, wizard, draft).open();
        });

        dialogBuilder.button(miniMessage(config.cancelBtn), context -> context.player().closeInventory());
        return dialogBuilder.build();
    }
}
