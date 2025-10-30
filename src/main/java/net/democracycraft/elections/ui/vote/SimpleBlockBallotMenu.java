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
 * Simple block ballot UI that shows one toggle button per candidate.
 * Clicking toggles Selected/Not selected; you must end with exactly the minimum required candidates.
 * All texts configurable via data/menus/SimpleBlockBallotMenu.yml.
 */
public class SimpleBlockBallotMenu extends ChildMenuImp {

    private final ElectionsService electionsService;
    private final int electionId;

    /**
     * @param player the player opening the dialog
     * @param parent parent menu
     * @param electionsService elections service
     * @param electionId election identifier
     */
    public SimpleBlockBallotMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId) {
        super(player, parent, "simple_block_" + electionId);
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }

    /** YAML-backed configuration DTO for this menu. */
    public static class Config implements Serializable {
        public String titleFallback = "<gold><bold>Ballot</bold></gold>";
        public String notFound = "<red><bold>Election not found.</bold></red>";
        public String titleFormat = "<gold><bold>%election_title% Ballot (Simple Block)</bold></gold>";
        public String instruction = "<gray>Select exactly <white><bold>%min%</bold></white> candidates.</gray>";
        /** Label shown next to state when candidate is selected. Placeholder: %candidate_name%, %candidate_party%. */
        public String optionSelected = "<gray>[Selected]</gray>";
        /** Label shown next to state when candidate is not selected. Placeholder: %candidate_name%, %candidate_party%. */
        public String optionNotSelected = " ";
        public String selectedLabel = "<aqua>Selected: </aqua>";
        public String submitBtn = "<green><bold>Submit</bold></green>";
        public String clearBtn = "<yellow>Clear all</yellow>";
        public String backBtn = "<red><bold>Back</bold></red>";
        public String mustSelectExactly = "<red><bold>You must select exactly %min% candidates.</bold></red>";
        public String submissionFailed = "<red><bold>Submission failed. Are you eligible or already voted?</bold></red>";
        public String submitted = "<green><bold>Ballot submitted.</bold></green>";
        /** Header describing placeholders supported in this menu. */
        public String yamlHeader = "SimpleBlockBallotMenu configuration. Placeholders: %election_title%, %min%, %candidate_name%, %candidate_party%.";
        /** Loading dialog title shown while submitting. */
        public String loadingTitle = "<gold><bold>Submitting</bold></gold>";
        /** Loading dialog message shown while submitting. */
        public String loadingMessage = "<gray><italic>Submitting your ballot…</italic></gray>";
        /** Sound to play when submission succeeds. */
        public SoundSpec successSound = new SoundSpec();
        public String valueGrayFormat = "<gray>%value%</gray>";
        /** Label format for each candidate row. Placeholders: %candidate_name%, %candidate_party%. */
        public String candidateLabelFormat = "<white>%candidate_name%</white> <gray>(%candidate_party%)</gray>";
        /** Label to use when a candidate has no party set (null/blank). */
        public String partyUnknown = "Independent";
        public Config() {}
    }

    /**
     * Builds the simple block ballot dialog.
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
        Map<String,String> ph = Map.of(
                "%election_title%", election.getTitle(),
                "%min%", String.valueOf(min)
        );

        BallotSessions.Session session = BallotSessions.get(getPlayer().getUniqueId(), electionId, election.getSystem());
        session.setSystem(election.getSystem());

        dialogBuilder.title(miniMessage(config.titleFormat, ph));
        dialogBuilder.canCloseWithEscape(true);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        dialogBuilder.addBody(DialogBody.plainMessage(Component.newline()
                .append(miniMessage(config.instruction, ph)).appendNewline()
                .append(miniMessage(config.selectedLabel, ph)).append(miniMessage(applyPlaceholders(config.valueGrayFormat, Map.of("%value%", String.valueOf(session.selectedCount()))), null))
        ));

        for (Candidate c : election.getCandidates()) {
            String key = "SEL_" + c.getId();
            boolean selected = session.isSelected(c.getId());
            String party = c.getParty();
            if (party == null || party.isBlank()) party = config.partyUnknown;
            List<SingleOptionDialogInput.OptionEntry> entries = new java.util.ArrayList<>();
            entries.add(SingleOptionDialogInput.OptionEntry.create("0", miniMessage(applyPlaceholders(config.optionNotSelected, Map.of("%candidate_name%", c.getName(), "%candidate_party%", party)), null), !selected));
            entries.add(SingleOptionDialogInput.OptionEntry.create("1", miniMessage(applyPlaceholders(config.optionSelected, Map.of("%candidate_name%", c.getName(), "%candidate_party%", party)), null), selected));
            dialogBuilder.addInput(DialogInput
                    .singleOption(key, miniMessage(applyPlaceholders(config.candidateLabelFormat, Map.of("%candidate_name%", c.getName(), "%candidate_party%", party)), null), entries)
                    .build());
        }

        dialogBuilder.buttonWithPlayer(miniMessage(config.submitBtn, ph), null, java.time.Duration.ofMinutes(5), 1, (playerActor, response) -> {
            List<Integer> picked = new ArrayList<>();
            for (Candidate c : election.getCandidates()) {
                String key = "SEL_" + c.getId();
                String sel = response.getText(key);
                int idx;
                try { idx = sel == null ? -1 : Integer.parseInt(sel); } catch (NumberFormatException e) { idx = -1; }
                if (idx == 1) picked.add(c.getId());
            }
            if (picked.size() != min) { playerActor.sendMessage(miniMessage(applyPlaceholders(config.mustSelectExactly, Map.of("%min%", String.valueOf(min))), null)); new SimpleBlockBallotMenu(playerActor, getParentMenu(), electionsService, electionId).open(); return; }
            new LoadingMenu(playerActor, getParentMenu(), miniMessage(config.loadingTitle, ph), miniMessage(config.loadingMessage, ph)).open();
            new BukkitRunnable() {
                @Override
                public void run() {
                    int voterId = electionsService.registerVoterAsync(electionId, playerActor.getName()).join().getId();
                    boolean ok = electionsService.submitBlockBallotAsync(electionId, voterId, picked).join();
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            // Close loading dialog after async completes
                            playerActor.closeDialog();
                            if (!ok) { playerActor.sendMessage(miniMessage(config.submissionFailed)); new SimpleBlockBallotMenu(playerActor, getParentMenu(), electionsService, electionId).open(); }
                            else { playerActor.sendMessage(miniMessage(config.submitted)); SoundHelper.play(playerActor, config.successSound); BallotSessions.clear(playerActor.getUniqueId(), electionId); }
                        }
                    }.runTask(Elections.getInstance());
                }
            }.runTaskAsynchronously(Elections.getInstance());
        });

        dialogBuilder.button(miniMessage(config.clearBtn, ph), ctx -> { session.clearAll(); new SimpleBlockBallotMenu(ctx.player(), getParentMenu(), electionsService, electionId).open(); });
        dialogBuilder.button(miniMessage(config.backBtn, ph), ctx -> getParentMenu().open());

        return dialogBuilder.build();
    }
}
