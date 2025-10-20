package net.democracycraft.democracyelections.ui.list;

import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.democracyelections.api.model.Election;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.api.ui.ParentMenu;
import net.democracycraft.democracyelections.ui.ChildMenuImp;
import net.democracycraft.democracyelections.ui.manager.ElectionManagerMenu;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

/**
 * Child dialog to view an election summary and open its manager.
 */
public class ElectionListItemMenu extends ChildMenuImp {

    private final ElectionsService electionsService;
    private final int electionId;

    /**
     * @param player player opening the dialog
     * @param parent parent menu
     * @param electionsService elections service
     * @param electionId election identifier
     */
    public ElectionListItemMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId) {
        super(player, parent, "election_list_item_" + electionId);
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }

    private io.papermc.paper.dialog.Dialog build() {
        Election e = electionsService.getElection(electionId).orElse(null);
        AutoDialog.Builder b = getAutoDialogBuilder();
        b.title(title(e == null ? "Election" : ("Election #" + e.getId())));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);

        if (e == null) {
            b.addBody(DialogBody.plainMessage(info("Election not found.")));
            b.button(warn("Back"), ctx -> ctx.player().closeInventory());
            return b.build();
        }

        b.addBody(DialogBody.plainMessage(Component.newline()
                .append(key("Title: ")).append(Component.text(e.getTitle()).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                .appendNewline().append(key("Status: ")).append(info(e.getStatus().name()))
                .appendNewline().append(key("Voters: ")).append(info(String.valueOf(e.getVoterCount())))
                .appendNewline().append(key("Candidates: ")).append(info(String.valueOf(e.getCandidates().size())))
                .appendNewline().append(key("Polls: ")).append(info(String.valueOf(e.getPolls().size())))
        ));

        b.button(warn("Open Manager"), ctx -> new ElectionManagerMenu(ctx.player(), electionsService, electionId).open());
        b.button(warn("Back"), ctx -> ctx.player().closeInventory());
        return b.build();
    }
}
