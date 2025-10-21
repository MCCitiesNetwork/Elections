package net.democracycraft.democracyelections.ui.manager;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.democracyelections.api.model.Election;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.data.VotingSystem;
import net.democracycraft.democracyelections.ui.ChildMenuImp;
import net.democracycraft.democracyelections.api.ui.ParentMenu;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Child dialog to configure voting system and minimum votes.
 */
public class SystemAndMinimumMenu extends ChildMenuImp {

    enum Keys { MIN_VOTES, MIN_VOTES_TEXT }

    private final ElectionsService electionsService;
    private final int electionId;

    /**
     * @param player player opening the dialog
     * @param parent parent menu
     * @param electionsService elections service
     * @param electionId election identifier
     */
    public SystemAndMinimumMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId) {
        super(player, parent, "system_min_" + electionId);
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }

    private Dialog build() {
        Election e = electionsService.getElection(electionId).orElse(null);
        int currentMin = e == null ? 1 : e.getMinimumVotes();
        VotingSystem system = e == null ? VotingSystem.PREFERENTIAL : e.getSystem();

        AutoDialog.Builder b = getAutoDialogBuilder();
        b.title(title("System & Minimum Votes"));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);

        b.addBody(DialogBody.plainMessage(info("Current system: ").append(Component.text(system.name()).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD))));
        b.addInput(DialogInput.numberRange(Keys.MIN_VOTES.name(), info("Minimum votes"), 1f, 10000f).step(1f).initial((float) currentMin).build());
        // Fine adjustment text box: if a valid integer is provided here, it overrides the slider value
        b.addInput(DialogInput.text(Keys.MIN_VOTES_TEXT.name(), key("Fine adjust (min votes)"))
                .labelVisible(true)
                .build());

        b.button(warn("Cycle system"), ctx -> {
            Election e2 = electionsService.getElection(electionId).orElse(null);
            VotingSystem next = (e2 == null || e2.getSystem() == VotingSystem.PREFERENTIAL) ? VotingSystem.BLOCK : VotingSystem.PREFERENTIAL;
            electionsService.setSystem(electionId, next);
            ctx.player().sendMessage(info("System set to ").append(Component.text(next.name()).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD)));
            new ElectionManagerMenu(ctx.player(), electionsService, electionId).open();
        });

        b.buttonWithPlayer(good("Save minimum"), null, Duration.ofMinutes(3), 1, (p, resp) -> {
            // Prefer the fine-adjust text value if present and valid; otherwise fall back to the slider
            Integer minVotes = null;
            String txt = resp.getText(Keys.MIN_VOTES_TEXT.name());
            if (txt != null && !txt.isBlank()) {
                try {
                    int parsed = Integer.parseInt(txt.trim());
                    if (parsed >= 1 && parsed <= 10000) {
                        minVotes = parsed;
                    }
                } catch (NumberFormatException ignored) { }
            }
            if (minVotes == null) {
                Float f = resp.getFloat(Keys.MIN_VOTES.name());
                minVotes = f == null ? currentMin : Math.round(f);
            }
            electionsService.setMinimumVotes(electionId, minVotes);
            p.sendMessage(good("Minimum votes updated."));
            new ElectionManagerMenu(p, electionsService, electionId).open();
        });

        b.button(info("Back"), ctx -> new ElectionManagerMenu(ctx.player(), electionsService, electionId).open());
        return b.build();
    }
}
