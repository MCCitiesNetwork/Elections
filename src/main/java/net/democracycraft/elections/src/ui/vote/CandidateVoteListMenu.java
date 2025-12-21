package net.democracycraft.elections.src.ui.vote;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
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
 * Candidate list menu: shows buttons for each candidate and allows navigating to a per-candidate screen.
 * Also provides Submit/Clear/Back actions and persists state via BallotSessions. All texts configurable via YAML.
 */
public class CandidateVoteListMenu extends ChildMenuImp {

    private final ElectionsService electionsService;
    private final int electionId;
    private final int page;
    private final int pageSize = 15;

    public CandidateVoteListMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId) {
        this(player, parent, electionsService, electionId, 0);
    }

    public CandidateVoteListMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId, int page) {
        super(player, parent, "ballot_list_" + electionId + "_" + Math.max(0, page));
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.page = Math.max(0, page);
        this.setDialog(build());
    }


    /** Config DTO for this menu. */
    public static class Config implements Serializable {
        /** Title to use when election is missing. */
        public String titleFallback = "<gold><bold>Ballot</bold></gold>";
        /** Message to show when election is not found. */
        public String notFound = "<red><bold>Election not found.</bold></red>";
        /** Title format. Placeholders: %election_title%. */
        public String titleFormat = "<gold><bold>%election_title% Candidates</bold></gold>";
        /** Instruction for block voting. Placeholder: %min%. */
        public String blockInstr = "<gray>Select exactly <white><bold>%min%</bold></white> candidates.</gray>";
        /** Instruction for preferential voting. Placeholder: %min%. */
        public String prefInstr = "<gray>Assign unique ranks starting at 1. Minimum preferences: <white><bold>%min%</bold></white></gray>";
        /** Label shown before selected count. */
        public String selectedLabel = "<aqua>Selected: </aqua>";
        /** Submit button label. */
        public String submitBtn = "<green><bold>Submit</bold></green>";
        /** Clear button label. */
        public String clearBtn = "<yellow>Clear all</yellow>";
        /** Clear confirmation button label. */
        public String clearConfirmBtn = "<yellow>Confirm Clear</yellow>";

        public String clearConfirmTitle = "<red><bold>Confirm Clear Selections</bold></red>";
        /** Back button label. */
        public String backBtn = "<red><bold>Back</bold></red>";
        /** Next page button label. */
        public String nextBtn = "<dark_gray>Next ▶</dark_gray>";
        /** Previous page button label. */
        public String prevBtn = "<dark_gray>◀ Prev</dark_gray>";

        /** Error shown when block ballot does not meet exact selection count. Placeholder: %min%. */
        public String mustSelectExactly = "<red><bold>You must select exactly %min% candidates.</bold></red>";
        /** Error shown when submission fails. */
        public String submissionFailed = "<red><bold>Submission failed. Are you eligible or already voted?</bold></red>";
        /** Message shown when ballot is submitted. */
        public String submitted = "<green><bold>Ballot submitted.</bold></green>";
        /** Error when no preferences set. */
        public String noPrefs = "<red><bold>No preferences set.</bold></red>";
        /** Error when rank is invalid. Placeholder: %rank%. */
        public String invalidRank = "<red><bold>Invalid rank: %rank%</bold></red>";
        /** Error when duplicate rank detected. Placeholder: %rank%. */
        public String duplicateRank = "<red><bold>Duplicate rank: %rank%</bold></red>";
        /** Error when fewer than minimum preferences selected. Placeholder: %min%. */
        public String selectAtLeast = "<red><bold>Select at least %min% preferences.</bold></red>";
        /** Header comment for generated YAML. Lists supported placeholders. */
        public String yamlHeader = "CandidateListMenu configuration. Placeholders: %election_title%, %min%, %rank%, %candidate_name%, %candidate_party%.";
        /** Loading dialog title shown while submitting. */
        public String loadingTitle = "<gold><bold>Submitting</bold></gold>";
        /** Loading dialog message shown while submitting. */
        public String loadingMessage = "<gray><italic>Submitting your ballot…</italic></gray>";
        /** Sound to play when submission succeeds. */
        public SoundSpec successSound = new SoundSpec();
        /** Tag appended when a candidate is currently selected in Block system. */
        public String selectedTag = " <dark_gray>[Selected]</dark_gray>";
        /** Tag format appended showing the rank in Preferential system. Placeholder: %rank%. */
        public String rankTag = " <dark_gray>[Rank: %rank%]</dark_gray>";
        public String valueGrayFormat = "<gray>%value%</gray>";
        /** Label format for each candidate button. Placeholders: %candidate_name%, %candidate_party%. */
        public String candidateLabelFormat = "<white>%candidate_name%</white> <dark_gray>(%candidate_party%)</dark_gray>";
        /** Label to use when a candidate has no party set (null/blank). */
        public String partyUnknown = "Independent";
        /** Whether the dialog can be closed with Escape. */
        public boolean canCloseWithEscape = true;
        public Config() {}

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(CandidateVoteListMenu.Config.class, "CandidateListMenu.yml", new CandidateVoteListMenu.Config().yamlHeader);
            yml.loadOrCreate(CandidateVoteListMenu.Config::new);
        }
    }

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
        VotingSystem system = election.getSystem();
        BallotSessions.Session session = BallotSessions.get(getPlayer().getUniqueId(), electionId, system);
        session.setSystem(system);

        Map<String, String> placeholders = Map.of(
                "%election_title%", election.getTitle(),
                "%min%", String.valueOf(Math.max(1, election.getMinimumVotes()))
        );

        dialogBuilder.title(miniMessage(config.titleFormat, placeholders));
        dialogBuilder.canCloseWithEscape(config.canCloseWithEscape);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        if (system == VotingSystem.BLOCK) {
            dialogBuilder.addBody(DialogBody.plainMessage(Component.newline()
                    .append(miniMessage(config.blockInstr, placeholders)).appendNewline()
                    .append(miniMessage(config.selectedLabel, placeholders)).append(miniMessage(applyPlaceholders(config.valueGrayFormat, Map.of("%value%", String.valueOf(session.selectedCount()))), null))));
        } else {
            dialogBuilder.addBody(DialogBody.plainMessage(Component.newline()
                    .append(miniMessage(config.prefInstr, placeholders)).appendNewline()));
        }

        List<Candidate> all = election.getCandidates();
        int from = Math.min(page * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        for (int i = from; i < to; i++) {
            Candidate candidate = all.get(i);
            String stateText;
            if (system == VotingSystem.BLOCK) {
                stateText = session.isSelected(candidate.getId()) ? config.selectedTag : "";
            } else {
                Integer rankValue = session.getRank(candidate.getId());
                stateText = (rankValue != null) ? applyPlaceholders(config.rankTag, Map.of("%rank%", String.valueOf(rankValue))) : "";
            }
            String party = candidate.getParty();
            if (party == null || party.isBlank()) party = config.partyUnknown;
            String labelMini = applyPlaceholders(config.candidateLabelFormat, Map.of(
                    "%candidate_name%", formatCandidateName(candidate.getName()),
                    "%candidate_party%", formatCandidateParty(candidate.getName(), party),
                    "%state%", stateText
            ));
            dialogBuilder.button(miniMessage(labelMini, null), context -> new CandidateVoteMenu(context.player(), this.getParentMenu(), electionsService, electionId, candidate.getId()).open());
        }
        if (page > 0) dialogBuilder.button(miniMessage(config.prevBtn, placeholders), c -> new CandidateVoteListMenu(c.player(), getParentMenu(), electionsService, electionId, page - 1).open());
        if (to < all.size()) dialogBuilder.button(miniMessage(config.nextBtn, placeholders), c -> new CandidateVoteListMenu(c.player(), getParentMenu(), electionsService, electionId, page + 1).open());

        dialogBuilder.buttonWithPlayer(miniMessage(config.submitBtn, placeholders), null, (playerActor, response) -> {
            if (system == VotingSystem.BLOCK) {
                List<Integer> pickedCandidates = session.getSelected();
                int minimumRequired = Math.max(1, election.getMinimumVotes());
                if (pickedCandidates.size() != minimumRequired) {
                    String base = BallotError.MUST_SELECT_EXACTLY_MIN.errorString();
                    String detail = applyPlaceholders(config.mustSelectExactly, Map.of("%min%", String.valueOf(minimumRequired)));
                    new ErrorMenu(playerActor, getParentMenu(),
                            "error_list_block_min_" + electionId + "_" + playerActor.getUniqueId(),
                            List.of(base, detail)).open();
                    return;
                }
                new LoadingMenu(playerActor, getParentMenu(), miniMessage(config.loadingTitle, placeholders), miniMessage(config.loadingMessage, placeholders)).open();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        int voterId = electionsService.registerVoterAsync(electionId, playerActor.getName()).join().getId();
                        boolean submissionOk = electionsService.submitBlockBallotAsync(electionId, voterId, pickedCandidates).join();
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                playerActor.closeDialog();
                                if (!submissionOk) {
                                    String base = BallotError.SUBMISSION_FAILED.errorString();
                                    String detail = config.submissionFailed;
                                    new ErrorMenu(playerActor, getParentMenu(),
                                            "error_list_block_submit_" + electionId + "_" + playerActor.getUniqueId(),
                                            List.of(base, detail)).open();
                                } else {
                                    playerActor.sendMessage(miniMessage(config.submitted));
                                    SoundHelper.play(playerActor, config.successSound);
                                    BallotSessions.clear(playerActor.getUniqueId(), electionId);
                                }
                            }
                        }.runTask(Elections.getInstance());
                    }
                }.runTaskAsynchronously(Elections.getInstance());
            } else {
                Map<Integer,Integer> ranksByCandidate = session.getAllRanks();
                if (ranksByCandidate.isEmpty()) {
                    String base = BallotError.INSUFFICIENT_PREFERENCES.errorString();
                    String detail = config.noPrefs;
                    new ErrorMenu(playerActor, getParentMenu(),
                            "error_list_pref_none_" + electionId + "_" + playerActor.getUniqueId(),
                            List.of(base, detail)).open();
                    return;
                }
                int minimumRequired = Math.max(1, election.getMinimumVotes());
                int maxRank = Math.max(1, all.size());
                Set<Integer> seenRanks = new HashSet<>();
                for (Map.Entry<Integer,Integer> rankEntry : ranksByCandidate.entrySet()) {
                    Integer rankValue = rankEntry.getValue();
                    if (rankValue == null || rankValue < 1 || rankValue > maxRank) {
                        String base = BallotError.INVALID_RANK.errorString();
                        String detail = applyPlaceholders(config.invalidRank, Map.of("%rank%", String.valueOf(rankValue)));
                        new ErrorMenu(playerActor, getParentMenu(),
                                "error_list_pref_invalid_" + electionId + "_" + playerActor.getUniqueId(),
                                List.of(base, detail)).open();
                        return;
                    }
                    if (!seenRanks.add(rankValue)) {
                        String base = BallotError.DUPLICATE_RANKING.errorString();
                        String detail = applyPlaceholders(config.duplicateRank, Map.of("%rank%", String.valueOf(rankValue)));
                        new ErrorMenu(playerActor, getParentMenu(),
                                "error_list_pref_duplicate_" + electionId + "_" + playerActor.getUniqueId(),
                                List.of(base, detail)).open();
                        return;
                    }
                }
                if (ranksByCandidate.size() < minimumRequired) {
                    String base = BallotError.INSUFFICIENT_PREFERENCES.errorString();
                    String detail = applyPlaceholders(config.selectAtLeast, Map.of("%min%", String.valueOf(minimumRequired)));
                    new ErrorMenu(playerActor, getParentMenu(),
                            "error_list_pref_min_" + electionId + "_" + playerActor.getUniqueId(),
                            List.of(base, detail)).open();
                    return;
                }
                List<Map.Entry<Integer,Integer>> entries = new ArrayList<>(ranksByCandidate.entrySet());
                entries.sort(java.util.Comparator.comparingInt(Map.Entry::getValue));
                List<Integer> orderedCandidateIds = new ArrayList<>();
                for (Map.Entry<Integer,Integer> entry : entries) orderedCandidateIds.add(entry.getKey());
                new LoadingMenu(playerActor, getParentMenu(), miniMessage(config.loadingTitle, placeholders), miniMessage(config.loadingMessage, placeholders)).open();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        int voterId = electionsService.registerVoterAsync(electionId, playerActor.getName()).join().getId();
                        boolean submissionOk = electionsService.submitPreferentialBallotAsync(electionId, voterId, orderedCandidateIds).join();
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                playerActor.closeDialog();
                                if (!submissionOk) {
                                    String base = BallotError.SUBMISSION_FAILED.errorString();
                                    String detail = config.submissionFailed;
                                    new ErrorMenu(playerActor, getParentMenu(),
                                            "error_list_pref_submit_" + electionId + "_" + playerActor.getUniqueId(),
                                            List.of(base, detail)).open();
                                } else {
                                    playerActor.sendMessage(miniMessage(config.submitted));
                                    SoundHelper.play(playerActor, config.successSound);
                                    BallotSessions.clear(playerActor.getUniqueId(), electionId);
                                }
                            }
                        }.runTask(Elections.getInstance());
                    }
                }.runTaskAsynchronously(Elections.getInstance());
            }
        });

        dialogBuilder.button(miniMessage(config.clearBtn, placeholders), context -> {
            AutoDialog.Builder confirm = getAutoDialogBuilder();
            confirm.title(miniMessage(config.clearConfirmTitle, placeholders));
            confirm.button(miniMessage(config.clearConfirmBtn, placeholders), c2 -> { BallotSessions.get(c2.player().getUniqueId(), electionId, system).clearAll(); new CandidateVoteListMenu(c2.player(), getParentMenu(), electionsService, electionId, page).open(); });
            confirm.button(miniMessage(config.backBtn, placeholders), c2 -> new CandidateVoteListMenu(c2.player(), getParentMenu(), electionsService, electionId, page).open());
            context.player().showDialog(confirm.build());
        });
        dialogBuilder.button(miniMessage(config.backBtn, placeholders), context -> getParentMenu().open());

        return dialogBuilder.build();
    }
}
