package net.democracycraft.democracyelections.ui.manager.create;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.democracyelections.api.ui.ParentMenu;
import net.democracycraft.democracyelections.ui.ChildMenuImp;
import net.democracycraft.democracyelections.ui.manager.ElectionManagerMenu;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Create wizard step: Confirm and create election.
 */
public class ElectionCreateConfirmMenu extends ChildMenuImp {

    private final ElectionCreateWizard wizard;
    private final DraftElection draft;

    public ElectionCreateConfirmMenu(Player player, ParentMenu parent, DraftElection draft) {
        super(player, parent, "create_confirm");
        this.wizard = (ElectionCreateWizard) parent;
        this.draft = draft;
        this.setDialog(build());
    }

    private Dialog build() {
        AutoDialog.Builder b = getAutoDialogBuilder();
        b.title(title("Create: Confirm"));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);

        String durationStr;
        if (draft.durationDays == null && draft.durationTime == null) {
            durationStr = "Never";
        } else {
            int dd = draft.durationDays == null ? 0 : draft.durationDays;
            int hh = draft.durationTime == null ? 0 : draft.durationTime.hour();
            int mm = draft.durationTime == null ? 0 : draft.durationTime.minute();
            durationStr = String.format("%sd %sh %sm", dd, hh, mm);
        }

        String permsCount = String.valueOf(draft.requirements.getPermissions().size());
        String mins = String.valueOf(draft.requirements.getMinActivePlaytimeMinutes());

        b.addBody(DialogBody.plainMessage(Component.newline()
                .append(key("Title: ")).append(info(draft.title))
                .appendNewline().append(key("System: ")).append(info(draft.system.name()))
                .appendNewline().append(key("Minimum votes: ")).append(info(String.valueOf(draft.minimumVotes)))
                .appendNewline().append(key("Requirements: ")).append(info(permsCount + " perms, " + mins + " min active playtime"))
                .appendNewline().append(key("Closes: ")).append(info(durationStr))
        ));

        b.button(good("Confirm"), ctx -> {
            var service = wizard.getService();
            var created = service.createElection(draft.title, draft.system, draft.minimumVotes, draft.requirements);
            // Apply duration if provided
            if (draft.durationDays != null || draft.durationTime != null) {
                service.setDuration(created.getId(), draft.durationDays, draft.durationTime);
            }
            ctx.player().sendMessage(good("Election created."));
            new ElectionManagerMenu(ctx.player(), service, created.getId()).open();
        });

        b.button(info("Back"), ctx -> new ElectionCreateSystemMenu(ctx.player(), wizard, draft).open());
        b.button(neg("Cancel"), ctx -> ctx.player().closeInventory());
        return b.build();
    }
}

