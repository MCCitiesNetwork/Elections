package net.democracycraft.elections.ui.vote;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.elections.api.model.Candidate;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.data.VotingSystem;
import net.democracycraft.elections.ui.ChildMenuImp;
import net.democracycraft.elections.util.HeadUtil;
import net.democracycraft.elections.ui.dialog.AutoDialog;
import net.democracycraft.elections.util.sound.SoundHelper;
import net.democracycraft.elections.util.sound.SoundSpec;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Per-candidate vote menu with texts configurable via per-menu YAML and placeholders.
 * BLOCK: toggle selected. PREFERENTIAL: choose rank (1..N).
 */
public class CandidateVoteMenu extends ChildMenuImp {

    private final ElectionsService electionsService;
    private final int electionId;
    private final int candidateId;

    public CandidateVoteMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId, int candidateId) {
        super(player, parent, "ballot_cand_" + electionId + "_" + candidateId);
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.candidateId = candidateId;
        this.setDialog(build());
    }

    /** Config DTO for this menu. */
    public static class Config implements Serializable {
        public String titleFallback = "<gold><bold>Vote</bold></gold>";
        public String electionNotFound = "<red><bold>Election not found.</bold></red>";
        public String candidateNotFound = "<red><bold>Candidate not found.</bold></red>";
        public String candidateTitleFormat = "<white><bold>%candidate_name%</bold></white>";
        public String selectLabel = "<aqua>Select</aqua>";
        public String saveBtn = "<gray>Save</gray>";
        public String rankLabelFormat = "<aqua>Rank (1..%max%)</aqua>";
        public String invalidRank = "<red><bold>Invalid rank.</bold></red>";
        public String clearRankBtn = "<yellow>Clear rank</yellow>";
        public String backBtn = "<red><bold>Back</bold></red>";
        /** Message shown when a block selection is saved. Placeholders: %candidate_name%. */
        public String savedSelectionMsg = "<green><bold>Vote counted for %candidate_name%.</bold></green>";
        /** Message shown when a rank is saved. Placeholders: %candidate_name%, %rank%. */
        public String savedRankMsg = "<green><bold>Rank %rank% saved for %candidate_name%.</bold></green>";
        /** Sound to play on successful save (selection or rank). */
        public SoundSpec successSound = new SoundSpec();
        public String yamlHeader = "CandidateVoteMenu configuration. Placeholders: %candidate_name%, %max%.";
        public Config() {}
    }

    private Dialog build() {
        Optional<Election> optionalElection = electionsService.getElectionSnapshot(electionId);
        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        if (optionalElection.isEmpty()) {
            dialogBuilder.title(miniMessage(config.titleFallback));
            dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(config.electionNotFound)));
            return dialogBuilder.build();
        }
        Election election = optionalElection.get();
        VotingSystem system = election.getSystem();
        BallotSessions.Session session = BallotSessions.get(getPlayer().getUniqueId(), electionId, system);
        session.setSystem(system);

        Candidate candidate = null;
        List<Candidate> candidates = election.getCandidates();
        for (Candidate candidateItem : candidates) if (candidateItem.getId() == candidateId) { candidate = candidateItem; break; }
        if (candidate == null) {
            dialogBuilder.title(miniMessage(config.titleFallback));
            dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(config.candidateNotFound)));
            return dialogBuilder.build();
        }

        dialogBuilder.title(miniMessage(applyPlaceholders(config.candidateTitleFormat, Map.of("%candidate_name%", candidate.getName())), null));
        dialogBuilder.canCloseWithEscape(true);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        HeadUtil.updateHeadItemBytesAsync(electionsService, electionId, candidate.getId(), candidate.getName());
        ItemStack headItem = HeadUtil.headFromBytesOrName(candidate.getId(), candidate.getName());
        dialogBuilder.addBody(DialogBody.item(headItem).showTooltip(true).build());

        final String candidateName = candidate.getName();
        final SoundSpec successSound = config.successSound;

        if (system == VotingSystem.BLOCK) {
            boolean initial = session.isSelected(candidateId);
            String selectKey = "SEL_" + candidateId;
            dialogBuilder.addInput(DialogInput.bool(selectKey, miniMessage(config.selectLabel)).initial(initial).build());
            dialogBuilder.button(miniMessage(config.saveBtn), context -> {
                Boolean value = context.response().getBoolean(selectKey);
                session.setSelected(candidateId, value != null && value);
                // Feedback: message + sound
                Map<String,String> ph = new HashMap<>(); ph.put("%candidate_name%", candidateName);
                context.player().sendMessage(miniMessage(applyPlaceholders(config.savedSelectionMsg, ph), null));
                SoundHelper.play(context.player(), successSound);
                new CandidateListMenu(context.player(), getParentMenu(), electionsService, electionId).open();
            });
        } else {
            int maxRank = Math.max(1, candidates.size());
            Integer initialRank = session.getRank(candidateId);
            String rankKey = "RANK_" + candidateId;
            int initRank = (initialRank != null && initialRank >= 1 && initialRank <= maxRank) ? initialRank : 1;
            Map<String,String> placeholders = new HashMap<>(); placeholders.put("%max%", String.valueOf(maxRank));
            var range = DialogInput
                    .numberRange(rankKey, miniMessage(applyPlaceholders(config.rankLabelFormat, placeholders), null), 1f, (float) maxRank)
                    .step(1f)
                    .initial((float) initRank);
            dialogBuilder.addInput(range.build());
            dialogBuilder.button(miniMessage(config.saveBtn), context -> {
                Float value = context.response().getFloat(rankKey);
                if (value == null || value < 1 || value > maxRank) {
                    context.player().sendMessage(miniMessage(config.invalidRank));
                    new CandidateVoteMenu(context.player(), getParentMenu(), electionsService, electionId, candidateId).open();
                    return;
                }
                int rank = Math.round(value);
                session.setRank(candidateId, rank);
                // Feedback: message + sound
                Map<String,String> ph = new HashMap<>(); ph.put("%candidate_name%", candidateName); ph.put("%rank%", String.valueOf(rank));
                context.player().sendMessage(miniMessage(applyPlaceholders(config.savedRankMsg, ph), null));
                SoundHelper.play(context.player(), successSound);
                new CandidateListMenu(context.player(), getParentMenu(), electionsService, electionId).open();
            });
            dialogBuilder.button(miniMessage(config.clearRankBtn), context -> { session.clearRank(candidateId); new CandidateListMenu(context.player(), getParentMenu(), electionsService, electionId).open(); });
        }

        dialogBuilder.button(miniMessage(config.backBtn), context -> new CandidateListMenu(context.player(), getParentMenu(), electionsService, electionId).open());
        return dialogBuilder.build();
    }
}
