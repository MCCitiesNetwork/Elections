package net.democracycraft.democracyelections.util.listener;

import net.democracycraft.democracyelections.DemocracyElections;
import net.democracycraft.democracyelections.api.model.Election;
import net.democracycraft.democracyelections.api.model.Poll;
import net.democracycraft.democracyelections.api.model.Voter;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.data.ElectionStatus;
import net.democracycraft.democracyelections.data.RequirementsDto;
import net.democracycraft.democracyelections.ui.vote.BallotIntroMenu;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Optional;

/**
 * Bukkit listener that opens the ballot UI when a player right-clicks a block
 * registered as a polling booth (poll) for an open election.
 */
public class PollInteractListener implements Listener {

    private final ElectionsService electionsService;

    public PollInteractListener(DemocracyElections plugin, ElectionsService svc) {
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
        if (!player.hasPermission("elections.user") && !player.hasPermission("democracyelections.user")) {
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

        // Register (or get) voter id
        Voter voter = electionsService.registerVoter(election.getId(), player.getName());
        // Prevent duplicate submissions from opening the UI
        boolean alreadySubmitted = election.getBallots().stream().anyMatch(v -> v.getVoterId() == voter.getId() && v.isSubmitted());
        if (alreadySubmitted) {
            player.sendMessage("You have already submitted a ballot for this election.");
            return;
        }

        net.democracycraft.democracyelections.vote.VoteSessionManager.open(player.getUniqueId(), election.getId(), voter.getId(), election.getSystem());
        new BallotIntroMenu(player, electionsService, election.getId()).open();
    }

    private Optional<Election> findElectionByBlock(Block block) {
        Location loc = block.getLocation();
        World w = loc.getWorld();
        if (w == null) return Optional.empty();
        String worldName = w.getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        for (Election e : electionsService.listElections()) {
            for (Poll p : e.getPolls()) {
                if (p.getWorld().equalsIgnoreCase(worldName) && p.getX() == x && p.getY() == y && p.getZ() == z) {
                    return Optional.of(e);
                }
            }
        }
        return Optional.empty();
    }
}
