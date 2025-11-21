package net.democracycraft.elections.ui.vote;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.model.Candidate;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.data.VotingSystem;
import net.democracycraft.elections.ui.ChildMenuImp;
import net.democracycraft.elections.api.ui.AutoDialog;
import net.democracycraft.elections.ui.common.LoadingMenu;
import net.democracycraft.elections.util.sound.SoundHelper;
import net.democracycraft.elections.util.sound.SoundSpec;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.Serializable;
import java.util.*;

/**
 * Candidate list menu: shows buttons for each candidate and allows navigating to a per-candidate screen.
 * Also provides Submit/Clear/Back actions and persists state via BallotSessions. All texts configurable via YAML.
 */
public class CandidateListMenu extends ChildMenuImp {

    private final ElectionsService electionsService;
    private final int electionId;
    private final int page;
    private final int pageSize = 15;

    public CandidateListMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId) {
        this(player, parent, electionsService, electionId, 0);
    }

    public CandidateListMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId, int page) {
        super(player, parent, "ballot_list_" + electionId + "_" + Math.max(0, page));
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.page = Math.max(0, page);
        this.setDialog(build());
    }

    /** Config DTO for this menu. */
    public static class Config implements Serializable {
        public String titleFallback = "<gold><bold>Ballot</bold></gold>";
        public String notFound = "<red><bold>Election not found.</bold></red>";
        public String titleFormat = "<gold><bold>%election_title% Candidates</bold></gold>";
        public String blockInstr = "<gray>Select exactly <white><bold>%min%</bold></white> candidates.</gray>";
        public String prefInstr = "<gray>Assign unique ranks starting at 1. Minimum preferences: <white><bold>%min%</bold></white></gray>";
        public String selectedLabel = "<aqua>Selected: </aqua>";
        public String submitBtn = "<green><bold>Submit</bold></green>";
        public String clearBtn = "<yellow>Clear all</yellow>";
        public String clearConfirmBtn = "<yellow>Confirm Clear</yellow>";
        public String backBtn = "<red><bold>Back</bold></red>";
        public String nextBtn = "<gray>Next ▶</gray>";
        public String prevBtn = "<gray>◀ Prev</gray>";

        public String mustSelectExactly = "<red><bold>You must select exactly %min% candidates.</bold></red>";
        public String submissionFailed = "<red><bold>Submission failed. Are you eligible or already voted?</bold></red>";
        public String submitted = "<green><bold>Ballot submitted.</bold></green>";
        public String noPrefs = "<red><bold>No preferences set.</bold></red>";
        public String invalidRank = "<red><bold>Invalid rank: %rank%</bold></red>";
        public String duplicateRank = "<red><bold>Duplicate rank: %rank%</bold></red>";
        public String selectAtLeast = "<red><bold>Select at least %min% preferences.</bold></red>";
        public String yamlHeader = "CandidateListMenu configuration. Placeholders: %election_title%, %min%, %rank%.";
        /** Loading dialog title shown while submitting. */
        public String loadingTitle = "<gold><bold>Submitting</bold></gold>";
        /** Loading dialog message shown while submitting. */
        public String loadingMessage = "<gray><italic>Submitting your ballot…</italic></gray>";
        /** Sound to play when submission succeeds. */
        public SoundSpec successSound = new SoundSpec();
        public String selectedTag = " <gray>[Selected]</gray>";
        public String rankTagFormat = " <gray>[%rank%]</gray>";
        public String candidateLabelFormat = "<white><bold>%candidate_name%</bold></white>%state%";
        public String valueGrayFormat = "<gray>%value%</gray>";
        public String clearConfirmTitle = "<yellow><bold>Clear all selections?</bold></yellow>";
        public Config() {}
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
        dialogBuilder.canCloseWithEscape(true);
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
                stateText = (rankValue != null) ? applyPlaceholders(config.rankTagFormat, Map.of("%rank%", String.valueOf(rankValue))) : "";
            }
            String labelMini = applyPlaceholders(config.candidateLabelFormat, Map.of("%candidate_name%", candidate.getName(), "%state%", stateText));
            dialogBuilder.button(miniMessage(labelMini, null), context -> new CandidateVoteMenu(context.player(), this.getParentMenu(), electionsService, electionId, candidate.getId()).open());
        }
        if (page > 0) dialogBuilder.button(miniMessage(config.prevBtn, placeholders), c -> new CandidateListMenu(c.player(), getParentMenu(), electionsService, electionId, page - 1).open());
        if (to < all.size()) dialogBuilder.button(miniMessage(config.nextBtn, placeholders), c -> new CandidateListMenu(c.player(), getParentMenu(), electionsService, electionId, page + 1).open());

        dialogBuilder.buttonWithPlayer(miniMessage(config.submitBtn, placeholders), null, (playerActor, response) -> {
            if (system == VotingSystem.BLOCK) {
                List<Integer> pickedCandidates = session.getSelected();
                int minimumRequired = Math.max(1, election.getMinimumVotes());
                if (pickedCandidates.size() != minimumRequired) { playerActor.sendMessage(miniMessage(applyPlaceholders(config.mustSelectExactly, Map.of("%min%", String.valueOf(minimumRequired))), null)); new CandidateListMenu(playerActor, getParentMenu(), electionsService, electionId, page).open(); return; }
                new LoadingMenu(playerActor, getParentMenu(), miniMessage(config.loadingTitle, placeholders), miniMessage(config.loadingMessage, placeholders)).open();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        int voterId = electionsService.registerVoterAsync(electionId, playerActor.getName()).join().getId();
                        boolean submissionOk = electionsService.submitBlockBallotAsync(electionId, voterId, pickedCandidates).join();
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!submissionOk) { playerActor.sendMessage(miniMessage(config.submissionFailed)); new CandidateListMenu(playerActor, getParentMenu(), electionsService, electionId, page).open(); }
                                else { playerActor.sendMessage(miniMessage(config.submitted)); SoundHelper.play(playerActor, config.successSound); BallotSessions.clear(playerActor.getUniqueId(), electionId); }
                            }
                        }.runTask(Elections.getInstance());
                    }
                }.runTaskAsynchronously(Elections.getInstance());
            } else {
                Map<Integer,Integer> ranksByCandidate = session.getAllRanks();
                if (ranksByCandidate.isEmpty()) { playerActor.sendMessage(miniMessage(config.noPrefs)); new CandidateListMenu(playerActor, getParentMenu(), electionsService, electionId, page).open(); return; }
                int minimumRequired = Math.max(1, election.getMinimumVotes());
                int maxRank = Math.max(1, all.size());
                Set<Integer> seenRanks = new HashSet<>();
                for (Map.Entry<Integer,Integer> rankEntry : ranksByCandidate.entrySet()) {
                    Integer rankValue = rankEntry.getValue();
                    if (rankValue == null || rankValue < 1 || rankValue > maxRank) { playerActor.sendMessage(miniMessage(applyPlaceholders(config.invalidRank, Map.of("%rank%", String.valueOf(rankValue))), null)); new CandidateListMenu(playerActor, getParentMenu(), electionsService, electionId, page).open(); return; }
                    if (!seenRanks.add(rankValue)) { playerActor.sendMessage(miniMessage(applyPlaceholders(config.duplicateRank, Map.of("%rank%", String.valueOf(rankValue))), null)); new CandidateListMenu(playerActor, getParentMenu(), electionsService, electionId, page).open(); return; }
                }
                if (ranksByCandidate.size() < minimumRequired) { playerActor.sendMessage(miniMessage(applyPlaceholders(config.selectAtLeast, Map.of("%min%", String.valueOf(minimumRequired))), null)); new CandidateListMenu(playerActor, getParentMenu(), electionsService, electionId, page).open(); return; }
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
                                if (!submissionOk) { playerActor.sendMessage(miniMessage(config.submissionFailed)); new CandidateListMenu(playerActor, getParentMenu(), electionsService, electionId, page).open(); }
                                else { playerActor.sendMessage(miniMessage(config.submitted)); SoundHelper.play(playerActor, config.successSound); BallotSessions.clear(playerActor.getUniqueId(), electionId); }
                            }
                        }.runTask(Elections.getInstance());
                    }
                }.runTaskAsynchronously(Elections.getInstance());
            }
        });

        dialogBuilder.button(miniMessage(config.clearBtn, placeholders), context -> {
            AutoDialog.Builder confirm = getAutoDialogBuilder();
            confirm.title(miniMessage(config.clearConfirmTitle, placeholders));
            confirm.button(miniMessage(config.clearConfirmBtn, placeholders), c2 -> { BallotSessions.get(c2.player().getUniqueId(), electionId, system).clearAll(); new CandidateListMenu(c2.player(), getParentMenu(), electionsService, electionId, page).open(); });
            confirm.button(miniMessage(config.backBtn, placeholders), c2 -> new CandidateListMenu(c2.player(), getParentMenu(), electionsService, electionId, page).open());
            context.player().showDialog(confirm.build());
        });
        dialogBuilder.button(miniMessage(config.backBtn, placeholders), context -> getParentMenu().open());

        return dialogBuilder.build();
    }
}
