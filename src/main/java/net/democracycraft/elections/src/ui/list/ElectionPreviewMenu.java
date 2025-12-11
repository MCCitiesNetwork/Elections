package net.democracycraft.elections.src.ui.list;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.src.ui.ChildMenuImp;
import net.democracycraft.elections.src.ui.common.LoadingMenu;
import net.democracycraft.elections.src.ui.manager.ElectionManagerMenu;
import net.democracycraft.elections.api.ui.AutoDialog;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.io.Serializable;
import java.util.Map;

/**
 * Child dialog to view an election summary and open its manager.
 * All texts are configurable via data/menus/ElectionListItemMenu.yml with placeholders.
 */
public class ElectionPreviewMenu extends ChildMenuImp {

    private final ElectionsService electionsService;
    private final int electionId;

    /**
     * @param player player opening the dialog
     * @param parent parent menu
     * @param electionsService elections service
     * @param electionId election identifier
     */
    public ElectionPreviewMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId) {
        super(player, parent, "election_list_item_" + electionId);
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }

    /** Config DTO for this menu. */
    public static class Config implements Serializable {
        public String titleFallback = "<gold><bold>Election</bold></gold>";
        public String titleFormat = "<gold><bold>Election #%id%</bold></gold>";
        public String notFound = "<red><bold>Election not found.</bold></red>";
        public String titleLabel = "<aqua>Title: </aqua>";
        public String statusLabel = "<aqua>Status: </aqua>";
        public String votersLabel = "<aqua>Voters: </aqua>";
        public String candidatesLabel = "<aqua>Candidates: </aqua>";
        public String pollsLabel = "<aqua>Polls: </aqua>";
        public String openManagerBtn = "<yellow><bold>Open Manager</bold></yellow>";
        public String backBtn = "<gray>Back</gray>";
        public String yamlHeader = "ElectionListItemMenu configuration. Placeholders: %id%, %title%, %status%, %voters%, %candidates%, %polls%.";
        public Config() {}

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(ElectionPreviewMenu.Config.class, "ElectionPreviewMenu.yml", new ElectionPreviewMenu.Config().yamlHeader);
            yml.loadOrCreate(ElectionPreviewMenu.Config::new);
        }

    }

    private Dialog build() {
        Election election = electionsService.getElection(electionId).orElse(null);
        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();

        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        if (election == null) {
            dialogBuilder.title(miniMessage(config.titleFallback, null));
            dialogBuilder.canCloseWithEscape(true);
            dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);
            dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(config.notFound, null)));
            dialogBuilder.button(miniMessage(config.backBtn, null), context -> {
                Dialog parentDialog = parentMenu.getDialog();
                if (parentDialog != null) {
                    context.player().showDialog(parentDialog);
                }
            });
            return dialogBuilder.build();
        }

        Map<String, String> placeholders = Map.of(
                "%id%", String.valueOf(election.getId()),
                "%title%", election.getTitle(),
                "%status%", election.getStatus().name(),
                "%voters%", String.valueOf(election.getVoterCount()),
                "%candidates%", String.valueOf(election.getCandidates().size()),
                "%polls%", String.valueOf(election.getPolls().size())
        );

        dialogBuilder.title(miniMessage(config.titleFormat, placeholders));
        dialogBuilder.canCloseWithEscape(true);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        dialogBuilder.addBody(DialogBody.plainMessage(Component.newline()
                .append(miniMessage(config.titleLabel, placeholders)).append(miniMessage("<white><bold>" + election.getTitle() + "</bold></white>", null))
                .appendNewline().append(miniMessage(config.statusLabel, placeholders)).append(miniMessage("<gray>" + placeholders.get("%status%") + "</gray>", null))
                .appendNewline().append(miniMessage(config.votersLabel, placeholders)).append(miniMessage("<gray>" + placeholders.get("%voters%") + "</gray>", null))
                .appendNewline().append(miniMessage(config.candidatesLabel, placeholders)).append(miniMessage("<gray>" + placeholders.get("%candidates%") + "</gray>", null))
                .appendNewline().append(miniMessage(config.pollsLabel, placeholders)).append(miniMessage("<gray>" + placeholders.get("%polls%") + "</gray>", null))
        ));

        dialogBuilder.button(miniMessage(config.openManagerBtn, placeholders), context -> new ElectionManagerMenu(context.player(), electionsService, electionId).open());
        Dialog parentDialog = parentMenu.getDialog();
        if (parentDialog != null) {
            dialogBuilder.button(miniMessage(config.backBtn, placeholders), context -> context.player().showDialog(parentDialog));
        }

        return dialogBuilder.build();
    }
}
