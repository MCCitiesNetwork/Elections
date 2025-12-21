package net.democracycraft.elections.src.util.listener;

import net.democracycraft.elections.api.model.Candidate;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.src.util.HeadUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * On player join, refresh candidate head bytes in the DB for any candidates with matching name.
 * This avoids slow lookups and ensures future UIs deserialize a cached head.
 */
public record PlayerJoinHeadCacheListener(ElectionsService electionsService) implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        String name = player.getName();
        for (Election election : electionsService.listElectionsSnapshot()) {
            for (Candidate candidate : election.getCandidates()) {
                if (candidate.getName() != null && candidate.getName().equalsIgnoreCase(name)) {
                    HeadUtil.updateHeadItemBytesAsync(electionsService, election.getId(), candidate.getId(), name);
                }
            }
        }
    }
}

