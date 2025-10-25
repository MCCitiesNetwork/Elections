package net.democracycraft.elections.ui.manager;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.data.TimeDto;
import net.democracycraft.elections.ui.ChildMenuImp;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.ui.dialog.AutoDialog;
import net.democracycraft.elections.ui.common.LoadingMenu;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Child dialog to set or clear an election duration. All texts are configurable via
 * data/menus/DurationMenu.yml with placeholders like %player%, %election_id%, %days%, %hours%, %minutes%.
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

    /**
     * Menu configuration DTO for this dialog.
     */
    public static class Config implements Serializable {
        public String title = "<gold><bold>Election Duration</bold></gold>";
        public String description = "<gray>Set how long the election remains open (Never = no duration).</gray>";
        public String daysLabel = "<aqua>Days</aqua>";
        public String hoursLabel = "<aqua>Hours</aqua>";
        public String minutesLabel = "<aqua>Minutes</aqua>";
        public String saveBtn = "<green><bold>Save</bold></green>";
        public String clearedBtn = "<yellow>Clear (Never)</yellow>";
        public String backBtn = "<gray>Back</gray>";
        public String updatedMsg = "<green><bold>Duration updated.</bold></green>";
        public String clearedMsg = "<yellow>Duration cleared.</yellow>";
        public String yamlHeader = "DurationMenu configuration. Placeholders: %player%, %election_id%, %days%, %hours%, %minutes%.";
        public float daysMin = 0f;
        public float daysMax = 365f;
        public float daysStep = 1f;
        public float hoursMin = 0f;
        public float hoursMax = 23f;
        public float hoursStep = 1f;
        public float minutesMin = 0f;
        public float minutesMax = 59f;
        public float minutesStep = 1f;
        public Config() {}
    }

    private Dialog build() {
        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        Election election = electionsService.getElection(electionId).orElse(null);
        Integer initialDays = election == null ? 0 : (election.getDurationDays() == null ? 0 : election.getDurationDays());
        int initialHours = election == null || election.getDurationTime() == null ? 0 : election.getDurationTime().hour();
        int initialMinutes = election == null || election.getDurationTime() == null ? 0 : election.getDurationTime().minute();

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%election_id%", String.valueOf(electionId));
        placeholders.put("%days%", String.valueOf(initialDays));
        placeholders.put("%hours%", String.valueOf(initialHours));
        placeholders.put("%minutes%", String.valueOf(initialMinutes));

        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        dialogBuilder.title(miniMessage(config.title, placeholders));
        dialogBuilder.canCloseWithEscape(true);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(config.description, placeholders)));
        dialogBuilder.addInput(DialogInput.numberRange(Keys.DAYS.name(), miniMessage(config.daysLabel, placeholders), config.daysMin, config.daysMax).step(config.daysStep).initial(initialDays.floatValue()).build());
        dialogBuilder.addInput(DialogInput.numberRange(Keys.HOURS.name(), miniMessage(config.hoursLabel, placeholders), config.hoursMin, config.hoursMax).step(config.hoursStep).initial((float) initialHours).build());
        dialogBuilder.addInput(DialogInput.numberRange(Keys.MINUTES.name(), miniMessage(config.minutesLabel, placeholders), config.minutesMin, config.minutesMax).step(config.minutesStep).initial((float) initialMinutes).build());

        dialogBuilder.buttonWithPlayer(miniMessage(config.saveBtn, placeholders), null, Duration.ofMinutes(5), 1, (playerActor, response) -> {
            Float daysVal = response.getFloat(Keys.DAYS.name());
            Float hoursVal = response.getFloat(Keys.HOURS.name());
            Float minutesVal = response.getFloat(Keys.MINUTES.name());
            int days = daysVal == null ? 0 : Math.round(daysVal);
            int hours = hoursVal == null ? 0 : Math.round(hoursVal);
            int minutes = minutesVal == null ? 0 : Math.round(minutesVal);
            TimeDto timeDto = new TimeDto(0, minutes, hours);
            new LoadingMenu(playerActor, getParentMenu()).open();
            // Offload DB write to async
            new BukkitRunnable() {
                @Override
                public void run() {
                    electionsService.setDuration(electionId, days, timeDto, playerActor.getName());
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            playerActor.sendMessage(miniMessage(applyPlaceholders(config.updatedMsg, Map.of("%days%", String.valueOf(days), "%hours%", String.valueOf(hours), "%minutes%", String.valueOf(minutes))), null));
                            new ElectionManagerMenu(playerActor, electionsService, electionId).open();
                        }
                    }.runTask(Elections.getInstance());
                }
            }.runTaskAsynchronously(Elections.getInstance());
        });

        dialogBuilder.button(miniMessage(config.clearedBtn, placeholders), context -> {
            // Run clear asynchronously
            new LoadingMenu(context.player(), getParentMenu()).open();
            new BukkitRunnable() {
                @Override
                public void run() {
                    electionsService.setDuration(electionId, null, null, context.player().getName());
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            context.player().sendMessage(miniMessage(config.clearedMsg, placeholders));
                            new ElectionManagerMenu(context.player(), electionsService, electionId).open();
                        }
                    }.runTask(Elections.getInstance());
                }
            }.runTaskAsynchronously(Elections.getInstance());
        });

        dialogBuilder.button(miniMessage(config.backBtn, placeholders), context -> new ElectionManagerMenu(context.player(), electionsService, electionId).open());
        return dialogBuilder.build();
    }
}
