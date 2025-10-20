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
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.time.Duration;

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
        Election e = electionsService.getElection(electionId).orElse(null);

        AutoDialog.Builder b = getAutoDialogBuilder();
        b.title(title("Candidates"));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);

        if (e == null || e.getCandidates().isEmpty()) {
            b.addBody(DialogBody.plainMessage(info("No candidates yet.")));
        } else {
            b.addBody(DialogBody.plainMessage(info("Current candidates:")));
            for (Candidate c : e.getCandidates()) {
                Component label = Component.text("#" + c.getId() + " ")
                        .append(Component.text(c.getName()).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
                int cid = c.getId();
                b.button(neg("Remove ").append(label), ctx -> {
                    boolean removed = electionsService.removeCandidate(electionId, cid);
                    ctx.player().sendMessage(removed ? warn("Candidate removed.") : neg("Could not remove candidate."));
                    new CandidatesMenu(ctx.player(), getParentMenu(), electionsService, electionId).open();
                });
            }
        }

        b.addInput(DialogInput.text(Keys.CANDIDATE_NAME.name(), info("Candidate name")).labelVisible(true).build());
        b.buttonWithPlayer(good("Add"), null, Duration.ofMinutes(3), 1, (p, resp) -> {
            String name = resp.getText(Keys.CANDIDATE_NAME.name());
            if (name == null || name.isBlank()) {
                p.sendMessage(neg("Name cannot be empty."));
                new CandidatesMenu(p, getParentMenu(), electionsService, electionId).open();
                return;
            }
            electionsService.addCandidate(electionId, name);
            p.sendMessage(good("Candidate added."));
            new CandidatesMenu(p, getParentMenu(), electionsService, electionId).open();
        });

        b.button(info("Back"), ctx -> new ElectionManagerMenu(ctx.player(), electionsService, electionId).open());
        return b.build();
    }
}
