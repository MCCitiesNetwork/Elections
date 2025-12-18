package net.democracycraft.elections.src.ui.vote;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.model.BallotError;
import net.democracycraft.elections.api.model.Candidate;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.src.ui.ChildMenuImp;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.src.util.HeadUtil;
import net.democracycraft.elections.api.ui.AutoDialog;
import net.democracycraft.elections.src.ui.common.ErrorMenu;
import net.democracycraft.elections.src.ui.common.LoadingMenu;
import net.democracycraft.elections.src.util.sound.SoundHelper;
import net.democracycraft.elections.src.util.sound.SoundSpec;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ballot UI for Block voting. Voter must select exactly minimumVotes candidates using boolean inputs.
 * All texts are configurable via data/menus/BlockBallotMenu.yml with placeholders.
 */
public class BlockBallotMenu extends ChildMenuImp {

    private final ElectionsService electionsService;
    private final int electionId;

    public BlockBallotMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId) {
        super(player, parent, "ballot_block_" + electionId);
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }

    /** Config DTO for this menu. */
    public static class Config implements Serializable {
        public String titleFallback = "<gold><bold>Block Ballot</bold></gold>";
        public String notFound = "<red><bold>Election not found.</bold></red>";
        public String titleFormat = "<gold><bold>%election_title% Ballot (Block)</bold></gold>";
        public String minimumLabel = "<aqua>Minimum required: </aqua>";
        public String checkExactly = "<gray>Check exactly <white><bold>%min%</bold></white> candidates.</gray>";
        public String submitBtn = "<green><bold>Submit</bold></green>";
        public String mustSelectExactly = "<red><bold>You must select exactly %min% candidates.</bold></red>";
        public String submissionFailed = "<red><bold>Submission failed. Are you eligible or already voted?</bold></red>";
        public String submitted = "<green><bold>Ballot submitted.</bold></green>";
        public String clearBtn = "<yellow>Clear</yellow>";
        public String backBtn = "<red><bold>Back</bold></red>";
        /** Header describing placeholders supported in this menu. */
        public String yamlHeader = "BlockBallotMenu configuration. Placeholders: %election_title%, %min%, %candidate_name%, %candidate_party%, %x%, %y%, %z%.";
        /** Loading dialog title shown while submitting. */
        public String loadingTitle = "<gold><bold>Submitting</bold></gold>";
        /** Loading dialog message shown while submitting. */
        public String loadingMessage = "<gray><italic>Submitting your ballot…</italic></gray>";
        /** Sound to play when submission succeeds. */
        public SoundSpec successSound = new SoundSpec();
        /** Label format for each candidate row. Placeholders: %candidate_name%, %candidate_party%. */
        public String candidateLabelFormat = "<gray>%candidate_name% (%candidate_party%)</gray>";
        /** Label to use when a candidate has no party set (null/blank). */
        public String partyUnknown = "Independent";
        /** Whether the dialog can be closed with Escape. */
        public boolean canCloseWithEscape = true;
        public Config() {}

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(BlockBallotMenu.Config.class, "BallotIntroMenu.yml", new BlockBallotMenu.Config().yamlHeader);
            yml.loadOrCreate(BlockBallotMenu.Config::new);
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
        int min = Math.max(1, election.getMinimumVotes());

        Map<String, String> placeholders = Map.of(
                "%election_title%", election.getTitle(),
                "%min%", String.valueOf(min)
        );

        dialogBuilder.title(miniMessage(config.titleFormat, placeholders));
        dialogBuilder.canCloseWithEscape(config.canCloseWithEscape);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        dialogBuilder.addBody(DialogBody.plainMessage(Component.newline()
                .append(miniMessage(config.minimumLabel, placeholders)).append(miniMessage("<gray>" + min + "</gray>"))
                .appendNewline().append(miniMessage(config.checkExactly, placeholders))
        ));

        List<Candidate> candidates = election.getCandidates();
        Map<String, Integer> keyToId = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            String key = keyFor(candidate.getId());
            keyToId.put(key, candidate.getId());
            HeadUtil.updateHeadItemBytesAsync(electionsService, electionId, candidate.getId(), candidate.getName());
            ItemStack headItem = HeadUtil.headFromBytesOrName(electionsService, electionId, candidate.getId(), candidate.getName());
            dialogBuilder.addBody(DialogBody.item(headItem).showTooltip(true).build());
            String party = candidate.getParty();
            if (party == null || party.isBlank()) party = config.partyUnknown;
            boolean initiallySelected = BallotSessions.get(getPlayer().getUniqueId(), electionId, election.getSystem()).isSelected(candidate.getId());
            dialogBuilder.addInput(DialogInput.bool(key, miniMessage(applyPlaceholders(config.candidateLabelFormat, Map.of(
                    "%candidate_name%", candidate.getName(),
                    "%candidate_party%", party
            )), null)).initial(initiallySelected).build());
        }

        dialogBuilder.buttonWithPlayer(miniMessage(config.submitBtn, placeholders), null, (playerActor, response) -> {
            List<Integer> picks = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : keyToId.entrySet()) {
                Boolean selected = response.getBoolean(entry.getKey());
                if (selected != null && selected) picks.add(entry.getValue());
            }
            if (picks.size() != min) {
                String base = BallotError.MUST_SELECT_EXACTLY_MIN.errorString();
                String detail = applyPlaceholders(config.mustSelectExactly, Map.of("%min%", String.valueOf(min)));
                new ErrorMenu(playerActor, getParentMenu(),
                        "error_block_min_" + electionId + "_" + playerActor.getUniqueId(),
                        List.of(base, detail)).open();
                return;
            }
            new LoadingMenu(playerActor, getParentMenu(), miniMessage(config.loadingTitle, placeholders), miniMessage(config.loadingMessage, placeholders)).open();
            // Offload voter registration and ballot submission to async thread to avoid blocking the server tick.
            new BukkitRunnable() {
                @Override
                public void run() {
                    int voterId = electionsService.registerVoterAsync(electionId, playerActor.getName()).join().getId();
                    boolean success = electionsService.submitBlockBallotAsync(electionId, voterId, picks).join();
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            // Close loading dialog after async completes
                            playerActor.closeDialog();
                            if (!success) {
                                String base = BallotError.SUBMISSION_FAILED.errorString();
                                String detail = config.submissionFailed;
                                new ErrorMenu(playerActor, getParentMenu(),
                                        "error_block_submit_" + electionId + "_" + playerActor.getUniqueId(),
                                        List.of(base, detail)).open();
                            } else {
                                playerActor.sendMessage(miniMessage(config.submitted));
                                SoundHelper.play(playerActor, config.successSound);
                            }
                        }
                    }.runTask(Elections.getInstance());
                }
            }.runTaskAsynchronously(Elections.getInstance());
        });

        dialogBuilder.button(miniMessage(config.clearBtn, placeholders), context -> new BlockBallotMenu(context.player(), getParentMenu(), electionsService, electionId).open());

        dialogBuilder.buttonWithPlayer(miniMessage(config.backBtn, placeholders), null, (playerActor, response) -> {
            BallotSessions.Session session = BallotSessions.get(playerActor.getUniqueId(), electionId, election.getSystem());
            session.setSystem(election.getSystem());
            for (Map.Entry<String, Integer> entry : keyToId.entrySet()) {
                String key = entry.getKey();
                Integer candidateId = entry.getValue();
                Boolean selected = response.getBoolean(key);
                if (selected != null) session.setSelected(candidateId, selected);
            }
            getParentMenu().open();
        });

        return dialogBuilder.build();
    }

    private String keyFor(int candidateId) { return "CAND_" + candidateId; }
}
