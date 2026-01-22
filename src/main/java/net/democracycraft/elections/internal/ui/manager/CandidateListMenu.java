package net.democracycraft.elections.internal.ui.manager;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.elections.api.model.Candidate;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.internal.data.Dto;
import net.democracycraft.elections.internal.ui.ChildMenuImp;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.api.ui.AutoDialog;
import net.democracycraft.elections.internal.ui.common.LoadingMenu;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import net.democracycraft.elections.api.ui.ChildMenu;

/**
 * Child dialog to list and remove election candidates.
 */
public class CandidateListMenu extends ChildMenuImp implements ParentMenu {

    private final ElectionsService electionsService;
    private final int electionId;
    private final List<ChildMenu> childMenus = new ArrayList<>();

    public CandidateListMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId) {
        super(player, parent, "candidate_list_" + electionId);
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }

    @Override
    public List<ChildMenu> getChildMenus() { return childMenus; }
    @Override
    public void addChildMenu(ChildMenu childMenu) { childMenus.add(childMenu); }
    @Override
    public void addChildMenus(List<ChildMenu> childMenus) { this.childMenus.addAll(childMenus); }

    public static class Config implements Dto {
        public String title = "<gold><bold>Candidate List</bold></gold>";
        public String none = "<gray>No candidates found.</gray>";
        public String editPrefix = "<yellow>Edit</yellow> ";
        public String backBtn = "<gray>Back</gray>";

        public String yamlHeader = "CandidateListMenu configuration. Placeholders: %candidate_name%, %candidate_id%.";
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

                dialogBuilder.button(miniMessage(config.editPrefix, null).append(label), context -> {
                    new CandidateEditMenu(context.player(), this, electionsService, electionId, candidate.getId()).open();
                });
            }
        }

        dialogBuilder.button(miniMessage(config.backBtn, null), context ->
            new CandidatesAddMenu(context.player(), getParentMenu(), electionsService, electionId).open()
        );
        return dialogBuilder.build();
    }
}

