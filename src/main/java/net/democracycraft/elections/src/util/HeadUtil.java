package net.democracycraft.elections.src.util;

import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.service.ElectionsService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Utility to build and maintain player head items for candidates, avoiding blocking lookups.
 */
public final class HeadUtil {
    private HeadUtil() {}

    /**
     * Builds a generic PLAYER_HEAD item (no owner set). Always safe and fast.
     */
    public static ItemStack genericHead() {
        return new ItemStack(Material.PLAYER_HEAD, 1);
    }

    /**
     * Returns the stored head ItemStack bytes if present (deserialized), otherwise
     * returns a generic head without owner. No OfflinePlayer calls.
     */
    public static ItemStack headFromBytesOrName(ElectionsService electionsService, int electionId, int candidateId, String name) {
        try {
            byte[] bytes = electionsService.getCandidateHeadItemBytesSnapshot(electionId, candidateId);
            if (bytes != null) return ItemStack.deserializeBytes(bytes);
        } catch (Throwable ignored) {}
        return genericHead();
    }

    /**
     * Legacy overload kept for compatibility; delegates to generic head.
     */
    public static ItemStack headFromBytesOrName(int candidateId, String name) {
        return genericHead();
    }

    /**
     * Asynchronously refreshes and stores the head ItemStack bytes for a candidate.
     * Only persists if the player is currently online to avoid slow lookups.
     */
    public static void updateHeadItemBytesAsync(ElectionsService svc, int electionId, int candidateId, String playerName) {
        Elections plugin = Elections.getInstance();
        Player online = (playerName == null ? null : Bukkit.getPlayerExact(playerName));
        if (online == null) return; // skip if not online; will remain generic until available
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    ItemStack head = genericHead();
                    var meta = head.getItemMeta();
                    if (meta instanceof SkullMeta skull) {
                        skull.setOwningPlayer(online);
                        head.setItemMeta(skull);
                    }
                    byte[] bytes = head.serializeAsBytes();
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            try { svc.setCandidateHeadItemBytesAsync(electionId, candidateId, bytes).join(); } catch (Throwable ignored) {}
                        }
                    }.runTaskAsynchronously(plugin);
                } catch (Throwable ignored) {}
            }
        }.runTask(plugin);
    }
}
