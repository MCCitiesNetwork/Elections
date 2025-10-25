package net.democracycraft.elections.util.listener;

import net.democracycraft.elections.api.model.Candidate;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.util.HeadUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * On player join, refresh candidate head bytes in the DB for any candidates with matching name.
 * This avoids slow lookups and ensures future UIs deserialize a cached head.
 */
public class PlayerJoinHeadCacheListener implements Listener {

    private final ElectionsService electionsService;

    public PlayerJoinHeadCacheListener(ElectionsService svc) {
        this.electionsService = svc;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var p = event.getPlayer();
        String name = p.getName();
        for (Election e : electionsService.listElectionsSnapshot()) {
            for (Candidate c : e.getCandidates()) {
                if (c.getName() != null && c.getName().equalsIgnoreCase(name)) {
                    HeadUtil.updateHeadItemBytesAsync(electionsService, e.getId(), c.getId(), name);
                }
            }
        }
    }
}

