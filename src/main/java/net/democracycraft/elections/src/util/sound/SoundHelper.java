package net.democracycraft.elections.src.util.sound;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility to safely play configurable sounds to a player.
 * <p>
 * This helper supports both {@link Sound} enum values and raw namespaced keys as plain strings.
 * When a raw string is used it is normalized to lower case before being passed to Bukkit,
 * because Minecraft resource locations must only contain {@code [a-z0-9/._-]} characters.
 * Invalid or blank sound names are ignored silently.
 */
public final class SoundHelper {

    private SoundHelper() {}

    /**
     * Plays a sound described by the provided specification to the given player.
     * <p>
     * Resolution happens in two steps:
     * <ol>
     *     <li>First the {@link Sound} registry is scanned to find a matching enum constant
     *     by key or fully qualified namespaced key.</li>
     *     <li>If no match is found, the provided string is treated as a raw namespaced key,
     *     converted to lower case and passed to {@link Player#playSound(Location, String, float, float)}.</li>
     * </ol>
     * If the specification is {@code null}, disabled, or has a blank sound name, nothing is played.
     *
     * @param player target player, never {@code null}
     * @param spec   sound specification, may be {@code null}
     */
    public static void play(@NotNull Player player, @Nullable SoundSpec spec) {
        if (spec == null || !spec.enabled) return;

        Location loc = player.getLocation();
        float volume = spec.volume;
        float pitch = spec.pitch;
        SoundCategory category = spec.category != null ? spec.category : SoundCategory.MASTER;

        if (spec.sound == null || spec.sound.isBlank()) {
            return;
        }

        // Normalize raw name to avoid invalid resource location characters (e.g. upper case).
        String name = spec.sound.trim();

        // Try to match using Registry iterator against the original name (case-insensitive).
        Sound matched = null;
        for (Sound s : Registry.SOUNDS) {
            NamespacedKey key = Registry.SOUNDS.getKey(s);
            if (key != null && (key.getKey().equalsIgnoreCase(name) || key.toString().equalsIgnoreCase(name))) {
                matched = s;
                break;
            }
        }

        if (matched != null) {
            player.playSound(loc, matched, category, volume, pitch);
            return;
        }

        // Fall back to namespaced key string; normalize to lower case to satisfy ResourceLocation rules.
        String normalized = name.toLowerCase(java.util.Locale.ROOT);
        player.playSound(loc, normalized, volume, pitch);
    }
}
