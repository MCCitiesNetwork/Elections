package net.democracycraft.democracyelections.ui.vote;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.democracyelections.api.model.Candidate;
import net.democracycraft.democracyelections.api.model.Election;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.api.ui.ParentMenu;
import net.democracycraft.democracyelections.data.VotingSystem;
import net.democracycraft.democracyelections.ui.ChildMenuImp;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Candidate list menu: shows buttons for each candidate and allows navigating to a per-candidate screen.
 * Also provides Submit/Clear/Back actions and persists state via BallotSessions.
 */
public class CandidateListMenu extends ChildMenuImp {

    private final ElectionsService svc;
    private final int electionId;

    public CandidateListMenu(Player player, ParentMenu parent, ElectionsService svc, int electionId) {
        super(player, parent, "ballot_list_" + electionId);
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
        VotingSystem system = e.getSystem();
        BallotSessions.Session session = BallotSessions.get(getPlayer().getUniqueId(), electionId, system);
        session.setSystem(system);

        b.title(title(e.getTitle() + " Candidates"));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);

        // instructions + summary
        if (system == VotingSystem.BLOCK) {
            b.addBody(DialogBody.plainMessage(Component.newline()
                    .append(info("Select exactly "+Math.max(1, e.getMinimumVotes())+" candidates.\n"))
                    .append(key("Selected: ")).append(info(String.valueOf(session.selectedCount())))));
        } else {
            b.addBody(DialogBody.plainMessage(Component.newline()
                    .append(info("Assign unique ranks starting at 1. Minimum preferences: "+Math.max(1, e.getMinimumVotes())+"\n"))));
        }

        // candidate buttons with state indicator
        List<Candidate> cs = e.getCandidates();
        for (Candidate c : cs) {
            String state;
            if (system == VotingSystem.BLOCK) {
                state = session.isSelected(c.getId()) ? "[Selected]" : "";
            } else {
                Integer r = session.getRank(c.getId());
                state = r != null ? ("["+r+"]") : "";
            }
            String label = (state.isEmpty() ? c.getName() : c.getName()+" "+state);
            b.button(info(label), ctx -> new CandidateVoteMenu(ctx.player(), this.getParentMenu(), svc, electionId, c.getId()).open());
        }

        // submit action
        b.buttonWithPlayer(good("Submit"), null, java.time.Duration.ofMinutes(5), 1, (p, resp) -> {
            if (system == VotingSystem.BLOCK) {
                List<Integer> picks = session.getSelected();
                int min = Math.max(1, e.getMinimumVotes());
                if (picks.size() != min) { p.sendMessage(neg("You must select exactly "+min+" candidates.")); new CandidateListMenu(p, getParentMenu(), svc, electionId).open(); return; }
                boolean ok = svc.submitBlockBallot(electionId, svc.registerVoter(electionId, p.getName()).getId(), picks);
                if (!ok) { p.sendMessage(neg("Submission failed. Are you eligible or already voted?")); new CandidateListMenu(p, getParentMenu(), svc, electionId).open(); }
                else { p.sendMessage(good("Ballot submitted.")); BallotSessions.clear(p.getUniqueId(), electionId); }
            } else {
                Map<Integer,Integer> ranks = session.getAllRanks();
                if (ranks.isEmpty()) { p.sendMessage(neg("No preferences set.")); new CandidateListMenu(p, getParentMenu(), svc, electionId).open(); return; }
                int min = Math.max(1, e.getMinimumVotes());
                // Validate ranks: >=1, <= candidate count, unique
                int maxRank = Math.max(1, cs.size());
                Set<Integer> seen = new HashSet<>();
                for (Map.Entry<Integer,Integer> en : ranks.entrySet()) {
                    Integer r = en.getValue();
                    if (r == null || r < 1 || r > maxRank) { p.sendMessage(neg("Invalid rank: "+r)); new CandidateListMenu(p, getParentMenu(), svc, electionId).open(); return; }
                    if (!seen.add(r)) { p.sendMessage(neg("Duplicate rank: "+r)); new CandidateListMenu(p, getParentMenu(), svc, electionId).open(); return; }
                }
                if (ranks.size() < min) { p.sendMessage(neg("Select at least "+min+" preferences.")); new CandidateListMenu(p, getParentMenu(), svc, electionId).open(); return; }
                List<Map.Entry<Integer,Integer>> list = new ArrayList<>(ranks.entrySet());
                list.sort(java.util.Comparator.comparingInt(Map.Entry::getValue));
                List<Integer> ordered = new ArrayList<>();
                for (Map.Entry<Integer,Integer> en : list) ordered.add(en.getKey());
                boolean ok = svc.submitPreferentialBallot(electionId, svc.registerVoter(electionId, p.getName()).getId(), ordered);
                if (!ok) { p.sendMessage(neg("Submission failed. Are you eligible or already voted?")); new CandidateListMenu(p, getParentMenu(), svc, electionId).open(); }
                else { p.sendMessage(good("Ballot submitted.")); BallotSessions.clear(p.getUniqueId(), electionId); }
            }
        });

        b.button(warn("Clear all"), ctx -> { BallotSessions.get(ctx.player().getUniqueId(), electionId, system).clearAll(); new CandidateListMenu(ctx.player(), getParentMenu(), svc, electionId).open(); });
        b.button(neg("Back"), ctx -> getParentMenu().open());

        return b.build();
    }
}
