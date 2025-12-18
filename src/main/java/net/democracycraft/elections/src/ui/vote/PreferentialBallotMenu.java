package net.democracycraft.elections.src.ui.vote;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput;
import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.model.BallotError;
import net.democracycraft.elections.api.model.Candidate;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.src.data.VotingSystem;
import net.democracycraft.elections.src.ui.ChildMenuImp;
import net.democracycraft.elections.api.ui.AutoDialog;
import net.democracycraft.elections.src.ui.common.ErrorMenu;
import net.democracycraft.elections.src.ui.common.LoadingMenu;
import net.democracycraft.elections.src.util.sound.SoundHelper;
import net.democracycraft.elections.src.util.sound.SoundSpec;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.Serializable;
import java.util.*;

/**
 * Ballot UI for Preferential voting. Uses a boolean checkbox to include a candidate
 * and a text input to set their preference rank (integer). Ranks must be unique and start at 1.
 * All texts are configurable via data/menus/PreferentialBallotMenu.yml with placeholders.
 */
public class PreferentialBallotMenu extends ChildMenuImp {

    private final ElectionsService electionsService;
    private final int electionId;

    public PreferentialBallotMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId) {
        super(player, parent, "ballot_pref_" + electionId);
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }

    /** Config DTO for this menu. */
    public static class Config implements Serializable {
        public String titleFallback = "<gold><bold>Preferential Ballot</bold></gold>";
        public String notFound = "<red><bold>Election not found.</bold></red>";
        public String titleFormat = "<gold><bold>%election_title% Ballot (Preferential)</bold></gold>";
        public String minPrefsLabel = "<aqua>Minimum preferences: </aqua>";
        public String instruction = "<gray>Check candidates and type an integer rank between 1 and <white><bold>%max%</bold></white>. Ranks must be unique.</gray>";
        public String submitBtn = "<green><bold>Submit</bold></green>";
        public String missingRank = "<red><bold>Missing rank for a selected candidate.</bold></red>";
        public String invalidRank = "<red><bold>Invalid rank: %rank%</bold></red>";
        public String duplicateRank = "<red><bold>Duplicate rank: %rank%</bold></red>";
        public String selectAtLeast = "<red><bold>Select at least %min% preferences.</bold></red>";
        public String submissionFailed = "<red><bold>Submission failed. Are you eligible or already voted?</bold></red>";
        public String submitted = "<green><bold>Ballot submitted.</bold></green>";
        public String clearBtn = "<yellow>Clear</yellow>";
        public String backBtn = "<red><bold>Back</bold></red>";
        /** YAML header with supported placeholders. */
        public String yamlHeader = "PreferentialBallotMenu configuration. Placeholders: %election_title%, %min%, %max%, %rank%, %candidate_name%, %candidate_party%. ";
        /** Loading dialog title shown while submitting. */
        public String loadingTitle = "<gold><bold>Submitting</bold></gold>";
        /** Loading dialog message shown while submitting. */
        public String loadingMessage = "<gray><italic>Submitting your ballot…</italic></gray>";
        /** Sound to play when submission succeeds. */
        public SoundSpec successSound = new SoundSpec();
        public String valueGrayFormat = "<gray>%value%</gray>";
        /** Label to use when a candidate has no party set (null/blank). */
        public String partyUnknown = "Independent";
        /** Label for each candidate slider. Placeholders: %candidate_name%, %candidate_party%, %current_rank%. */
        public String candidateSliderLabelFormat = "<white>%candidate_name%</white> <gray>(%candidate_party%)</gray> <gray> | Current: </gray>";
        /** Text used when a candidate currently has no rank assigned. */
        public String notRankedText = "Not ranked";
        public boolean canCloseWithEscape = true;
        public Config() {}

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(PreferentialBallotMenu.Config.class, "PreferentialBallotMenu.yml", new PreferentialBallotMenu.Config().yamlHeader);
            yml.loadOrCreate(PreferentialBallotMenu.Config::new);
        }
    }


    private Dialog build() {
        Optional<Election> optionalElection = electionsService.getElectionSnapshot(electionId);
        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();

        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);
        dialogBuilder.canCloseWithEscape(config.canCloseWithEscape);
        if (optionalElection.isEmpty()) {
            dialogBuilder.title(miniMessage(config.titleFallback));
            dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(config.notFound)));
            return dialogBuilder.build();
        }
        Election election = optionalElection.get();
        if (election.getSystem() != VotingSystem.PREFERENTIAL) {
            // Safety: this menu is only meaningful for preferential systems.
            return dialogBuilder.build();
        }
        int min = Math.max(1, election.getMinimumVotes());
        int maxRank = Math.max(1, election.getCandidates().size());

        Map<String, String> placeholders = Map.of(
                "%election_title%", election.getTitle(),
                "%min%", String.valueOf(min),
                "%max%", String.valueOf(maxRank)
        );

        dialogBuilder.title(miniMessage(config.titleFormat, placeholders));
        dialogBuilder.canCloseWithEscape(true);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        dialogBuilder.addBody(DialogBody.plainMessage(Component.newline()
                .append(miniMessage(config.minPrefsLabel, placeholders))
                .append(miniMessage(applyPlaceholders(config.valueGrayFormat, Map.of("%value%", String.valueOf(min))), null))
                .appendNewline()
                .append(miniMessage(config.instruction, placeholders))
        ));

        BallotSessions.Session session = BallotSessions.get(getPlayer().getUniqueId(), electionId, election.getSystem());
        session.setSystem(election.getSystem());

        List<Candidate> candidates = election.getCandidates();
        Map<String, Integer> sliderKeyToCandidateId = new LinkedHashMap<>();

        for (Candidate candidate : candidates) {
            int candidateId = candidate.getId();
            String sliderKey = "RANK_" + candidateId;
            sliderKeyToCandidateId.put(sliderKey, candidateId);

            String party = candidate.getParty();
            if (party == null || party.isBlank()) party = config.partyUnknown;

            Integer currentRank = session.getRank(candidateId);
            String currentRankLabel = currentRank == null ? config.notRankedText : String.valueOf(currentRank);

            // Build placeholders
            Map<String, String> cph = Map.of(
                    "%candidate_name%", candidate.getName(),
                    "%candidate_party%", party,
                    "%current_rank%", currentRankLabel
            );

            // Build FULL label as Component
            Component sliderLabel = miniMessage(applyPlaceholders(config.candidateSliderLabelFormat, cph));

            // Build dialog slider
            NumberRangeDialogInput range = DialogInput
                    .numberRange(sliderKey, sliderLabel, 0, maxRank)
                    //.labelFormat("%s")
                    .step(1f)
                    .initial(currentRank == null ? 0f : currentRank.floatValue())
                    .build();

            dialogBuilder.addInput(range);
        }

        dialogBuilder.buttonWithPlayer(miniMessage(config.submitBtn), null, (playerActor, response) -> {
            Map<Integer, Integer> ranksByCandidate = new HashMap<>();
            for (Map.Entry<String, Integer> entry : sliderKeyToCandidateId.entrySet()) {
                String key = entry.getKey();
                Float v = response.getFloat(key);
                int rank = (v == null ? 0 : Math.round(v));
                if (rank >= 1 && rank <= maxRank) {
                    ranksByCandidate.put(entry.getValue(), rank);
                }
            }

            // Persist back into the session
            session.clearAll();
            for (Map.Entry<Integer, Integer> e : ranksByCandidate.entrySet()) {
                session.setRank(e.getKey(), e.getValue());
            }

            if (ranksByCandidate.size() < min) {
                String base = BallotError.INSUFFICIENT_PREFERENCES.errorString();
                String detail = applyPlaceholders(config.selectAtLeast, Map.of("%min%", String.valueOf(min)));
                new ErrorMenu(playerActor, getParentMenu(),
                        "error_pref_min_prefs_" + electionId + "_" + playerActor.getUniqueId(),
                        java.util.List.of(base, detail)).open();
                return;
            }

            int maximum = Math.max(1, election.getCandidates().size());
            Set<Integer> seen = new HashSet<>();
            for (Integer r : ranksByCandidate.values()) {
                if (r == null || r < 1 || r > maximum) {
                    String base = BallotError.INVALID_RANK.errorString();
                    String detail = applyPlaceholders(config.invalidRank, Map.of("%rank%", String.valueOf(r)));
                    new ErrorMenu(playerActor, getParentMenu(),
                            "error_pref_invalid_rank_bounds_" + electionId + "_" + playerActor.getUniqueId(),
                            List.of(base, detail)).open();
                    return;
                }
                if (!seen.add(r)) {
                    String base = BallotError.DUPLICATE_RANKING.errorString();
                    String detail = applyPlaceholders(config.duplicateRank, Map.of("%rank%", String.valueOf(r)));
                    new ErrorMenu(playerActor, getParentMenu(),
                            "error_pref_duplicate_rank_" + electionId + "_" + playerActor.getUniqueId(),
                            List.of(base, detail)).open();
                    return;
                }
            }

            List<Map.Entry<Integer, Integer>> orderedEntries = new ArrayList<>(ranksByCandidate.entrySet());
            orderedEntries.sort(Comparator.comparingInt(Map.Entry::getValue));
            List<Integer> orderedCandidateIds = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : orderedEntries) orderedCandidateIds.add(entry.getKey());

            new LoadingMenu(playerActor, getParentMenu(), miniMessage(config.loadingTitle, placeholders), miniMessage(config.loadingMessage, placeholders)).open();
            new BukkitRunnable() {
                @Override
                public void run() {
                    int voterId = electionsService.registerVoterAsync(electionId, playerActor.getName()).join().getId();
                    boolean ok = electionsService.submitPreferentialBallotAsync(electionId, voterId, orderedCandidateIds).join();
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            playerActor.closeDialog();
                            if (!ok) {
                                String base = BallotError.SUBMISSION_FAILED.errorString();
                                String detail = config.submissionFailed;
                                new ErrorMenu(playerActor, getParentMenu(),
                                        "error_pref_submit_" + electionId + "_" + playerActor.getUniqueId(),
                                        java.util.List.of(base, detail)).open();
                            } else {
                                playerActor.sendMessage(miniMessage(config.submitted));
                                SoundHelper.play(playerActor, config.successSound);
                                BallotSessions.clear(playerActor.getUniqueId(), electionId);
                            }
                        }
                    }.runTask(Elections.getInstance());
                }
            }.runTaskAsynchronously(Elections.getInstance());
        });

        dialogBuilder.button(miniMessage(config.clearBtn), context -> {
            session.clearAll();
            new PreferentialBallotMenu(context.player(), getParentMenu(), electionsService, electionId).open();
        });

        dialogBuilder.buttonWithPlayer(miniMessage(config.backBtn), null, (playerActor, response) -> {
            for (Map.Entry<String, Integer> entry : sliderKeyToCandidateId.entrySet()) {
                String key = entry.getKey();
                Integer candidateId = entry.getValue();
                Float v = response.getFloat(key);
                if (v == null) continue;
                int rank = Math.round(v);
                if (rank >= 1 && rank <= maxRank) {
                    session.setRank(candidateId, rank);
                } else {
                    session.clearRank(candidateId);
                }
            }
            getParentMenu().open();
        });

        return dialogBuilder.build();
    }

    private String selKey(int candidateId) { return "CAND_" + candidateId; }
    private String rankKey(int candidateId) { return "RANK_" + candidateId; }
}
