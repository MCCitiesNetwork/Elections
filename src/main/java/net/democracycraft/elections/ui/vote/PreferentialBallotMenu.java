package net.democracycraft.elections.ui.vote;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.model.Candidate;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.ui.ChildMenuImp;
import net.democracycraft.elections.util.HeadUtil;
import net.democracycraft.elections.ui.dialog.AutoDialog;
import net.democracycraft.elections.ui.common.LoadingMenu;
import net.democracycraft.elections.util.sound.SoundHelper;
import net.democracycraft.elections.util.sound.SoundSpec;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.Serializable;
import java.time.Duration;
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
        public String rankForFormat = "<aqua>Rank (1..%max%) for %candidate_name%</aqua>";
        public String yamlHeader = "PreferentialBallotMenu configuration. Placeholders: %election_title%, %min%, %max%, %rank%, %candidate_name%.";
        /** Loading dialog title shown while submitting. */
        public String loadingTitle = "<gold><bold>Submitting</bold></gold>";
        /** Loading dialog message shown while submitting. */
        public String loadingMessage = "<gray><italic>Submitting your ballot…</italic></gray>";
        /** Sound to play when submission succeeds. */
        public SoundSpec successSound = new SoundSpec();
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
                .append(miniMessage(config.minPrefsLabel, placeholders)).append(miniMessage("<gray>" + min + "</gray>", null))
                .appendNewline().append(miniMessage(config.instruction, placeholders))
        ));

        List<Candidate> candidates = election.getCandidates();
        Map<String, Integer> selectKeyToId = new LinkedHashMap<>();
        Map<String, Integer> rankKeyToId = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            String selectKey = selKey(candidate.getId());
            String rankKey = rankKey(candidate.getId());
            selectKeyToId.put(selectKey, candidate.getId());
            rankKeyToId.put(rankKey, candidate.getId());
            HeadUtil.updateHeadItemBytesAsync(electionsService, electionId, candidate.getId(), candidate.getName());
            dialogBuilder.addBody(DialogBody.item(HeadUtil.headFromBytesOrName(candidate.getId(), candidate.getName())).showTooltip(true).build());
            dialogBuilder.addInput(DialogInput.bool(selectKey, miniMessage("<gray>" + candidate.getName() + "</gray>")).initial(false).build());
            dialogBuilder.addInput(DialogInput.text(rankKey, miniMessage(applyPlaceholders(config.rankForFormat, Map.of("%max%", String.valueOf(maxRank), "%candidate_name%", candidate.getName())), null)).labelVisible(true).build());
        }

        dialogBuilder.buttonWithPlayer(miniMessage(config.submitBtn), null, Duration.ofMinutes(5), 1, (playerActor, response) -> {
            List<Map.Entry<Integer, Integer>> selections = new ArrayList<>();
            Set<Integer> seenRanks = new HashSet<>();
            for (Map.Entry<String, Integer> entry : selectKeyToId.entrySet()) {
                Boolean selected = response.getBoolean(entry.getKey());
                if (selected != null && selected) {
                    String rk = rankKey(entry.getValue());
                    String text = response.getText(rk);
                    if (text == null || text.isBlank()) { playerActor.sendMessage(miniMessage(config.missingRank)); new PreferentialBallotMenu(playerActor, getParentMenu(), electionsService, electionId).open(); return; }
                    int rank;
                    try { rank = Integer.parseInt(text.trim()); } catch (NumberFormatException ex) { playerActor.sendMessage(miniMessage(applyPlaceholders(config.invalidRank, Map.of("%rank%", text)), null)); new PreferentialBallotMenu(playerActor, getParentMenu(), electionsService, electionId).open(); return; }
                    if (rank < 1 || rank > maxRank) { playerActor.sendMessage(miniMessage(applyPlaceholders(config.invalidRank, Map.of("%rank%", String.valueOf(rank))), null)); new PreferentialBallotMenu(playerActor, getParentMenu(), electionsService, electionId).open(); return; }
                    if (!seenRanks.add(rank)) { playerActor.sendMessage(miniMessage(applyPlaceholders(config.duplicateRank, Map.of("%rank%", String.valueOf(rank))), null)); new PreferentialBallotMenu(playerActor, getParentMenu(), electionsService, electionId).open(); return; }
                    selections.add(Map.entry(entry.getValue(), rank));
                }
            }
            if (selections.size() < min) { playerActor.sendMessage(miniMessage(applyPlaceholders(config.selectAtLeast, Map.of("%min%", String.valueOf(min))), null)); new PreferentialBallotMenu(playerActor, getParentMenu(), electionsService, electionId).open(); return; }
            selections.sort(Comparator.comparingInt(Map.Entry::getValue));
            List<Integer> ordered = new ArrayList<>();
            for (Map.Entry<Integer, Integer> pair : selections) ordered.add(pair.getKey());
            new LoadingMenu(playerActor, getParentMenu(), miniMessage(config.loadingTitle, placeholders), miniMessage(config.loadingMessage, placeholders)).open();
            new BukkitRunnable() {
                @Override
                public void run() {
                    int voterId = electionsService.registerVoterAsync(electionId, playerActor.getName()).join().getId();
                    boolean success = electionsService.submitPreferentialBallotAsync(electionId, voterId, ordered).join();
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            // Close loading dialog after async completes
                            playerActor.closeDialog();
                            if (!success) {
                                playerActor.sendMessage(miniMessage(config.submissionFailed));
                                new PreferentialBallotMenu(playerActor, getParentMenu(), electionsService, electionId).open();
                            } else {
                                playerActor.sendMessage(miniMessage(config.submitted));
                                SoundHelper.play(playerActor, config.successSound);
                            }
                        }
                    }.runTask(Elections.getInstance());
                }
            }.runTaskAsynchronously(Elections.getInstance());
        });

        dialogBuilder.button(miniMessage(config.clearBtn), context -> new PreferentialBallotMenu(context.player(), getParentMenu(), electionsService, electionId).open());
        dialogBuilder.button(miniMessage(config.backBtn), context -> getParentMenu().open());

        return dialogBuilder.build();
    }

    private String selKey(int candidateId) { return "CAND_" + candidateId; }
    private String rankKey(int candidateId) { return "RANK_" + candidateId; }
}
