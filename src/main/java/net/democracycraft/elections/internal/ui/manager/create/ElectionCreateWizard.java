package net.democracycraft.elections.internal.ui.manager.create;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.internal.ui.ParentMenuImp;
import net.democracycraft.elections.api.ui.AutoDialog;
import org.bukkit.entity.Player;

import java.io.Serializable;

/**
 * Parent wizard to create a new election.
 * All texts are configurable via data/menus/ElectionCreateWizard.yml with placeholders.
 */
public class ElectionCreateWizard extends ParentMenuImp {

    private final ElectionsService electionsService;
    private final DraftElection draft = new DraftElection();

    /**
     * @param player player opening the wizard
     * @param electionsService elections service to persist created election
     */
    public ElectionCreateWizard(Player player, ElectionsService electionsService) {
        super(player, "election_create_wizard");
        this.electionsService = electionsService;
        this.setDialog(build());
    }

    public DraftElection getDraft() { return draft; }
    public ElectionsService getService() { return electionsService; }

    /** Config DTO for this menu. */
    public static class Config implements Serializable {
        public String title = "<gold><bold>Create Election</bold></gold>";
        public String intro1 = "<gray>This wizard will guide you through creating a new election.</gray>";
        public String intro2 = "<gray>Steps: Basics -> Duration -> Requirements -> System & Minimum -> Confirm</gray>";
        public String startBtn = "<yellow><bold>Start</bold></yellow>";
        public String cancelBtn = "<gray>Cancel</gray>";
        public String yamlHeader = "ElectionCreateWizard configuration.";
        /** Whether the dialog can be closed with Escape. */
        public boolean canCloseWithEscape = true;
        public Config() {}

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(Config.class, "ElectionCreateWizard.yml", new Config().yamlHeader);
            yml.loadOrCreate(Config::new);
        }
    }

    private Dialog build() {
        AutoDialog.Builder builder = getAutoDialogBuilder();
        var yml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), null);
        Config config = yml.loadOrCreate(Config::new);

        builder.title(miniMessage(config.title, null));
        builder.canCloseWithEscape(config.canCloseWithEscape);
        builder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        builder.addBody(DialogBody.plainMessage(miniMessage(config.intro1, null)));
        builder.addBody(DialogBody.plainMessage(miniMessage(config.intro2, null)));

        builder.button(miniMessage(config.startBtn, null), context -> new ElectionCreateBasicsMenu(context.player(), this, draft).open());
        builder.button(miniMessage(config.cancelBtn, null), context -> context.player().closeInventory());
        return builder.build();
    }
}
