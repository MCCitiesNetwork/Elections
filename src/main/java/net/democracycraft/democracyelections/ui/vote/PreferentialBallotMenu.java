package net.democracycraft.democracyelections.ui.vote;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.democracyelections.api.model.Candidate;
import net.democracycraft.democracyelections.api.model.Election;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.api.ui.ParentMenu;
import net.democracycraft.democracyelections.ui.ChildMenuImp;
import net.democracycraft.democracyelections.util.HeadUtil;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.*;

/**
 * Ballot UI for Preferential voting. Uses a boolean checkbox to include a candidate
 * and a text input to set their preference rank (integer). Ranks must be unique and start at 1.
 */
public class PreferentialBallotMenu extends ChildMenuImp {

    private final ElectionsService svc;
    private final int electionId;

    public PreferentialBallotMenu(Player player, ParentMenu parent, ElectionsService svc, int electionId) {
        super(player, parent, "ballot_pref_" + electionId);
        this.svc = svc;
        this.electionId = electionId;
        this.setDialog(build());
    }

    private Dialog build() {
        Optional<Election> opt = svc.getElection(electionId);
        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        if (opt.isEmpty()) {
            dialogBuilder.title(title("Preferential Ballot"));
            dialogBuilder.addBody(DialogBody.plainMessage(neg("Election not found.")));
            return dialogBuilder.build();
        }
        Election e = opt.get();
        int min = Math.max(1, e.getMinimumVotes());
        int maxRank = Math.max(1, e.getCandidates().size());

        dialogBuilder.title(title(e.getTitle() + " Ballot (Preferential)"));
        dialogBuilder.canCloseWithEscape(true);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        dialogBuilder.addBody(DialogBody.plainMessage(Component.newline()
                .append(key("Minimum preferences: ")).append(info(String.valueOf(min)))
                .appendNewline().append(info("Check candidates and type an integer rank between 1 and "+maxRank+". Ranks must be unique."))
        ));

        List<Candidate> cs = e.getCandidates();
        Map<String, Integer> keyToId = new LinkedHashMap<>();
        Map<String, Integer> rankKeyToId = new LinkedHashMap<>();
        for (Candidate c : cs) {
            String key = selKey(c.getId());
            String rkey = rankKey(c.getId());
            keyToId.put(key, c.getId());
            rankKeyToId.put(rkey, c.getId());
            // Refresh head bytes asynchronously and render head from bytes or name
            HeadUtil.updateHeadItemBytesAsync(svc, electionId, c.getId(), c.getName());
            dialogBuilder.addBody(DialogBody.item(HeadUtil.headFromBytesOrName(svc, electionId, c.getId(), c.getName())).showTooltip(true).build());
            dialogBuilder.addInput(DialogInput.bool(key, info(c.getName())).initial(false).build());
            dialogBuilder.addInput(DialogInput.text(rkey, key("Rank (1.."+maxRank+") for "+c.getName())).labelVisible(true).build());
        }

        dialogBuilder.buttonWithPlayer(good("Submit"), null, Duration.ofMinutes(5), 1, (p, resp) -> {
            List<Map.Entry<Integer, Integer>> selections = new ArrayList<>(); // (candidateId, rank)
            Set<Integer> seenRanks = new HashSet<>();
            for (Map.Entry<String, Integer> e1 : keyToId.entrySet()) {
                Boolean sel = resp.getBoolean(e1.getKey());
                if (sel != null && sel) {
                    String rkey = rankKey(e1.getValue());
                    String txt = resp.getText(rkey);
                    if (txt == null || txt.isBlank()) { p.sendMessage(neg("Missing rank for a selected candidate.")); new PreferentialBallotMenu(p, getParentMenu(), svc, electionId).open(); return; }
                    int r;
                    try { r = Integer.parseInt(txt.trim()); } catch (NumberFormatException ex) { p.sendMessage(neg("Invalid rank: "+txt)); new PreferentialBallotMenu(p, getParentMenu(), svc, electionId).open(); return; }
                    if (r < 1 || r > maxRank) { p.sendMessage(neg("Invalid rank: "+r)); new PreferentialBallotMenu(p, getParentMenu(), svc, electionId).open(); return; }
                    if (!seenRanks.add(r)) { p.sendMessage(neg("Duplicate rank: "+r)); new PreferentialBallotMenu(p, getParentMenu(), svc, electionId).open(); return; }
                    selections.add(Map.entry(e1.getValue(), r));
                }
            }
            if (selections.size() < min) { p.sendMessage(neg("Select at least "+min+" preferences.")); new PreferentialBallotMenu(p, getParentMenu(), svc, electionId).open(); return; }
            selections.sort(Comparator.comparingInt(Map.Entry::getValue));
            List<Integer> ordered = new ArrayList<>();
            for (Map.Entry<Integer, Integer> pair : selections) ordered.add(pair.getKey());
            boolean ok = svc.submitPreferentialBallot(electionId, svc.registerVoter(electionId, p.getName()).getId(), ordered);
            if (!ok) {
                p.sendMessage(neg("Submission failed. Are you eligible or already voted?"));
                new PreferentialBallotMenu(p, getParentMenu(), svc, electionId).open();
            } else {
                p.sendMessage(good("Ballot submitted."));
            }
        });

        dialogBuilder.button(warn("Clear"), ctx -> new PreferentialBallotMenu(ctx.player(), getParentMenu(), svc, electionId).open());
        dialogBuilder.button(neg("Back"), ctx -> getParentMenu().open());

        return dialogBuilder.build();
    }

    private String selKey(int candidateId) { return "CAND_" + candidateId; }
    private String rankKey(int candidateId) { return "RANK_" + candidateId; }
}
