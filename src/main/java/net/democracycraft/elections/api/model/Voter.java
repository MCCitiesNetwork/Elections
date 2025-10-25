package net.democracycraft.elections.api.model;

/**
 * Read-only view of a registered voter in an election.
 */
public interface Voter {
    /** Unique voter id within an election. */
    int getId();
    /** Player name (non-null). */
    String getName();
}
