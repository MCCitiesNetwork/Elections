package net.democracycraft.democracyelections.ui.vote;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.democracyelections.api.model.Candidate;
import net.democracycraft.democracyelections.api.model.Election;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.ui.ChildMenuImp;
import net.democracycraft.democracyelections.api.ui.ParentMenu;
import net.democracycraft.democracyelections.util.HeadUtil;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ballot UI for Block voting. Voter must select exactly minimumVotes candidates using boolean inputs.
 */
public class BlockBallotMenu extends ChildMenuImp {

    private final ElectionsService svc;
    private final int electionId;

    public BlockBallotMenu(Player player, ParentMenu parent, ElectionsService svc, int electionId) {
        super(player, parent, "ballot_block_" + electionId);
        this.svc = svc;
        this.electionId = electionId;
        this.setDialog(build());
    }

    private Dialog build() {
        Optional<Election> opt = svc.getElection(electionId);
        AutoDialog.Builder b = getAutoDialogBuilder();
        if (opt.isEmpty()) {
            b.title(title("Block Ballot"));
            b.addBody(DialogBody.plainMessage(neg("Election not found.")));
            return b.build();
        }
        Election e = opt.get();
        int min = Math.max(1, e.getMinimumVotes());

        b.title(title(e.getTitle() + " Ballot (Block)"));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);

        b.addBody(DialogBody.plainMessage(Component.newline()
                .append(key("Minimum required: ")).append(info(String.valueOf(min)))
                .appendNewline().append(info("Check exactly " + min + " candidates."))
        ));

        List<Candidate> cs = e.getCandidates();
        Map<String, Integer> keyToId = new LinkedHashMap<>();
        for (Candidate c : cs) {
            String key = keyFor(c.getId());
            keyToId.put(key, c.getId());
            // Refresh head bytes asynchronously (fallback renders immediately by name)
            HeadUtil.updateHeadItemBytesAsync(svc, electionId, c.getId(), c.getName());
            // Candidate head item (bytes preferred, fallback to player-name head)
            ItemStack headItem = HeadUtil.headFromBytesOrName(svc, electionId, c.getId(), c.getName());
            b.addBody(DialogBody.item(headItem).showTooltip(true).build());
            b.addInput(DialogInput.bool(key, info(c.getName())).initial(false).build());
        }

        b.buttonWithPlayer(good("Submit"), null, Duration.ofMinutes(5), 1, (p, resp) -> {
            List<Integer> picks = new ArrayList<>();
            for (Map.Entry<String, Integer> e1 : keyToId.entrySet()) {
                Boolean sel = resp.getBoolean(e1.getKey());
                if (sel != null && sel) picks.add(e1.getValue());
            }
            if (picks.size() != min) {
                p.sendMessage(neg("You must select exactly " + min + " candidates."));
                new BlockBallotMenu(p, getParentMenu(), svc, electionId).open();
                return;
            }
            boolean ok = svc.submitBlockBallot(electionId, svc.registerVoter(electionId, p.getName()).getId(), picks);
            if (!ok) {
                p.sendMessage(neg("Submission failed. Are you eligible or already voted?"));
                new BlockBallotMenu(p, getParentMenu(), svc, electionId).open();
            } else {
                p.sendMessage(good("Ballot submitted."));
            }
        });

        b.button(warn("Clear"), ctx -> new BlockBallotMenu(ctx.player(), getParentMenu(), svc, electionId).open());
        b.button(neg("Back"), ctx -> getParentMenu().open());

        return b.build();
    }

    private String keyFor(int candidateId) { return "CAND_" + candidateId; }
}
