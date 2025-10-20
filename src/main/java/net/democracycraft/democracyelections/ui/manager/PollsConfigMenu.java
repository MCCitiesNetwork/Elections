package net.democracycraft.democracyelections.ui.manager;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.democracyelections.DemocracyElections;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.ui.ChildMenuImp;
import net.democracycraft.democracyelections.api.ui.ParentMenu;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.democracycraft.democracyelections.util.listener.DynamicListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Objects;

/**
 * Child dialog to configure polls by clicking blocks (only dynamic listener allowed).
 */
public class PollsConfigMenu extends ChildMenuImp {

    private final DemocracyElections plugin;
    private final ElectionsService electionsService;
    private final int electionId;

    /**
     * @param player player opening the dialog
     * @param parent parent menu
     * @param plugin main plugin
     * @param electionsService elections service
     * @param electionId election identifier
     */
    public PollsConfigMenu(Player player, ParentMenu parent, DemocracyElections plugin, ElectionsService electionsService, int electionId) {
        super(player, parent, "polls_cfg_" + electionId);
        this.plugin = plugin;
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }

    private Dialog build() {
        AutoDialog.Builder b = getAutoDialogBuilder();
        b.title(title("Configure Polls"));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);

        int polls = electionsService.getElection(electionId).map(e -> e.getPolls().size()).orElse(0);
        b.addBody(DialogBody.plainMessage(info("Current polls: " + polls)));
        b.addBody(DialogBody.plainMessage(info("Click a block/head to define or remove a poll.")));

        b.button(good("Define Poll"), ctx -> startBlockSelect(ctx.player(), true));
        b.button(neg("Undefine Poll"), ctx -> startBlockSelect(ctx.player(), false));
        b.button(warn("Back"), ctx -> new ElectionManagerMenu(ctx.player(), electionsService, electionId).open());

        return b.build();
    }

    private void startBlockSelect(Player p, boolean define) {
        var dyn = new DynamicListener();
        Listener listener = new Listener() {
            @EventHandler
            public void onInteract(PlayerInteractEvent event) {
                if (!event.getPlayer().getUniqueId().equals(p.getUniqueId())) return;
                if (event.getClickedBlock() == null) return;
                Location loc = event.getClickedBlock().getLocation();
                var worldName = Objects.requireNonNull(loc.getWorld()).getName();
                int x = loc.getBlockX(); int y = loc.getBlockY(); int z = loc.getBlockZ();
                boolean ok;
                if (define) ok = electionsService.addPoll(electionId, worldName, x, y, z).isPresent();
                else ok = electionsService.removePoll(electionId, worldName, x, y, z);
                p.sendMessage(ok ? good((define?"Defined":"Undefined") + " poll at " + x + "," + y + "," + z) : neg("Could not update poll."));
                dyn.stop(); dyn.deleteListener();
                Bukkit.getScheduler().runTask(plugin, () -> new ElectionManagerMenu(p, electionsService, electionId).open());
                event.setCancelled(true);
            }
        };
        dyn.setListener(listener); dyn.start();
        p.closeInventory();
        p.sendMessage(info("Click a block/head to " + (define?"define":"remove") + " the poll."));
    }
}
