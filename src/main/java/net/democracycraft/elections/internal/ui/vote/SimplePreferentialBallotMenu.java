package net.democracycraft.elections.internal.ui.vote;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.model.BallotError;
import net.democracycraft.elections.api.model.Candidate;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.internal.ui.ChildMenuImp;
import net.democracycraft.elections.api.ui.AutoDialog;
import net.democracycraft.elections.internal.ui.common.ErrorMenu;
import net.democracycraft.elections.internal.ui.common.LoadingMenu;
import net.democracycraft.elections.internal.util.sound.SoundHelper;
import net.democracycraft.elections.internal.util.sound.SoundSpec;
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
        public String instruction = "<gray>Click candidates to assign the next available rank. Click again to unassign.</gray>";
        public String nextRankMsg = "<aqua>Next rank to assign: <white><bold>#%next_rank%</bold></white></aqua>";
        public String allRankedMsg = "<green><bold>All candidates ranked.</bold></green>";
        public String formatRanked = "<green>[%rank%]</green> <white>%candidate_name%</white> <dark_gray>(%candidate_party%)</dark_gray>";
        public String formatUnranked = "<dark_gray>[ - ]</dark_gray> <white>%candidate_name%</white> <dark_gray>(%candidate_party%)</dark_gray>";
        public String submitBtn = "<green><bold>Submit</bold></green>";
        public String clearBtn = "<yellow>Clear all</yellow>";
        public String backBtn = "<red><bold>Back</bold></red>";
        public String submissionFailed = "<red><bold>Submission failed. Are you eligible or already voted?</bold></red>";
        public String submitted = "<green><bold>Ballot submitted.</bold></green>";
        public String invalidRank = "<red><bold>Invalid rank: %rank%</bold></red>";
        public String duplicateRank = "<red><bold>Duplicate rank: %rank%</bold></red>";
        public String selectAtLeast = "<red><bold>Select at least %min% preferences.</bold></red>";/** Header comment describing placeholders supported. */
        public String yamlHeader = "SimplePreferentialBallotMenu configuration. Placeholders: %election_title%, %min%, %candidate_name%, %candidate_party%, %rank%, %next_rank%.";
        /** Loading dialog title shown while submitting. */
        public String loadingTitle = "<gold><bold>Submitting</bold></gold>";
        /** Loading dialog message shown while submitting. */
        public String loadingMessage = "<gray><italic>Submitting your ballot…</italic></gray>";
        /** Sound to play when submission succeeds. */
        public SoundSpec successSound = new SoundSpec();
        public String partyUnknown = "Independent";
        /** Whether the dialog can be closed with Escape. */
        public boolean canCloseWithEscape = true;
        public Config() {}


        public static void loadConfig() {
            var yml = getOrCreateMenuYml(Config.class, "SimplePreferentialBallotMenu.yml", new Config().yamlHeader);
            yml.loadOrCreate(Config::new);
        }
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

        BallotSessions.Session session = BallotSessions.get(getPlayer().getUniqueId(), electionId, election.getSystem());
        session.setSystem(election.getSystem());

        // Calculate next available rank
        Set<Integer> usedRanks = new HashSet<>(session.getAllRanks().values());
        int nextAvailableRank = 1;
        while (usedRanks.contains(nextAvailableRank)) {
            nextAvailableRank++;
        }
        final int nextRankFinal = nextAvailableRank;

        Map<String, String> ph = Map.of(
                "%election_title%", election.getTitle(),
                "%min%", String.valueOf(min),
                "%next_rank%", String.valueOf(nextRankFinal)
        );

        dialogBuilder.title(miniMessage(config.titleFormat, ph));
        dialogBuilder.canCloseWithEscape(config.canCloseWithEscape);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        // Next rank status message or all ranked
        Component rankStatusMsg = (nextRankFinal > election.getCandidates().size())
                ? miniMessage(config.allRankedMsg, ph)
                : miniMessage(config.nextRankMsg, ph);

        // Instruction body
        dialogBuilder.addBody(DialogBody.plainMessage(Component.newline()
                .append(miniMessage(config.instruction, ph))
                .appendNewline()
                .append(rankStatusMsg)
        ));
        // Candidate buttons
        for (Candidate c : election.getCandidates()) {
            Integer current = session.getRank(c.getId());
            String party = c.getParty();
            if (party == null || party.isBlank()) party = config.partyUnknown;

            String format = (current != null) ? config.formatRanked : config.formatUnranked;
            Map<String, String> cph = Map.of(
                    "%candidate_name%", formatCandidateName(c.getName()),
                    "%candidate_party%", formatCandidateParty(c.getName(), party),
                    "%rank%", current != null ? String.valueOf(current) : "-"
            );

            dialogBuilder.button(miniMessage(format, cph), ctx -> {
                if (current != null) {
                    session.clearRank(c.getId());
                } else {
                    session.setRank(c.getId(), nextRankFinal);
                }
                new SimplePreferentialBallotMenu(ctx.player(), getParentMenu(), electionsService, electionId).open();
            });
        }

        dialogBuilder.buttonWithPlayer(miniMessage(config.submitBtn, ph), null, (playerActor, response) -> {
            Map<Integer, Integer> ranksByCandidate = session.getAllRanks();

            if (ranksByCandidate.isEmpty() || ranksByCandidate.size() < min) {
                String base = BallotError.INSUFFICIENT_PREFERENCES.errorString();
                String detail = applyPlaceholders(config.selectAtLeast, Map.of("%min%", String.valueOf(min)));
                new ErrorMenu(playerActor, getParentMenu(), "error_simple_pref_min_" + electionId,
                        List.of(base, detail)).open();
                return;
            }
            int maximum = Math.max(1, election.getCandidates().size());
            Set<Integer> seen = new HashSet<>();
            for (Integer r : ranksByCandidate.values()) {
                if (r == null || r < 1 || r > maximum) {
                    String base = BallotError.INVALID_RANK.errorString();
                    String detail = applyPlaceholders(config.invalidRank, Map.of("%rank%", String.valueOf(r)));
                    new ErrorMenu(playerActor, getParentMenu(), "error_simple_pref_invalid_rank_" + electionId,
                            List.of(base, detail)).open();
                    return;
                }
                if (!seen.add(r)) {
                    String base = BallotError.DUPLICATE_RANKING.errorString();
                    String detail = applyPlaceholders(config.duplicateRank, Map.of("%rank%", String.valueOf(r)));
                    new ErrorMenu(playerActor, getParentMenu(), "error_simple_pref_dup_rank_" + electionId,
                            List.of(base, detail)).open();
                    return;
                }
            }
            List<Map.Entry<Integer, Integer>> entries = new ArrayList<>(ranksByCandidate.entrySet());
            entries.sort(java.util.Comparator.comparingInt(Map.Entry::getValue));
            List<Integer> orderedCandidateIds = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : entries) orderedCandidateIds.add(entry.getKey());
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
                            if (!ok) {
                                String base = BallotError.SUBMISSION_FAILED.errorString();
                                String detail = config.submissionFailed;
                                new ErrorMenu(playerActor, getParentMenu(), "error_simple_pref_submit_" + electionId,
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
        });

        dialogBuilder.button(miniMessage(config.clearBtn, ph), ctx -> { session.clearAll(); new SimplePreferentialBallotMenu(ctx.player(), getParentMenu(), electionsService, electionId).open(); });

        dialogBuilder.button(miniMessage(config.backBtn, ph), ctx -> getParentMenu().open());

        return dialogBuilder.build();
    }
}
