package net.democracycraft.elections.internal.util.head;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerHeadCache {
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameToUUIDCache = new ConcurrentHashMap<>();
    private final Set<UUID> failingProfiles = ConcurrentHashMap.newKeySet();

    public CompletableFuture<ItemStack> getPlayerHead(UUID playerUUID) {
        if (playerUUID == null) return CompletableFuture.completedFuture(new ItemStack(Material.AIR));

        // use cached profile if available
        PlayerProfile cached = cache.get(playerUUID);
        if (cached != null && hasTextures(cached)) {
            return CompletableFuture.completedFuture(createHeadFromProfile(cached));
        }

        if (failingProfiles.contains(playerUUID)) {
            return CompletableFuture.completedFuture(new ItemStack(Material.AIR));
        }

        // create profile
        PlayerProfile profile = Bukkit.createProfile(playerUUID);

        return profile.update().thenApply(updatedProfile -> {
            // save to local cache (not persistent)
            cache.put(playerUUID, updatedProfile);
            failingProfiles.remove(playerUUID);
            return createHeadFromProfile(updatedProfile);
        }).exceptionally(ex -> {
            failingProfiles.add(playerUUID);
            return new ItemStack(Material.AIR);
        });
    }

    public CompletableFuture<ItemStack> getPlayerHead(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return CompletableFuture.completedFuture(new ItemStack(Material.AIR));
        }

        UUID cachedUUID = nameToUUIDCache.get(playerName);
        if (cachedUUID != null) {
            return getPlayerHead(cachedUUID);
        }

        // resolve profile by name
        PlayerProfile profile = Bukkit.createProfile(playerName);

        // call update to fetch UUID and properties
        return profile.update().thenApply(updatedProfile -> {
            // save to local cache (not persistent)
            cache.put(updatedProfile.getId(), updatedProfile);
            nameToUUIDCache.put(playerName, updatedProfile.getId());
            return createHeadFromProfile(updatedProfile);
        }).exceptionally(ex -> {
            return new ItemStack(Material.AIR);
        });
    }

    private boolean hasTextures(PlayerProfile profile) {
        for (ProfileProperty property : profile.getProperties()) {
            if (property.getName().equalsIgnoreCase("textures")) {
                return true;
            }
        }
        return false;
    }

    private ItemStack createHeadFromProfile(PlayerProfile profile) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);
        }
        return head;
    }
}