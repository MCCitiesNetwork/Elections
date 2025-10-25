package net.democracycraft.elections.api.model;

/**
 * Read-only location of a polling booth where players can vote.
 * All fields are non-null and represent a world coordinate.
 */
public interface Poll {
    /** World name (non-null). */
    String getWorld();
    /** Block X coordinate. */
    int getX();
    /** Block Y coordinate. */
    int getY();
    /** Block Z coordinate. */
    int getZ();
}
