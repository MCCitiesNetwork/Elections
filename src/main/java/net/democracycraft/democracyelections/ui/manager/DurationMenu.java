package net.democracycraft.democracyelections.ui.manager;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.democracyelections.api.model.Election;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.data.TimeDto;
import net.democracycraft.democracyelections.ui.ChildMenuImp;
import net.democracycraft.democracyelections.api.ui.ParentMenu;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Child dialog to set or clear an election duration.
 */
public class DurationMenu extends ChildMenuImp {

    enum Keys { DAYS, HOURS, MINUTES }

    private final ElectionsService electionsService;
    private final int electionId;

    /**
     * @param player player opening the dialog
     * @param parent parent menu
     * @param electionsService elections service
     * @param electionId election identifier
     */
    public DurationMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId) {
        super(player, parent, "duration_" + electionId);
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }


    private Dialog build() {
        Election election = electionsService.getElection(electionId).orElse(null);
        Integer durationDaysInit = election == null ? 0 : (election.getDurationDays() == null ? 0 : election.getDurationDays());
        int hoursInit = election == null || election.getDurationTime() == null ? 0 : election.getDurationTime().hour();
        int minutesInit = election == null || election.getDurationTime() == null ? 0 : election.getDurationTime().minute();

        AutoDialog.Builder b = getAutoDialogBuilder();
        b.title(title("Election Duration"));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);

        b.addBody(DialogBody.plainMessage(info("Set how long the election remains open (Never = no duration).")));
        b.addInput(DialogInput.numberRange(Keys.DAYS.name(), info("Days"), 0f, 365f).step(1f).initial(durationDaysInit.floatValue()).build());
        b.addInput(DialogInput.numberRange(Keys.HOURS.name(), info("Hours"), 0f, 23f).step(1f).initial((float) hoursInit).build());
        b.addInput(DialogInput.numberRange(Keys.MINUTES.name(), info("Minutes"), 0f, 59f).step(1f).initial((float) minutesInit).build());

        b.buttonWithPlayer(good("Save"), null, Duration.ofMinutes(5), 1, (p, resp) -> {
            Float fD = resp.getFloat(Keys.DAYS.name());
            Float fH = resp.getFloat(Keys.HOURS.name());
            Float fM = resp.getFloat(Keys.MINUTES.name());
            int d = fD == null ? 0 : Math.round(fD);
            int h = fH == null ? 0 : Math.round(fH);
            int m = fM == null ? 0 : Math.round(fM);
            TimeDto t = new TimeDto(0, m, h);
            electionsService.setDuration(electionId, d, t);
            p.sendMessage(good("Duration updated."));
            new ElectionManagerMenu(p, electionsService, electionId).open();
        });

        b.button(warn("Clear (Never)"), ctx -> {
            electionsService.setDuration(electionId, null, null);
            ctx.player().sendMessage(warn("Duration cleared."));
            new ElectionManagerMenu(ctx.player(), electionsService, electionId).open();
        });

        b.button(info("Back"), ctx -> new ElectionManagerMenu(ctx.player(), electionsService, electionId).open());
        return b.build();
    }
}
