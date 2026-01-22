package net.democracycraft.elections.internal.util.head;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.democracycraft.elections.Elections;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerHeadCache {
    private final Elections plugin;

    public PlayerHeadCache(Elections plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<ItemStack> getPlayerHead(@NotNull UUID playerUUID) {

        return plugin.getMojangService().getSkin(playerUUID).thenApply(skin -> {
            if (skin == null) {
                return new ItemStack(Material.AIR);
            }

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);


            head.editMeta(meta -> {
                if (meta instanceof SkullMeta skullMeta) {
                    PlayerProfile ownerProfile = Bukkit.createProfile(playerUUID);
                    ownerProfile.setProperty(new ProfileProperty("textures", skin.value(), skin.signature()));

                    skullMeta.setPlayerProfile(ownerProfile);
                }
            });

            return head;
        }).exceptionally(ignored -> new ItemStack(Material.AIR));
    }

    public CompletableFuture<ItemStack> getPlayerHead(@NotNull String playerName) {
       return plugin.getMojangService().getUUID(playerName).thenCompose(uuid ->{
              if (uuid == null) {
                  return CompletableFuture.completedFuture(new ItemStack(Material.AIR));
              }
           return getPlayerHead(uuid);
       }).exceptionally(ignored -> new ItemStack(Material.AIR));


    }

}