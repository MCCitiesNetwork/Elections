package net.democracycraft.democracyelections.ui.vote;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.democracyelections.api.model.Election;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.data.VotingSystem;
import net.democracycraft.democracyelections.ui.ParentMenuImp;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Intro screen for a ballot. Summarizes the election and explains how to vote,
 * offering a button to proceed to the candidate list menu.
 */
public class BallotIntroMenu extends ParentMenuImp {

    private final ElectionsService svc;
    private final int electionId;

    public BallotIntroMenu(Player player, ElectionsService svc, int electionId) {
        super(player, "ballot_intro_" + electionId);
        this.svc = svc;
        this.electionId = electionId;
        this.setDialog(build());
    }

    private Dialog build() {
        Optional<Election> opt = svc.getElection(electionId);
        AutoDialog.Builder b = getAutoDialogBuilder();
        if (opt.isEmpty()) {
            b.title(title("Ballot"));
            b.addBody(DialogBody.plainMessage(neg("Election not found.")));
            return b.build();
        }
        Election e = opt.get();
        b.title(title(e.getTitle() + " Ballot"));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);

        String sys = e.getSystem().name();
        String min = String.valueOf(e.getMinimumVotes());
        String how = e.getSystem() == VotingSystem.BLOCK
                ? "You have exactly %s votes. Order does not matter.".formatted(min)
                : "Rank candidates. You can submit after at least %s preferences.".formatted(min);

        b.addBody(DialogBody.plainMessage(Component.newline()
                .append(key("System: ")).append(info(sys))
                .appendNewline().append(key("Minimum: ")).append(info(min))
                .appendNewline().append(key("How to vote: ")).append(info(how))
        ));

        b.button(good("Start Voting"), ctx -> new CandidateListMenu(ctx.player(), this, svc, electionId).open());
        b.button(neg("Close"), ctx -> {});
        return b.build();
    }
}
