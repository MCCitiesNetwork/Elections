package net.democracycraft.elections.src.ui.manager;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.src.data.BallotMode;
import net.democracycraft.elections.src.ui.ChildMenuImp;
import net.democracycraft.elections.api.ui.AutoDialog;
import net.democracycraft.elections.src.ui.manager.create.ElectionCreateWizard;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

/**
 * Child dialog to configure the per-election Ballot UI mode (MANUAL or SIMPLE).
 * All texts are configurable via data/menus/BallotModeMenu.yml with placeholders.
 */
public class BallotModeMenu extends ChildMenuImp {

    private final ElectionsService electionsService;
    private final int electionId;

    /**
     * @param player player opening the dialog
     * @param parent parent menu to return to
     * @param electionsService elections service facade
     * @param electionId the election identifier whose mode will be edited
     */
    public BallotModeMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId) {
        super(player, parent, "ballot_mode_" + electionId);
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }

    /** YAML-backed configuration DTO for this menu. */
    public static class Config implements Serializable {
        public String title = "<gold><bold>Ballot Mode</bold></gold>";
        public String currentLabel = "<aqua>Current: </aqua>";
        public String manualBtn = "<yellow><bold>Manual</bold></yellow>";
        public String simpleBtn = "<yellow><bold>Simple</bold></yellow>";
        public String updatedMsg = "<green><bold>Ballot mode updated.</bold></green>";
        public String failedMsg = "<red><bold>Could not update ballot mode.</bold></red>";
        public String backBtn = "<gray>Back</gray>";
        public String yamlHeader = "BallotModeMenu configuration. Placeholders: %election_id%, %mode%.";
        public Config() {}

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(BallotModeMenu.Config.class, "BallotModeMenu.yml", new BallotModeMenu.Config().yamlHeader);
            yml.loadOrCreate(BallotModeMenu.Config::new);
        }
    }

    private Dialog build() {
        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        Optional<Election> optional = electionsService.getElection(electionId);
        if (optional.isEmpty()) {
            dialogBuilder.title(miniMessage(config.title));
            dialogBuilder.addBody(DialogBody.plainMessage(miniMessage("<red><bold>Election not found.</bold></red>")));
            return dialogBuilder.build();
        }
        Election election = optional.get();
        Map<String,String> ph = Map.of("%election_id%", String.valueOf(electionId), "%mode%", election.getBallotMode().name());

        dialogBuilder.title(miniMessage(config.title, ph));
        dialogBuilder.canCloseWithEscape(true);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        Component body = Component.newline()
                .append(miniMessage(config.currentLabel, ph))
                .append(miniMessage("<gray>" + election.getBallotMode().name() + "</gray>", null));
        dialogBuilder.addBody(DialogBody.plainMessage(body));

        dialogBuilder.button(miniMessage(config.manualBtn, ph), ctx -> updateMode(ctx.player(), BallotMode.MANUAL, config));
        dialogBuilder.button(miniMessage(config.simpleBtn, ph), ctx -> updateMode(ctx.player(), BallotMode.SIMPLE, config));
        dialogBuilder.button(miniMessage(config.backBtn, ph), ctx -> new ElectionManagerMenu(ctx.player(), electionsService, electionId).open());

        return dialogBuilder.build();
    }

    /** Updates the per-election ballot mode asynchronously and returns to the manager. */
    private void updateMode(Player actor, BallotMode mode, Config config) {
        new BukkitRunnable() {
            @Override
            public void run() {
                boolean ok = electionsService.setBallotMode(electionId, mode, actor.getName());
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        actor.sendMessage(miniMessage(ok ? config.updatedMsg : config.failedMsg));
                        new ElectionManagerMenu(actor, electionsService, electionId).open();
                    }
                }.runTask(Elections.getInstance());
            }
        }.runTaskAsynchronously(Elections.getInstance());
    }
}
