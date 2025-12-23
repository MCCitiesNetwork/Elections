package net.democracycraft.elections.internal.ui.manager.create;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.internal.data.TimeDto;
import net.democracycraft.elections.internal.ui.ChildMenuImp;
import net.democracycraft.elections.api.ui.AutoDialog;
import org.bukkit.entity.Player;

import java.io.Serializable;

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

    /** Config DTO for this step. */
    public static class Config implements Serializable {
        public String title = "<gold><bold>Create: Duration</bold></gold>";
        public String description = "<gray>Set how long the election remains open (Never = no duration).</gray>";
        public String daysLabel = "<aqua>Days</aqua>";
        public String hoursLabel = "<aqua>Hours</aqua>";
        public String minutesLabel = "<aqua>Minutes</aqua>";
        public String nextBtn = "<green><bold>Next</bold></green>";
        public String backBtn = "<gray>Back</gray>";
        public String yamlHeader = "ElectionCreateDurationMenu configuration. Placeholders: %player%.";
        public float daysMin = 0f;
        public float daysMax = 365f;
        public float daysStep = 1f;
        public float hoursMin = 0f;
        public float hoursMax = 23f;
        public float hoursStep = 1f;
        public float minutesMin = 0f;
        public float minutesMax = 59f;
        public float minutesStep = 1f;
        /** Whether the dialog can be closed with Escape. */
        public boolean canCloseWithEscape = true;
        public Config() {}

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(Config.class, "ElectionCreateDurationMenu.yml", new Config().yamlHeader);
            yml.loadOrCreate(Config::new);
        }
    }

    private Dialog build() {
        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        dialogBuilder.title(miniMessage(config.title));
        dialogBuilder.canCloseWithEscape(config.canCloseWithEscape);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(config.description)));
        float daysInit = draft.durationDays == null ? 0f : draft.durationDays.floatValue();
        float hoursInit = draft.durationTime == null ? 0f : draft.durationTime.hour();
        float minutesInit = draft.durationTime == null ? 0f : draft.durationTime.minute();

        dialogBuilder.addInput(DialogInput.numberRange(Keys.DAYS.name(), miniMessage(config.daysLabel), config.daysMin, config.daysMax).step(config.daysStep).initial(daysInit).build());
        dialogBuilder.addInput(DialogInput.numberRange(Keys.HOURS.name(), miniMessage(config.hoursLabel), config.hoursMin, config.hoursMax).step(config.hoursStep).initial(hoursInit).build());
        dialogBuilder.addInput(DialogInput.numberRange(Keys.MINUTES.name(), miniMessage(config.minutesLabel), config.minutesMin, config.minutesMax).step(config.minutesStep).initial(minutesInit).build());

        dialogBuilder.buttonWithPlayer(miniMessage(config.nextBtn), null, (playerActor, response) -> {
            Float daysVal = response.getFloat(Keys.DAYS.name());
            Float hoursVal = response.getFloat(Keys.HOURS.name());
            Float minutesVal = response.getFloat(Keys.MINUTES.name());
            int days = daysVal == null ? 0 : Math.round(daysVal);
            int hours = hoursVal == null ? 0 : Math.round(hoursVal);
            int minutes = minutesVal == null ? 0 : Math.round(minutesVal);

            if (days == 0 && hours == 0 && minutes == 0) {
                draft.durationDays = null;
                draft.durationTime = null;
            } else {
                draft.durationDays = days;
                draft.durationTime = new TimeDto(0, minutes, hours);
            }
            new ElectionCreateRequirementsMenu(playerActor, wizard, draft).open();
        });

        dialogBuilder.button(miniMessage(config.backBtn), context -> new ElectionCreateBasicsMenu(context.player(), wizard, draft).open());
        return dialogBuilder.build();
    }
}
