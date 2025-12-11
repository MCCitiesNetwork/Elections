package net.democracycraft.elections.src.ui.manager;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.model.Candidate;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.src.ui.ChildMenuImp;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.src.util.HeadUtil;
import net.democracycraft.elections.api.ui.AutoDialog;
import net.democracycraft.elections.src.ui.common.LoadingMenu;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

/**
 * Child dialog to manage election candidates (add/remove).
 * All texts are configurable via data/menus/CandidatesMenu.yml with placeholders.
 */
public class CandidatesMenu extends ChildMenuImp {

    enum Keys { CANDIDATE_NAME, CANDIDATE_PARTY }

    private final ElectionsService electionsService;
    private final int electionId;

    /**
     * @param player player opening the dialog
     * @param parent parent menu
     * @param electionsService elections service
     * @param electionId election identifier
     */
    public CandidatesMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId) {
        super(player, parent, "candidates_" + electionId);
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }

    /** Config DTO for this menu. */
    public static class Config implements Serializable {
        public String title = "<gold><bold>Candidates</bold></gold>";
        public String none = "<gray>No candidates yet.</gray>";
        public String currentHeader = "<aqua><bold>Current candidates:</bold></aqua>";
        public String removePrefix = "<red>Remove</red> ";
        public String removedMsg = "<yellow>Candidate removed.</yellow>";
        public String removeFailedMsg = "<red><bold>Could not remove candidate.</bold></red>";
        public String nameLabel = "<aqua>Candidate name</aqua>";
        /**
         * Label for the optional candidate party input. This field may be empty when adding a
         * candidate; the service will accept null/empty party values.
         */
        public String partyLabel = "<aqua>Candidate party (optional)</aqua>";
        public String addBtn = "<green><bold>Add</bold></green>";
        public String nameEmptyMsg = "<red><bold>Name cannot be empty.</bold></red>";
        public String addFailedMsg = "<red><bold>Could not add candidate.</bold></red>";
        public String addedMsg = "<green><bold>Candidate added.</bold></green>";
        public String backBtn = "<gray>Back</gray>";
        public String yamlHeader = "CandidatesMenu configuration. Placeholders: %player%, %election_id%, %candidate_id%, %candidate_name%.";
        /** Loading dialog title and message while adding/removing. */
        public String loadingTitle = "<gold><bold>Updating</bold></gold>";
        public String loadingMessage = "<gray><italic>Applying candidate changes…</italic></gray>";
        public Config() {}

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(CandidatesMenu.Config.class, "CandidatesMenu.yml", new CandidatesMenu.Config().yamlHeader);
            yml.loadOrCreate(CandidatesMenu.Config::new);
        }
    }

    private Dialog build() {
        Election election = electionsService.getElection(electionId).orElse(null);

        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        dialogBuilder.title(miniMessage(config.title, Map.of("%election_id%", String.valueOf(electionId))));
        dialogBuilder.canCloseWithEscape(true);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        if (election == null || election.getCandidates().isEmpty()) {
            dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(config.none, null)));
        } else {
            dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(config.currentHeader, null)));
            for (Candidate candidate : election.getCandidates()) {
                Component label = miniMessage("<gray>#" + candidate.getId() + "</gray> ")
                        .append(miniMessage("<white><bold>" + candidate.getName() + "</bold></white>", null));
                int candidateId = candidate.getId();
                dialogBuilder.button(miniMessage(config.removePrefix, null).append(label), context -> {
                    // Offload removal to async thread
                    new LoadingMenu(context.player(), getParentMenu(), miniMessage(config.loadingTitle, null), miniMessage(config.loadingMessage, null)).open();
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            boolean removed = electionsService.removeCandidate(electionId, candidateId, context.player().getName());
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    context.player().sendMessage(removed ? miniMessage(config.removedMsg, null) : miniMessage(config.removeFailedMsg, null));
                                    new CandidatesMenu(context.player(), getParentMenu(), electionsService, electionId).open();
                                }
                            }.runTask(Elections.getInstance());
                        }
                    }.runTaskAsynchronously(Elections.getInstance());
                });
            }
        }

        // Add name input and optional party input so the candidate party can be set at creation time.
        dialogBuilder.addInput(DialogInput.text(Keys.CANDIDATE_NAME.name(), miniMessage(config.nameLabel, null)).labelVisible(true).build());
        dialogBuilder.addInput(DialogInput.text(Keys.CANDIDATE_PARTY.name(), miniMessage(config.partyLabel, null)).labelVisible(true).build());
        dialogBuilder.buttonWithPlayer(miniMessage(config.addBtn, null), null, (playerActor, response) -> {
            String name = response.getText(Keys.CANDIDATE_NAME.name());
            String party = response.getText(Keys.CANDIDATE_PARTY.name());
            if (name == null || name.isBlank()) {
                playerActor.sendMessage(miniMessage(config.nameEmptyMsg, null));
                new CandidatesMenu(playerActor, getParentMenu(), electionsService, electionId).open();
                return;
            }
            if (party != null && party.isBlank()) party = null;
            final String partyFinal = party;
            // Offload add to async thread
            new LoadingMenu(playerActor, getParentMenu(), miniMessage(config.loadingTitle, null), miniMessage(config.loadingMessage, null)).open();
            new BukkitRunnable() {
                @Override
                public void run() {
                    Optional<Candidate> added = electionsService.addCandidate(electionId, name, partyFinal, playerActor.getName());
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (added.isEmpty()) {
                                playerActor.sendMessage(miniMessage(config.addFailedMsg, null));
                                new CandidatesMenu(playerActor, getParentMenu(), electionsService, electionId).open();
                                return;
                            }
                            Candidate candidate = added.get();
                            HeadUtil.updateHeadItemBytesAsync(electionsService, electionId, candidate.getId(), name);
                            playerActor.sendMessage(miniMessage(config.addedMsg, Map.of("%candidate_id%", String.valueOf(candidate.getId()), "%candidate_name%", name)));
                            new CandidatesMenu(playerActor, getParentMenu(), electionsService, electionId).open();
                        }
                    }.runTask(Elections.getInstance());
                }
            }.runTaskAsynchronously(Elections.getInstance());
        });

        dialogBuilder.button(miniMessage(config.backBtn, null), context -> new ElectionManagerMenu(context.player(), electionsService, electionId).open());
        return dialogBuilder.build();
    }
}
