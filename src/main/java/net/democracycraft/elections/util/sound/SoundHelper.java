package net.democracycraft.elections.util.sound;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility to safely play configurable sounds to a player.
 * Supports Bukkit Sound enum and namespaced keys as plain strings.
 */
public final class SoundHelper {

    private SoundHelper() {}

    /**
     * Plays a sound described by the provided specification to the given player.
     * If the spec is null or disabled, nothing happens.
     *
     * @param player target player
     * @param spec   sound specification (nullable)
     */
    public static void play(@NotNull Player player, @Nullable SoundSpec spec) {
        if (spec == null || !spec.enabled) return;
        Location loc = player.getLocation();
        float volume = spec.volume;
        float pitch = spec.pitch;
        SoundCategory category = spec.category != null ? spec.category : SoundCategory.MASTER;
        if (spec.sound == null || spec.sound.isBlank()) return;

        // Try to match using Registry iterator
        Sound matched = null;
        String name = spec.sound.trim();
        for (Sound s : Registry.SOUNDS) {
            NamespacedKey key = Registry.SOUNDS.getKey(s);
            if (key != null &&
                    (key.getKey().equalsIgnoreCase(name) ||
                            key.toString().equalsIgnoreCase(name))) {
                matched = s;
                break;
            }
        }

        if (matched != null) {
            player.playSound(loc, matched, category, volume, pitch);
        } else {
            player.playSound(loc, name, volume, pitch);
        }
    }
}
