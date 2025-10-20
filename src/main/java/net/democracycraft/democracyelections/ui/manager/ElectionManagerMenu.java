package net.democracycraft.democracyelections.ui.manager;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.democracyelections.DemocracyElections;
import net.democracycraft.democracyelections.api.model.Election;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.ui.ParentMenuImp;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Parent dialog to manage a specific election, summarizing data and navigating to child menus.
 */
public class ElectionManagerMenu extends ParentMenuImp {

    private final ElectionsService electionService;
    private final int electionId;
    private final DemocracyElections plugin = DemocracyElections.getInstance();

    /**
     * @param player player opening the dialog
     * @param electionsService elections service
     * @param electionId election identifier
     */
    public ElectionManagerMenu(Player player, ElectionsService electionsService, int electionId) {
        super(player, "election_manager_" + electionId);
        this.electionService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }

    private Dialog build() {
        Optional<Election> opt = electionService.getElection(electionId);
        if (opt.isEmpty()) {
            AutoDialog.Builder b = getAutoDialogBuilder();
            b.title(title("Election Manager"));
            b.addBody(DialogBody.plainMessage(neg("Election not found.")));
            return b.build();
        }
        Election election = opt.get();

        AutoDialog.Builder builder = getAutoDialogBuilder();
        builder.title(title("Election Manager: " + election.getTitle() + " (#" + election.getId() + ")"));
        builder.canCloseWithEscape(true);
        builder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        String status = election.getStatus().name();
        int voters = election.getVoterCount();
        int polls = election.getPolls().size();
        int candidates = election.getCandidates().size();
        Integer durationDays = election.getDurationDays();
        var durationTime = election.getDurationTime();
        String durationStr = (durationDays == null && durationTime == null) ? "Never" : (String.format("%sd %sh %sm", durationDays == null ? 0 : durationDays, durationTime == null ? 0 : durationTime.hour(), durationTime == null ? 0 : durationTime.minute()));

        builder.addBody(DialogBody.plainMessage(Component.newline()
                .append(key("Status: ")).append(info(status))
                .appendNewline().append(key("Voters: ")).append(info(String.valueOf(voters)))
                .appendNewline().append(key("Polls: ")).append(info(String.valueOf(polls)))
                .appendNewline().append(key("Candidates: ")).append(info(String.valueOf(candidates)))
                .appendNewline().append(key("Closes: ")).append(info(durationStr))
        ));

        // Overview/info
        builder.button(good("Ballots submitted: " + election.getBallots().size()), ctx -> new ElectionManagerMenu(ctx.player(), electionService, electionId).open());

        // Navigation to child menus
        builder.button(warn("Edit Title"), ctx -> new TitleEditMenu(ctx.player(), this, electionService, electionId).open());
        builder.button(warn("Polls (" + polls + ")"), ctx -> new PollsConfigMenu(ctx.player(), this, plugin, electionService, electionId).open());
        builder.button(warn("Duration"), ctx -> new DurationMenu(ctx.player(), this, electionService, electionId).open());

        // Status toggle
        builder.button(warn("Status: " + election.getStatus()), ctx -> {
            boolean ok;
            switch (election.getStatus()) {
                case OPEN -> ok = electionService.closeElection(electionId);
                case CLOSED, DELETED -> ok = electionService.openElection(electionId);
                default -> ok = electionService.openElection(electionId);
            }
            ctx.player().sendMessage(ok ? good("Status updated.") : neg("Could not update status."));
            new ElectionManagerMenu(ctx.player(), electionService, electionId).open();
        });

        builder.button(warn("System & Minimum"), ctx -> new SystemAndMinimumMenu(ctx.player(), this, electionService, electionId).open());
        builder.button(warn("Candidates (" + candidates + ")"), ctx -> new CandidatesMenu(ctx.player(), this, electionService, electionId).open());
        builder.button(warn("Requirements"), ctx -> new RequirementsMenu(ctx.player(), this, electionService, electionId).open());

        // Close
        builder.button(neg("Close"), ctx -> {});

        return builder.build();
    }
}
