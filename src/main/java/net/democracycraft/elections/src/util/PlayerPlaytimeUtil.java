package net.democracycraft.elections.src.util;

import org.bukkit.entity.Player;
import org.bukkit.Statistic;

/**
 * Helper utility to get player's active playtime from Bukkit
 * (Statistic.PLAY_ONE_MINUTE) in different units.
 * Note: PLAY_ONE_MINUTE reports ticks played (1 second = 20 ticks).
 * This statistic persists across disconnects - it is saved when the player
 * disconnects and loaded when they reconnect.
 */
public final class PlayerPlaytimeUtil {
    private PlayerPlaytimeUtil() {}

    /**
     * Returns the player's playtime in ticks.
     * @param player the player to get playtime for
     * @return playtime in ticks (20 ticks = 1 second)
     */
    public static long getPlaytimeTicks(Player player) {
        if (player == null) return 0L;
        try {
            return player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        } catch (Throwable t) {
            return 0L;
        }
    }

    /**
     * Returns the player's playtime in seconds.
     * @param player the player to get playtime for
     * @return playtime in seconds
     */
    public static long getPlaytimeSeconds(Player player) {
        long ticks = getPlaytimeTicks(player);
        return ticks / 20L;
    }

    /**
     * Returns the player's playtime in minutes.
     * @param player the player to get playtime for
     * @return playtime in minutes
     */
    public static long getPlaytimeMinutes(Player player) {
        long seconds = getPlaytimeSeconds(player);
        return seconds / 60L;
    }

    /**
     * Returns the player's playtime in hours.
     * @param player the player to get playtime for
     * @return playtime in hours
     */
    public static long getPlaytimeHours(Player player) {
        long minutes = getPlaytimeMinutes(player);
        return minutes / 60L;
    }

    /**
     * Returns the player's playtime in days.
     * @param player the player to get playtime for
     * @return playtime in days
     */
    public static long getPlaytimeDays(Player player) {
        long hours = getPlaytimeHours(player);
        return hours / 24L;
    }
}