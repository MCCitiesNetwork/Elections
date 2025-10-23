package net.democracycraft.democracyelections.ui.manager;

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
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Optional;

/**
 * Child dialog to manage election candidates (add/remove).
 */
public class CandidatesMenu extends ChildMenuImp {

    enum Keys { CANDIDATE_NAME }

    private final ElectionsService electionsService;
    private final int electionId;

    /**
     * @param player player opening the dialog
     * @param parent parent menu
     * @param electionsService elections service
     * @param electionId election identifier
     */
    public CandidatesMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId) {
        super(player, parent, "candidates_" + electionId);
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }

    private Dialog build() {
        Election election = electionsService.getElection(electionId).orElse(null);

        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        dialogBuilder.title(title("Candidates"));
        dialogBuilder.canCloseWithEscape(true);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        if (election == null || election.getCandidates().isEmpty()) {
            dialogBuilder.addBody(DialogBody.plainMessage(info("No candidates yet.")));
        } else {
            dialogBuilder.addBody(DialogBody.plainMessage(info("Current candidates:")));
            for (Candidate candidate : election.getCandidates()) {
                Component label = Component.text("#" + candidate.getId() + " ")
                        .append(Component.text(candidate.getName()).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
                int candidateId = candidate.getId();
                dialogBuilder.button(neg("Remove ").append(label), ctx -> {
                    boolean removed = electionsService.removeCandidate(electionId, candidateId);
                    ctx.player().sendMessage(removed ? warn("Candidate removed.") : neg("Could not remove candidate."));
                    new CandidatesMenu(ctx.player(), getParentMenu(), electionsService, electionId).open();
                });
            }
        }

        dialogBuilder.addInput(DialogInput.text(Keys.CANDIDATE_NAME.name(), info("Candidate name")).labelVisible(true).build());
        dialogBuilder.buttonWithPlayer(good("Add"), null, Duration.ofMinutes(3), 1, (p, resp) -> {
            String name = resp.getText(Keys.CANDIDATE_NAME.name());
            if (name == null || name.isBlank()) {
                p.sendMessage(neg("Name cannot be empty."));
                new CandidatesMenu(p, getParentMenu(), electionsService, electionId).open();
                return;
            }
            Optional<Candidate> added = electionsService.addCandidate(electionId, name);
            if (added.isEmpty()) {
                p.sendMessage(neg("Could not add candidate."));
                new CandidatesMenu(p, getParentMenu(), electionsService, electionId).open();
                return;
            }
            Candidate cand = added.get();
            // Trigger async head bytes update; UI will fallback to name-based head until bytes are ready
            HeadUtil.updateHeadItemBytesAsync(electionsService, electionId, cand.getId(), name);
            p.sendMessage(good("Candidate added."));
            new CandidatesMenu(p, getParentMenu(), electionsService, electionId).open();
        });

        dialogBuilder.button(info("Back"), ctx -> new ElectionManagerMenu(ctx.player(), electionsService, electionId).open());
        return dialogBuilder.build();
    }
}
