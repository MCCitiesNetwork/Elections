package net.democracycraft.democracyelections.util;

import net.democracycraft.democracyelections.DemocracyElections;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Utility to build and maintain player head items for candidates, including
 * an async offline fallback that updates stored ItemStack bytes safely.
 */
public final class HeadUtil {
    private HeadUtil() {}

    /**
     * Builds a PLAYER_HEAD item with the owning player set to the given name.
     * Quick fallback for immediate UI rendering.
     */
    public static ItemStack headFor(String playerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        if (playerName == null || playerName.isBlank()) return head;
        try {
            OfflinePlayer off = Bukkit.getOfflinePlayer(playerName);
            var meta = head.getItemMeta();
            if (meta instanceof SkullMeta skull) {
                skull.setOwningPlayer(off);
                head.setItemMeta(skull);
            }
        } catch (Throwable ignored) {}
        return head;
    }

    /**
     * Returns the stored head ItemStack bytes if present (deserialized), otherwise
     * returns a fallback head by player name.
     */
    public static ItemStack headFromBytesOrName(ElectionsService electionsService, int electionId, int candidateId, String name) {
        try {
            byte[] bytes = electionsService.getCandidateHeadItemBytes(electionId, candidateId);
            if (bytes != null) return ItemStack.deserializeBytes(bytes);
        } catch (Throwable ignored) {}
        return headFor(name);
    }

    /**
     * Asynchronously refreshes and stores the head ItemStack bytes for a candidate using OfflinePlayer fallback.
     * Heavy lookups run async; ItemStack creation and serialization run on the main thread.
     */
    public static void updateHeadItemBytesAsync(ElectionsService svc, int electionId, int candidateId, String playerName) {
        DemocracyElections plugin = DemocracyElections.getInstance();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    ItemStack head = headFor(playerName);
                    byte[] bytes = head.serializeAsBytes();
                    svc.setCandidateHeadItemBytes(electionId, candidateId, bytes);
                } catch (Throwable ignored) {}
            });
        });
    }
}
