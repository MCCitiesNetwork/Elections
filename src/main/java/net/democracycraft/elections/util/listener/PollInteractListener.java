package net.democracycraft.elections.util.listener;

import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.model.Poll;
import net.democracycraft.elections.api.model.Voter;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.data.ElectionStatus;
import net.democracycraft.elections.data.RequirementsDto;
import net.democracycraft.elections.ui.vote.BallotIntroMenu;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Optional;

/**
 * Bukkit listener that opens the ballot UI when a player right-clicks a block
 * registered as a polling booth (poll) for an open election.
 */
public class PollInteractListener implements Listener {

    private final ElectionsService electionsService;

    public PollInteractListener(ElectionsService svc) {
        this.electionsService = svc;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();

        Optional<Election> match = findElectionByBlock(block);
        if (match.isEmpty()) return;

        Election election = match.get();
        if (election.getStatus() != ElectionStatus.OPEN) {
            player.sendMessage("This election is not open.");
            return;
        }

        // Minimal eligibility: require elections.user and any configured permission nodes
        if (!player.hasPermission("elections.user")) {
            player.sendMessage("You don't have permission to vote.");
            return;
        }
        RequirementsDto req = election.getRequirements();
        if (req != null && req.getPermissions() != null) {
            for (String node : req.getPermissions()) {
                if (node == null || node.isBlank()) continue;
                if (!player.hasPermission(node)) {
                    player.sendMessage("You are not eligible to vote in this election.");
                    return;
                }
            }
        }

        // Register voter off the main thread to avoid DB blocking.
        new BukkitRunnable() {
            @Override
            public void run() {
                Voter voter = electionsService.registerVoterAsync(election.getId(), player.getName()).join();
                // Back on main thread: fetch a fresh election snapshot and guard against duplicate submissions.
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Election latest = electionsService.getElectionSnapshot(election.getId()).orElse(election);
                        boolean alreadySubmitted = latest.getBallots().stream().anyMatch(b -> b.getVoterId() == voter.getId() && b.isSubmitted());
                        if (alreadySubmitted) {
                            player.sendMessage("You have already submitted a ballot for this election.");
                            return;
                        }
                        net.democracycraft.elections.vote.VoteSessionManager.open(player.getUniqueId(), election.getId(), voter.getId(), latest.getSystem());
                        new BallotIntroMenu(player, electionsService, election.getId()).open();
                    }
                }.runTask(Elections.getInstance());
            }
        }.runTaskAsynchronously(Elections.getInstance());
    }

    private Optional<Election> findElectionByBlock(Block block) {
        Location loc = block.getLocation();
        World w = loc.getWorld();
        if (w == null) return Optional.empty();
        String worldName = w.getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        for (Election e : electionsService.listElectionsSnapshot()) {
            for (Poll p : e.getPolls()) {
                if (p.getWorld().equalsIgnoreCase(worldName) && p.getX() == x && p.getY() == y && p.getZ() == z) {
                    return Optional.of(e);
                }
            }
        }
        return Optional.empty();
    }
}
