package net.democracycraft.elections.database.entity;

/**
 * RDBMS row for elections. Timestamps stored as epoch millis (UTC).
 *
 * Note: Closing time is computed at runtime from OPENED time + duration. No closesAt column is persisted.
 */
public class ElectionEntity {
    public int id;
    public String title;
    public String status;
    public String system;
    public int minimumVotes;
    public long createdAtEpochMillis;
    public Integer durationDays; // nullable
    public Integer durationHour; // nullable
    public Integer durationMinute; // nullable
    public Integer durationSecond; // nullable
    /** Ballot UI mode: MANUAL or SIMPLE (stored as string). */
    public String ballotMode; // nullable -> defaults to MANUAL

    public ElectionEntity() {}
}
