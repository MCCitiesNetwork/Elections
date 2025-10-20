package net.democracycraft.democracyelections.ui.manager.create;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.democracyelections.api.ui.ParentMenu;
import net.democracycraft.democracyelections.data.TimeDto;
import net.democracycraft.democracyelections.ui.ChildMenuImp;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.time.Duration;

public class ElectionCreateDurationMenu extends ChildMenuImp {

    enum Keys { DAYS, HOURS, MINUTES }

    private final ElectionCreateWizard wizard;
    private final DraftElection draft;

    public ElectionCreateDurationMenu(Player player, ParentMenu parent, DraftElection draft) {
        super(player, parent, "create_duration");
        this.wizard = (ElectionCreateWizard) parent;
        this.draft = draft;
        this.setDialog(build());
    }

    private Dialog build() {
        AutoDialog.Builder b = getAutoDialogBuilder();
        b.title(title("Create: Duration"));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);

        b.addBody(DialogBody.plainMessage(info("Set how long the election remains open (Never = no duration).")));
        float dd = draft.durationDays == null ? 0f : draft.durationDays.floatValue();
        float hh = draft.durationTime == null ? 0f : draft.durationTime.hour();
        float mm = draft.durationTime == null ? 0f : draft.durationTime.minute();

        b.addInput(DialogInput.numberRange(Keys.DAYS.name(), info("Days"), 0f, 365f).step(1f).initial(dd).build());
        b.addInput(DialogInput.numberRange(Keys.HOURS.name(), info("Hours"), 0f, 23f).step(1f).initial(hh).build());
        b.addInput(DialogInput.numberRange(Keys.MINUTES.name(), info("Minutes"), 0f, 59f).step(1f).initial(mm).build());

        b.buttonWithPlayer(good("Next"), null, Duration.ofMinutes(5), 1, (p, resp) -> {
            Float fD = resp.getFloat(Keys.DAYS.name());
            Float fH = resp.getFloat(Keys.HOURS.name());
            Float fM = resp.getFloat(Keys.MINUTES.name());
            int d = fD == null ? 0 : Math.round(fD);
            int h = fH == null ? 0 : Math.round(fH);
            int m = fM == null ? 0 : Math.round(fM);

            if (d == 0 && h == 0 && m == 0) {
                draft.durationDays = null;
                draft.durationTime = null;
            } else {
                draft.durationDays = d;
                draft.durationTime = new TimeDto(0, m, h);
            }
            new ElectionCreateRequirementsMenu(p, wizard, draft).open();
        });

        b.button(info("Back"), ctx -> new ElectionCreateBasicsMenu(ctx.player(), wizard, draft).open());
        return b.build();
    }
}
