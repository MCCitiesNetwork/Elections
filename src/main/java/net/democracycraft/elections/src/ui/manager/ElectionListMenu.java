package net.democracycraft.elections.src.ui.manager;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.src.ui.ParentMenuImp;
import net.democracycraft.elections.src.ui.list.ElectionPreviewMenu;
import net.democracycraft.elections.src.ui.manager.create.ElectionCreateWizard;
import net.democracycraft.elections.api.ui.AutoDialog;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Locale;

/**
 * Parent dialog listing all elections and providing navigation to item view and creation wizard.
 * All strings are configurable via data/menus/ElectionListMenu.yml with placeholders.
 */
public class ElectionListMenu extends ParentMenuImp {

    private final ElectionsService electionsService;
    private final int page;
    private final int pageSize = 12;
    private final String query;

    /**
     * @param player player opening the dialog
     * @param electionsService service to retrieve and create elections
     */
    public ElectionListMenu(Player player, ElectionsService electionsService) { this(player, electionsService, 0, null); }

    public ElectionListMenu(Player player, ElectionsService electionsService, int page, String query) {
        super(player, "election_list" + (query==null?"":"_q") + "_" + page);
        this.electionsService = electionsService;
        this.page = Math.max(0, page);
        this.query = (query==null || query.isBlank()) ? null : query;
        this.setDialog(build());
    }

    /** Config DTO for this menu. */
    public static class Config implements Serializable {
        public String title = "<gold><bold>Election Manager</bold></gold>";
        public String empty = "<gray>No elections created yet.</gray>";
        public String listHeader = "<aqua><bold>Elections:</bold></aqua>";
        public String rowFormat = "<gray>#%id%</gray> <white><bold>%title%</bold></white> <gray>[</gray><gray>%status%</gray><gray>]</gray> <gray>Voters:%voters% Cand:%cands%</gray>";
        public String createBtn = "<yellow><bold>Create Election</bold></yellow>";
        public String refreshBtn = "<gray>Refresh</gray>";
        public String searchBtn = "<gray>Search</gray>";
        public String clearSearchBtn = "<gray>Clear Search</gray>";
        public String nextBtn = "<gray>Next ▶</gray>";
        public String prevBtn = "<gray>◀ Prev</gray>";
        public String closeBtn = "<red><bold>Close</bold></red>";
        public String yamlHeader = "ElectionListMenu configuration. Placeholders per row: %id%, %title%, %status%, %voters%, %cands%.";
        public String searchDialogTitle = "<gray>Search elections</gray>";
        public String searchQueryLabel = "<gray>Query</gray>";
        public String searchApplyBtn = "<green>Apply</green>";
        public Config() {}

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(ElectionListMenu.Config.class, "ElectionListMenu.yml", new ElectionListMenu.Config().yamlHeader);
            yml.loadOrCreate(ElectionListMenu.Config::new);
        }
    }

    private Dialog build() {
        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        dialogBuilder.title(miniMessage(config.title, null));
        dialogBuilder.canCloseWithEscape(true);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        List<Election> elections = new ArrayList<>(electionsService.listElections());
        elections.sort(Comparator.comparingInt(Election::getId));
        if (query != null) {
            String q = query.toLowerCase(Locale.ROOT);
            elections.removeIf(e -> e.getTitle()==null || !e.getTitle().toLowerCase(Locale.ROOT).contains(q));
        }

        if (elections.isEmpty()) {
            dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(config.empty, null)));
        } else {
            dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(config.listHeader, null)));
            int from = Math.min(page * pageSize, elections.size());
            int to = Math.min(from + pageSize, elections.size());
            for (int i = from; i < to; i++) {
                Election election = elections.get(i);
                Map<String, String> placeholders = Map.of(
                        "%id%", String.valueOf(election.getId()),
                        "%title%", election.getTitle(),
                        "%status%", election.getStatus().name(),
                        "%voters%", String.valueOf(election.getVoterCount()),
                        "%cands%", String.valueOf(election.getCandidates().size())
                );
                Component label = miniMessage(config.rowFormat, placeholders);
                int selectedElectionId = election.getId();
                dialogBuilder.button(label, context -> new ElectionPreviewMenu(context.player(), this, electionsService, selectedElectionId).open());
            }
            // Prev/Next controls
            if (page > 0) dialogBuilder.button(miniMessage(config.prevBtn, null), c -> new ElectionListMenu(c.player(), electionsService, page - 1, query).open());
            if (to < elections.size()) dialogBuilder.button(miniMessage(config.nextBtn, null), c -> new ElectionListMenu(c.player(), electionsService, page + 1, query).open());
        }

        dialogBuilder.button(miniMessage(config.createBtn, null), context -> new ElectionCreateWizard(context.player(), electionsService).open());
        dialogBuilder.button(miniMessage(config.searchBtn, null), context -> {
            AutoDialog.Builder searchDlg = getAutoDialogBuilder();
            searchDlg.title(miniMessage(config.searchDialogTitle, null));
            var input = io.papermc.paper.registry.data.dialog.input.DialogInput.text("Q", miniMessage(config.searchQueryLabel, null)).labelVisible(true).build();
            searchDlg.addInput(input);
            searchDlg.buttonWithPlayer(miniMessage(config.searchApplyBtn, null), null, (p, response) -> {
                String q = response.getText("Q");
                new ElectionListMenu(p, electionsService, 0, q).open();
            });
            searchDlg.button(miniMessage(config.clearSearchBtn, null), c2 -> new ElectionListMenu(c2.player(), electionsService, 0, null).open());
            searchDlg.button(miniMessage(config.closeBtn, null), c2 -> new ElectionListMenu(c2.player(), electionsService, page, query).open());
            context.player().showDialog(searchDlg.build());
        });
        dialogBuilder.button(miniMessage(config.refreshBtn, null), context -> new ElectionListMenu(context.player(), electionsService, page, query).open());
        dialogBuilder.button(miniMessage(config.closeBtn, null), context -> {});

        return dialogBuilder.build();
    }
}
