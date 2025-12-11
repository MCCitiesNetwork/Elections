package net.democracycraft.elections.src.ui.manager;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.src.ui.ChildMenuImp;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.api.ui.AutoDialog;
import net.democracycraft.elections.src.util.listener.DynamicListener;
import net.democracycraft.elections.src.ui.common.LoadingMenu;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Child dialog to configure polls by clicking blocks (only dynamic listener allowed).
 * All texts are configurable via data/menus/PollsConfigMenu.yml with placeholders.
 */
public class PollsConfigMenu extends ChildMenuImp {

    private final Elections plugin;
    private final ElectionsService electionsService;
    private final int electionId;

    /**
     * @param player player opening the dialog
     * @param parent parent menu
     * @param plugin main plugin
     * @param electionsService elections service
     * @param electionId election identifier
     */
    public PollsConfigMenu(Player player, ParentMenu parent, Elections plugin, ElectionsService electionsService, int electionId) {
        super(player, parent, "polls_cfg_" + electionId);
        this.plugin = plugin;
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }

    /**
     * Menu configuration DTO for this dialog.
     */
    public static class Config implements Serializable {
        public String title = "<gold><bold>Configure Polls</bold></gold>";
        public String currentPolls = "<aqua>Current polls:</aqua> <gray>%polls_count%</gray>";
        public String clickHint = "<gray>Click a block/head to define or remove a poll.</gray>";
        public String defineBtn = "<green><bold>Define Poll</bold></green>";
        public String undefineBtn = "<red><bold>Undefine Poll</bold></red>";
        public String backBtn = "<gray>Back</gray>";

        public String definedMsg = "<green><bold>Defined poll at</bold></green> <white>%x%</white>,<white>%y%</white>,<white>%z%</white>";
        public String undefinedMsg = "<yellow>Undefined poll at</yellow> <white>%x%</white>,<white>%y%</white>,<white>%z%</white>";
        public String updateFailedMsg = "<red><bold>Could not update poll.</bold></red>";
        public String conflictMsg = "<red><bold>There is already a poll here</bold></red> <gray>(%x%,%y%,%z%)</gray> <red>used by election</red> <white>#%conflict_election_id% %conflict_election_title%</white>";

        public String clickActionMsg = "<gray>Click a block/head to</gray> <white><bold>%action%</bold></white> <gray>the poll.</gray>";
        public String actionDefine = "define";
        public String actionRemove = "remove";

        public String confirmUndefineTitle = "<red><bold>Confirm undefine?</bold></red>";
        public String confirmBtn = "<red>Confirm</red>";
        public String cancelBtn = "<gray>Cancel</gray>";

        public String yamlHeader = "PollsConfigMenu configuration. Placeholders: %player%, %polls_count%, %x%, %y%, %z%, %action%, %conflict_election_id%, %conflict_election_title%.";
        /** Loading dialog title and message while defining/removing. */
        public String loadingTitle = "<gold><bold>Updating</bold></gold>";
        public String loadingMessage = "<gray><italic>Updating polls…</italic></gray>";
        public Config() {}

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(PollsConfigMenu.Config.class, "PollsConfigMenu.yml", new PollsConfigMenu.Config().yamlHeader);
            yml.loadOrCreate(PollsConfigMenu.Config::new);
        }
    }

    private Dialog build() {
        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        dialogBuilder.title(miniMessage(config.title, null));
        dialogBuilder.canCloseWithEscape(true);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        int polls = electionsService.getElection(electionId).map(e -> e.getPolls().size()).orElse(0);
        dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(applyPlaceholders(config.currentPolls, Map.of("%polls_count%", String.valueOf(polls))), null)));
        dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(config.clickHint, null)));

        dialogBuilder.button(miniMessage(config.defineBtn, null), context -> startBlockSelect(context.player(), true, config));
        dialogBuilder.button(miniMessage(config.undefineBtn, null), context -> {
            AutoDialog.Builder confirm = getAutoDialogBuilder();
            confirm.title(miniMessage(config.confirmUndefineTitle, null));
            confirm.button(miniMessage(config.confirmBtn, null), c2 -> startBlockSelect(c2.player(), false, config));
            confirm.button(miniMessage(config.cancelBtn, null), c2 -> new ElectionManagerMenu(c2.player(), electionsService, electionId).open());
            context.player().showDialog(confirm.build());
        });
        dialogBuilder.button(miniMessage(config.backBtn, null), context -> new ElectionManagerMenu(context.player(), electionsService, electionId).open());

        return dialogBuilder.build();
    }

    private void startBlockSelect(Player player, boolean define, Config config) {
        var dynamicListener = new DynamicListener();
        Listener listener = new Listener() {
            @EventHandler
            public void onInteract(PlayerInteractEvent event) {
                if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
                if (event.getClickedBlock() == null) return;
                Location loc = event.getClickedBlock().getLocation();
                var worldName = Objects.requireNonNull(loc.getWorld()).getName();
                int x = loc.getBlockX(); int y = loc.getBlockY(); int z = loc.getBlockZ();
                // Cancel and stop dynamic listener immediately on main thread
                event.setCancelled(true);
                dynamicListener.stop(); dynamicListener.deleteListener();
                // Execute DB update asynchronously
                new LoadingMenu(player, getParentMenu(), miniMessage(config.loadingTitle, null), miniMessage(config.loadingMessage, null)).open();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        boolean success = false;
                        boolean conflict = false;
                        String conflictTitle = null;
                        int conflictId = -1;
                        if (define) {
                            // Pre-check conflict across all elections (snapshot, safe on main/async)
                            for (var e : electionsService.listElectionsSnapshot()) {
                                for (var p : e.getPolls()) {
                                    if (p.getWorld().equalsIgnoreCase(worldName) && p.getX()==x && p.getY()==y && p.getZ()==z) {
                                        if (e.getId() != electionId) {
                                            conflict = true;
                                            conflictId = e.getId();
                                            conflictTitle = e.getTitle();
                                            break;
                                        }
                                    }
                                }
                                if (conflict) break;
                            }
                            if (!conflict) {
                                success = electionsService.addPoll(electionId, worldName, x, y, z, player.getName()).isPresent();
                            }
                        } else {
                            success = electionsService.removePoll(electionId, worldName, x, y, z, player.getName());
                        }
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("%x%", String.valueOf(x)); placeholders.put("%y%", String.valueOf(y)); placeholders.put("%z%", String.valueOf(z));
                        if (conflict) {
                            placeholders.put("%conflict_election_id%", String.valueOf(conflictId));
                            placeholders.put("%conflict_election_title%", conflictTitle == null ? "" : conflictTitle);
                        }
                        String successMsg = define ? config.definedMsg : config.undefinedMsg;

                        // Capture effectively final copies for inner task
                        final boolean conflictF = conflict;
                        final boolean successF = success;
                        final Map<String, String> placeholdersF = Map.copyOf(placeholders);
                        final String successMsgF = successMsg;

                        // Back to main thread to message and reopen UI
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (conflictF) {
                                    player.sendMessage(miniMessage(applyPlaceholders(config.conflictMsg, placeholdersF), null));
                                } else {
                                    player.sendMessage(successF ? miniMessage(applyPlaceholders(successMsgF, placeholdersF), null) : miniMessage(config.updateFailedMsg, placeholdersF));
                                }
                                new ElectionManagerMenu(player, electionsService, electionId).open();
                            }
                        }.runTask(plugin);
                    }
                }.runTaskAsynchronously(plugin);
            }
        };
        dynamicListener.setListener(listener); dynamicListener.start();
        player.closeInventory();
        String action = define ? config.actionDefine : config.actionRemove;
        player.sendMessage(miniMessage(applyPlaceholders(config.clickActionMsg, Map.of("%action%", action)), null));
    }
}
