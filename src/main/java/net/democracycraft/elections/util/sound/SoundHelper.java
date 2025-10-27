package net.democracycraft.elections.util.sound;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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
        // Try to match enum constant without using deprecated valueOf; fallback to string key
        Sound matched = null;
        String name = spec.sound.trim();
        for (Sound s : Sound.values()) {
            if (s.name().equalsIgnoreCase(name)) { matched = s; break; }
        }
        if (matched != null) {
            player.playSound(loc, matched, category, volume, pitch);
        } else {
            player.playSound(loc, name, volume, pitch);
        }
    }
}
