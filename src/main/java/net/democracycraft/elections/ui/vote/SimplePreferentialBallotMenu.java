package net.democracycraft.elections.ui.vote;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.model.Candidate;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.ui.ChildMenuImp;
import net.democracycraft.elections.ui.dialog.AutoDialog;
import net.democracycraft.elections.ui.common.LoadingMenu;
import net.democracycraft.elections.util.sound.SoundHelper;
import net.democracycraft.elections.util.sound.SoundSpec;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.Serializable;
import java.util.*;

/**
 * Simple preferential ballot UI that presents each candidate as a single cycling button:
 * clicking cycles through Not Ranked -> 1 -> 2 -> ... -> N -> Not Ranked.
 * All texts are configurable via data/menus/SimplePreferentialBallotMenu.yml.
 */
public class SimplePreferentialBallotMenu extends ChildMenuImp {

    private final ElectionsService electionsService;
    private final int electionId;

    /**
     * @param player the player opening the dialog
     * @param parent the parent menu to return to
     * @param electionsService elections service facade
     * @param electionId target election identifier
     */
    public SimplePreferentialBallotMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId) {
        super(player, parent, "simple_pref_" + electionId);
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }

    /** Configuration DTO for this menu, persisted in YAML per menu. */
    public static class Config implements Serializable {
        public String titleFallback = "<gold><bold>Ballot</bold></gold>";
        public String notFound = "<red><bold>Election not found.</bold></red>";
        public String titleFormat = "<gold><bold>%election_title% Ballot (Simple Preferential)</bold></gold>";
        public String instruction = "<gray>Click candidates to cycle rank. Submit with at least <white><bold>%min%</bold></white> preferences.</gray>";
        public String optionNotRanked = "<gray>Not Ranked</gray>";
        public String optionRankFormat = "<gray>Rank %rank%</gray>";
        public String submitBtn = "<green><bold>Submit</bold></green>";
        public String clearBtn = "<yellow>Clear all</yellow>";
        public String backBtn = "<red><bold>Back</bold></red>";
        public String submissionFailed = "<red><bold>Submission failed. Are you eligible or already voted?</bold></red>";
        public String submitted = "<green><bold>Ballot submitted.</bold></green>";
        public String invalidRank = "<red><bold>Invalid rank: %rank%</bold></red>";
        public String duplicateRank = "<red><bold>Duplicate rank: %rank%</bold></red>";
        public String selectAtLeast = "<red><bold>Select at least %min% preferences.</bold></red>";
        public String yamlHeader = "SimplePreferentialBallotMenu configuration. Placeholders: %election_title%, %min%, %candidate_name%, %rank%.";
        /** Loading dialog title shown while submitting. */
        public String loadingTitle = "<gold><bold>Submitting</bold></gold>";
        /** Loading dialog message shown while submitting. */
        public String loadingMessage = "<gray><italic>Submitting your ballot…</italic></gray>";
        /** Sound to play when submission succeeds. */
        public SoundSpec successSound = new SoundSpec();
        public Config() {}
    }

    /**
     * Builds the dialog, listing candidates with cycling rank buttons and Submit/Clear/Back actions.
     * @return dialog instance
     */
    private Dialog build() {
        Optional<Election> optionalElection = electionsService.getElectionSnapshot(electionId);
        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        if (optionalElection.isEmpty()) {
            dialogBuilder.title(miniMessage(config.titleFallback));
            dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(config.notFound)));
            return dialogBuilder.build();
        }
        Election election = optionalElection.get();
        int min = Math.max(1, election.getMinimumVotes());
        int maxRank = Math.max(1, election.getCandidates().size());

        Map<String, String> ph = Map.of(
                "%election_title%", election.getTitle(),
                "%min%", String.valueOf(min)
        );

        dialogBuilder.title(miniMessage(config.titleFormat, ph));
        dialogBuilder.canCloseWithEscape(true);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        dialogBuilder.addBody(DialogBody.plainMessage(Component.newline().append(miniMessage(config.instruction, ph))));

        BallotSessions.Session session = BallotSessions.get(getPlayer().getUniqueId(), electionId, election.getSystem());
        session.setSystem(election.getSystem());

        for (Candidate c : election.getCandidates()) {

            Integer current = session.getRank(c.getId());
            List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
            entries.add(SingleOptionDialogInput.OptionEntry.create("0", miniMessage(applyPlaceholders(config.optionNotRanked, Map.of("%candidate_name%", c.getName())), null), current == null));
            for (int r = 1; r <= maxRank; r++) {
                boolean init = current != null && current == r;
                entries.add(SingleOptionDialogInput.OptionEntry.create(String.valueOf(r), miniMessage(applyPlaceholders(config.optionRankFormat, Map.of("%candidate_name%", c.getName(), "%rank%", String.valueOf(r))), null), init));
            }
            String key = "RANK_" + c.getId();
            dialogBuilder.addInput(DialogInput.singleOption(key, miniMessage("<white>" + c.getName() + "</white>", null), entries)
                    .build());
        }

        dialogBuilder.buttonWithPlayer(miniMessage(config.submitBtn, ph), null, java.time.Duration.ofMinutes(5), 1, (playerActor, response) -> {
            Map<Integer, Integer> ranksByCandidate = new HashMap<>();
            for (Candidate c : election.getCandidates()) {
                String key = "RANK_" + c.getId();
                String txt = response.getText(key);
                int idx;
                try { idx = txt == null ? 0 : Integer.parseInt(txt); } catch (NumberFormatException e) { idx = 0; }
                if (idx >= 1 && idx <= maxRank) {
                    ranksByCandidate.put(c.getId(), idx);
                }
            }
            if (ranksByCandidate.isEmpty() || ranksByCandidate.size() < min) { playerActor.sendMessage(miniMessage(applyPlaceholders(config.selectAtLeast, Map.of("%min%", String.valueOf(min))), null)); new SimplePreferentialBallotMenu(playerActor, getParentMenu(), electionsService, electionId).open(); return; }
            int maximum = Math.max(1, election.getCandidates().size());
            Set<Integer> seen = new HashSet<>();
            for (Integer r : ranksByCandidate.values()) {
                if (r == null || r < 1 || r > maximum) { playerActor.sendMessage(miniMessage(applyPlaceholders(config.invalidRank, Map.of("%rank%", String.valueOf(r))), null)); new SimplePreferentialBallotMenu(playerActor, getParentMenu(), electionsService, electionId).open(); return; }
                if (!seen.add(r)) { playerActor.sendMessage(miniMessage(applyPlaceholders(config.duplicateRank, Map.of("%rank%", String.valueOf(r))), null)); new SimplePreferentialBallotMenu(playerActor, getParentMenu(), electionsService, electionId).open(); return; }
            }
            List<Map.Entry<Integer,Integer>> entries = new ArrayList<>(ranksByCandidate.entrySet());
            entries.sort(java.util.Comparator.comparingInt(Map.Entry::getValue));
            List<Integer> orderedCandidateIds = new ArrayList<>();
            for (Map.Entry<Integer,Integer> entry : entries) orderedCandidateIds.add(entry.getKey());
            new LoadingMenu(playerActor, getParentMenu(), miniMessage(config.loadingTitle, ph), miniMessage(config.loadingMessage, ph)).open();
            new BukkitRunnable() {
                @Override
                public void run() {
                    int voterId = electionsService.registerVoterAsync(electionId, playerActor.getName()).join().getId();
                    boolean ok = electionsService.submitPreferentialBallotAsync(electionId, voterId, orderedCandidateIds).join();
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            // Close loading dialog after async completes
                            playerActor.closeDialog();
                            if (!ok) { playerActor.sendMessage(miniMessage(config.submissionFailed)); new SimplePreferentialBallotMenu(playerActor, getParentMenu(), electionsService, electionId).open(); }
                            else { playerActor.sendMessage(miniMessage(config.submitted)); SoundHelper.play(playerActor, config.successSound); BallotSessions.clear(playerActor.getUniqueId(), electionId); }
                        }
                    }.runTask(Elections.getInstance());
                }
            }.runTaskAsynchronously(Elections.getInstance());
        });

        dialogBuilder.button(miniMessage(config.clearBtn, ph), ctx -> { session.clearAll(); new SimplePreferentialBallotMenu(ctx.player(), getParentMenu(), electionsService, electionId).open(); });
        dialogBuilder.button(miniMessage(config.backBtn, ph), ctx -> getParentMenu().open());

        return dialogBuilder.build();
    }
}
