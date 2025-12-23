package net.democracycraft.elections.internal.ui.manager;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.model.Candidate;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.internal.ui.ChildMenuImp;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.api.ui.AutoDialog;
import net.democracycraft.elections.internal.ui.common.ConfirmationMenu;
import net.democracycraft.elections.internal.ui.common.LoadingMenu;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.Serializable;
import java.util.Map;

/**
 * Child dialog to list and remove election candidates.
 */
public class CandidateListMenu extends ChildMenuImp {

    private final ElectionsService electionsService;
    private final int electionId;

    public CandidateListMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId) {
        super(player, parent, "candidate_list_" + electionId);
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }

    public static class Config implements Serializable {
        public String title = "<gold><bold>Candidate List</bold></gold>";
        public String none = "<gray>No candidates found.</gray>";
        public String removePrefix = "<red>Remove</red> ";
        public String confirmRemoveMsg = "<red>Are you sure you want to remove candidate <bold>%candidate_name%</bold>?</red>";
        public String removedMsg = "<yellow>Candidate removed.</yellow>";
        public String removeFailedMsg = "<red><bold>Could not remove candidate.</bold></red>";
        public String backBtn = "<gray>Back</gray>";

        public String yamlHeader = "CandidateListMenu configuration. Placeholders: %candidate_name%, %candidate_id%.";
        public String loadingTitle = "<gold><bold>Removing</bold></gold>";
        public String loadingMessage = "<gray><italic>Removing candidate...</italic></gray>";
        public boolean canCloseWithEscape = true;

        public Config() {}

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(Config.class, "CandidateListMenu.yml", new Config().yamlHeader);
            yml.loadOrCreate(Config::new);
        }
    }

    private Dialog build() {
        Election election = electionsService.getElection(electionId).orElse(null);
        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        dialogBuilder.title(miniMessage(config.title, null));
        dialogBuilder.canCloseWithEscape(config.canCloseWithEscape);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        if (election == null || election.getCandidates().isEmpty()) {
            dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(config.none, null)));
        } else {
            for (Candidate candidate : election.getCandidates()) {
                Component label = miniMessage("<gray>#" + candidate.getId() + "</gray> ")
                        .append(miniMessage("<white><bold>" + candidate.getName() + "</bold></white>", null));

                dialogBuilder.button(miniMessage(config.removePrefix, null).append(label), context -> {
                    new ConfirmationMenu(
                        context.player(),
                        this,
                        applyPlaceholders(config.confirmRemoveMsg, Map.of("%candidate_name%", candidate.getName())),
                        player -> {
                            new LoadingMenu(player, getParentMenu(), miniMessage(config.loadingTitle, null), miniMessage(config.loadingMessage, null)).open();
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    boolean removed = electionsService.removeCandidate(electionId, candidate.getId(), player.getName());
                                    new BukkitRunnable() {
                                        @Override
                                        public void run() {
                                            player.sendMessage(removed ? miniMessage(config.removedMsg, null) : miniMessage(config.removeFailedMsg, null));
                                            new CandidateListMenu(player, getParentMenu(), electionsService, electionId).open();
                                        }
                                    }.runTask(Elections.getInstance());
                                }
                            }.runTaskAsynchronously(Elections.getInstance());
                        }
                    ).open();
                });
            }
        }

        dialogBuilder.button(miniMessage(config.backBtn, null), context ->
            new CandidatesMenu(context.player(), getParentMenu(), electionsService, electionId).open()
        );
        return dialogBuilder.build();
    }
}

