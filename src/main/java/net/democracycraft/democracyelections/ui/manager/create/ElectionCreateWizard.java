package net.democracycraft.democracyelections.ui.manager.create;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.ui.ParentMenuImp;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

/**
 * Parent wizard to create a new election.
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

    private Dialog build() {
        AutoDialog.Builder b = getAutoDialogBuilder();
        b.title(title("Create Election"));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);

        b.addBody(DialogBody.plainMessage(info("This wizard will guide you through creating a new election.")));
        b.addBody(DialogBody.plainMessage(info("Steps: Basics -> Duration -> Requirements -> System & Minimum -> Confirm")));

        b.button(warn("Start"), ctx -> new ElectionCreateBasicsMenu(ctx.player(), this, draft).open());
        b.button(info("Cancel"), ctx -> ctx.player().closeInventory());
        return b.build();
    }
}
