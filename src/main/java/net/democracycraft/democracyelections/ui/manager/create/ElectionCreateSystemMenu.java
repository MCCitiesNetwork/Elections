package net.democracycraft.democracyelections.ui.manager.create;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.democracyelections.api.ui.ParentMenu;
import net.democracycraft.democracyelections.data.VotingSystem;
import net.democracycraft.democracyelections.ui.ChildMenuImp;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Create wizard step: System & Minimum votes.
 */
public class ElectionCreateSystemMenu extends ChildMenuImp {

    enum Keys { MIN_VOTES, MIN_VOTES_TEXT }

    private final ElectionCreateWizard wizard;
    private final DraftElection draft;

    public ElectionCreateSystemMenu(Player player, ParentMenu parent, DraftElection draft) {
        super(player, parent, "create_system");
        this.wizard = (ElectionCreateWizard) parent;
        this.draft = draft;
        this.setDialog(build());
    }

    private Dialog build() {
        AutoDialog.Builder b = getAutoDialogBuilder();
        b.title(title("Create: System & Minimum"));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);

        b.addBody(DialogBody.plainMessage(info("Current system: ").append(Component.text(draft.system.name()).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD))));
        b.addInput(DialogInput.numberRange(Keys.MIN_VOTES.name(), info("Minimum votes"), 1f, 10000f).step(1f).initial((float) draft.minimumVotes).build());
        // Fine adjustment text box: if valid integer is provided here, it overrides the slider value
        b.addInput(DialogInput.text(Keys.MIN_VOTES_TEXT.name(), key("Fine adjust (min votes)")).labelVisible(true).build());

        b.button(warn("Cycle system"), ctx -> {
            draft.system = (draft.system == VotingSystem.PREFERENTIAL) ? VotingSystem.BLOCK : VotingSystem.PREFERENTIAL;
            new ElectionCreateSystemMenu(ctx.player(), wizard, draft).open();
        });

        b.buttonWithPlayer(good("Next"), null, Duration.ofMinutes(3), 1, (p, resp) -> {
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
                minVotes = f == null ? draft.minimumVotes : Math.max(1, Math.round(f));
            }
            draft.minimumVotes = minVotes;
            new ElectionCreateConfirmMenu(p, wizard, draft).open();
        });

        b.button(info("Back"), ctx -> new ElectionCreateRequirementsMenu(ctx.player(), wizard, draft).open());
        return b.build();
    }
}
