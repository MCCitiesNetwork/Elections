package net.democracycraft.elections.internal.util.time;

import java.util.concurrent.TimeUnit;

/**
 * Utility class for parsing and formatting time units.
 * Supports parsing from simple strings like "d", "m", "h", "s" to full names like "day", "minute", "hour", "second".
 */
public class TimeUnitUtil {

    /**
     * Parses a time unit string into a TimeUnit.
     * Defaults to MINUTES if the string is invalid or null.
     *
     * @param unitStr The string representation of the time unit.
     * @return The corresponding TimeUnit.
     */
    public static TimeUnit parseTimeUnit(String unitStr) {
        if (unitStr == null || unitStr.isBlank()) {
            return TimeUnit.MINUTES;
        }
        String normalized = unitStr.trim().toLowerCase();
        if (normalized.startsWith("s")) return TimeUnit.SECONDS;
        if (normalized.equals("m") || normalized.startsWith("min")) return TimeUnit.MINUTES; // "m", "min", "minute" -> MINUTES
        if (normalized.startsWith("h")) return TimeUnit.HOURS;
        if (normalized.startsWith("d")) return TimeUnit.DAYS;

        return TimeUnit.MINUTES;
    }

    /**
     * Gets a user-friendly name for a TimeUnit.
     *
     * @param unit The TimeUnit.
     * @return A string like "seconds", "minutes", "hours", "days".
     */
    public static String getUnitName(TimeUnit unit) {
        return switch (unit) {
            case SECONDS -> "seconds";
            case MINUTES -> "minutes";
            case HOURS -> "hours";
            case DAYS -> "days";
            default -> "undefined";
        };
    }

    /**
     * Converts a value from one unit to another.
     *
     * @param value The value to convert.
     * @param from The source unit.
     * @param to The target unit.
     * @return The converted value.
     */
    public static long convert(long value, TimeUnit from, TimeUnit to) {
        return to.convert(value, from);
    }
}

