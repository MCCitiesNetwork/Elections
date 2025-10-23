package net.democracycraft.democracyelections.ui.vote;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.democracyelections.api.model.Candidate;
import net.democracycraft.democracyelections.api.model.Election;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.api.ui.ParentMenu;
import net.democracycraft.democracyelections.data.VotingSystem;
import net.democracycraft.democracyelections.ui.ChildMenuImp;
import net.democracycraft.democracyelections.util.HeadUtil;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Per-candidate vote menu. Adapts to system:
 * - BLOCK: toggle selected for this candidate.
 * - PREFERENTIAL: choose rank (1..N) for this candidate.
 * Persists state in BallotSessions and returns to the candidate list.
 */
public class CandidateVoteMenu extends ChildMenuImp {

    private final ElectionsService svc;
    private final int electionId;
    private final int candidateId;

    public CandidateVoteMenu(Player player, ParentMenu parent, ElectionsService svc, int electionId, int candidateId) {
        super(player, parent, "ballot_cand_" + electionId + "_" + candidateId);
        this.svc = svc;
        this.electionId = electionId;
        this.candidateId = candidateId;
        this.setDialog(build());
    }

    private Dialog build() {
        Optional<Election> opt = svc.getElection(electionId);
        AutoDialog.Builder b = getAutoDialogBuilder();
        if (opt.isEmpty()) {
            b.title(title("Vote"));
            b.addBody(DialogBody.plainMessage(neg("Election not found.")));
            return b.build();
        }
        Election e = opt.get();
        VotingSystem system = e.getSystem();
        BallotSessions.Session session = BallotSessions.get(getPlayer().getUniqueId(), electionId, system);
        session.setSystem(system);

        // find candidate
        Candidate cand = null;
        List<Candidate> list = e.getCandidates();
        for (Candidate c : list) if (c.getId() == candidateId) { cand = c; break; }
        if (cand == null) {
            b.title(title("Vote"));
            b.addBody(DialogBody.plainMessage(neg("Candidate not found.")));
            return b.build();
        }

        b.title(title(cand.getName()));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);

        // Head item
        HeadUtil.updateHeadItemBytesAsync(svc, electionId, cand.getId(), cand.getName());
        ItemStack headItem = HeadUtil.headFromBytesOrName(svc, electionId, cand.getId(), cand.getName());
        b.addBody(DialogBody.item(headItem).showTooltip(true).build());

        if (system == VotingSystem.BLOCK) {
            boolean initial = session.isSelected(candidateId);
            String selKey = "SEL_" + candidateId;
            b.addInput(DialogInput.bool(selKey, info("Select")).initial(initial).build());
            b.button(info("Save"), ctx -> {
                Boolean v = ctx.response().getBoolean(selKey);
                session.setSelected(candidateId, v != null && v);
                new CandidateListMenu(ctx.player(), getParentMenu(), svc, electionId).open();
            });
        } else {
            int maxRank = Math.max(1, list.size());
            Integer initialRank = session.getRank(candidateId);
            String rankKey = "RANK_" + candidateId;
            int initRank = (initialRank != null && initialRank >= 1 && initialRank <= maxRank) ? initialRank : 1;
            var range = DialogInput
                    .numberRange(rankKey, info("Rank (1.."+maxRank+")"), 1f, (float) maxRank)
                    .step(1f)
                    .initial((float) initRank);
            b.addInput(range.build());
            b.button(info("Save"), ctx -> {
                Float v = ctx.response().getFloat(rankKey);
                if (v == null || v < 1 || v > maxRank) {
                    ctx.player().sendMessage(neg("Invalid rank."));
                    new CandidateVoteMenu(ctx.player(), getParentMenu(), svc, electionId, candidateId).open();
                    return;
                }
                session.setRank(candidateId, Math.round(v));
                new CandidateListMenu(ctx.player(), getParentMenu(), svc, electionId).open();
            });
            b.button(warn("Clear rank"), ctx -> { session.clearRank(candidateId); new CandidateListMenu(ctx.player(), getParentMenu(), svc, electionId).open(); });
        }

        b.button(neg("Back"), ctx -> new CandidateListMenu(ctx.player(), getParentMenu(), svc, electionId).open());
        return b.build();
    }
}
