package net.democracycraft.elections.ui.manager;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.data.VotingSystem;
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
 * Child dialog to configure voting system and minimum votes.
 * All texts are configurable via data/menus/SystemAndMinimumMenu.yml with placeholders
 * like %player%, %election_id%, %system%, %min_votes%.
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

    /**
     * Menu configuration DTO for this dialog.
     */
    public static class Config implements Serializable {
        public String title = "<gold><bold>System & Minimum Votes</bold></gold>";
        public String currentSystemPrefix = "<gray>Current system: </gray>";
        public String minVotesLabel = "<aqua>Minimum votes</aqua>";
        public String fineAdjustLabel = "<aqua>Fine adjust (min votes)</aqua>";
        public String cycleSystemBtn = "<yellow>Cycle system</yellow>";
        public String systemSetMsgPrefix = "<gray>System set to </gray>";
        public String saveMinBtn = "<green><bold>Save minimum</bold></green>";
        public String minUpdatedMsg = "<green><bold>Minimum votes updated.</bold></green>";
        public String backBtn = "<gray>Back</gray>";
        public String confirmCycleTitle = "<yellow><bold>Change voting system?</bold></yellow>";
        public String confirmBtn = "<yellow>Confirm</yellow>";
        public String cancelBtn = "<gray>Cancel</gray>";
        public String blockMinWarn = "<yellow>Warning: min votes is greater than number of candidates. Opening will be blocked until resolved.</yellow>";
        public String yamlHeader = "SystemAndMinimumMenu configuration. Placeholders: %player%, %election_id%, %system%, %min_votes%.";
        public float minVotesMin = 1f;
        public float minVotesMax = 10000f;
        public float minVotesStep = 1f;
        public Config() {}
    }

    private Dialog build() {
        // Create AutoYML with header from default config
        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        Election election = electionsService.getElection(electionId).orElse(null);
        int currentMin = election == null ? 1 : election.getMinimumVotes();
        VotingSystem system = election == null ? VotingSystem.PREFERENTIAL : election.getSystem();

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%election_id%", String.valueOf(electionId));
        placeholders.put("%system%", system.name());
        placeholders.put("%min_votes%", String.valueOf(currentMin));

        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        dialogBuilder.title(miniMessage(config.title, placeholders));
        dialogBuilder.canCloseWithEscape(true);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        dialogBuilder.addBody(DialogBody.plainMessage(
                miniMessage(config.currentSystemPrefix, placeholders)
                        .append(miniMessage("<white><bold>" + system.name() + "</bold></white>", null))));
        dialogBuilder.addInput(DialogInput.numberRange(Keys.MIN_VOTES.name(), miniMessage(config.minVotesLabel, placeholders), config.minVotesMin, config.minVotesMax)
                .step(config.minVotesStep)
                .initial((float) currentMin)
                .build());
        dialogBuilder.addInput(DialogInput.text(Keys.MIN_VOTES_TEXT.name(), miniMessage(config.fineAdjustLabel, placeholders))
                .labelVisible(true)
                .build());

        dialogBuilder.button(miniMessage(config.cycleSystemBtn, placeholders), context -> {
            Election currentElection = electionsService.getElection(electionId).orElse(null);
            VotingSystem next = (currentElection == null || currentElection.getSystem() == VotingSystem.PREFERENTIAL) ? VotingSystem.BLOCK : VotingSystem.PREFERENTIAL;
            AutoDialog.Builder confirm = getAutoDialogBuilder();
            confirm.title(miniMessage(config.confirmCycleTitle, placeholders));
            confirm.button(miniMessage(config.confirmBtn, placeholders), c2 -> {
                // Perform the update asynchronously to avoid DB on main thread
                new LoadingMenu(c2.player(), getParentMenu()).open();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        electionsService.setSystem(electionId, next, c2.player().getName());
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                c2.player().sendMessage(miniMessage(config.systemSetMsgPrefix, placeholders)
                                        .append(miniMessage("<white><bold>" + next.name() + "</bold></white>", null)));
                                // Warn if switching to BLOCK and min votes > candidates
                                Election e2 = electionsService.getElection(electionId).orElse(null);
                                if (e2 != null && next == VotingSystem.BLOCK) {
                                    if (Math.max(1, e2.getMinimumVotes()) > e2.getCandidates().size()) {
                                        c2.player().sendMessage(miniMessage(config.blockMinWarn));
                                    }
                                }
                                new ElectionManagerMenu(c2.player(), electionsService, electionId).open();
                            }
                        }.runTask(Elections.getInstance());
                    }
                }.runTaskAsynchronously(Elections.getInstance());
            });
            confirm.button(miniMessage(config.cancelBtn, placeholders), c2 -> new SystemAndMinimumMenu(c2.player(), getParentMenu(), electionsService, electionId).open());
            context.player().showDialog(confirm.build());
        });

        dialogBuilder.buttonWithPlayer(miniMessage(config.saveMinBtn, placeholders), null, Duration.ofMinutes(3), 1, (playerActor, response) -> {
            Integer minVotes = null;
            String textInput = response.getText(Keys.MIN_VOTES_TEXT.name());
            int boundMin = Math.round(config.minVotesMin);
            int boundMax = Math.round(config.minVotesMax);
            if (textInput != null && !textInput.isBlank()) {
                try {
                    int parsed = Integer.parseInt(textInput.trim());
                    if (parsed >= boundMin && parsed <= boundMax) {
                        minVotes = parsed;
                    }
                } catch (NumberFormatException ignored) { }
            }
            if (minVotes == null) {
                Float rangeValue = response.getFloat(Keys.MIN_VOTES.name());
                int rv = rangeValue == null ? currentMin : Math.round(rangeValue);
                // clamp to configured bounds
                rv = Math.max(boundMin, Math.min(boundMax, rv));
                minVotes = rv;
            }
            final int minVotesFinal = minVotes;
            // Run DB write asynchronously, then update UI on main
            new LoadingMenu(playerActor, getParentMenu()).open();
            new BukkitRunnable() {
                @Override
                public void run() {
                    electionsService.setMinimumVotes(electionId, minVotesFinal, playerActor.getName());
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            playerActor.sendMessage(miniMessage(applyPlaceholders(config.minUpdatedMsg, Map.of("%min_votes%", String.valueOf(minVotesFinal))), null));
                            new ElectionManagerMenu(playerActor, electionsService, electionId).open();
                        }
                    }.runTask(Elections.getInstance());
                }
            }.runTaskAsynchronously(Elections.getInstance());
        });

        dialogBuilder.button(miniMessage(config.backBtn, placeholders), context -> new ElectionManagerMenu(context.player(), electionsService, electionId).open());
        return dialogBuilder.build();
    }
}
