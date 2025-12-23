package net.democracycraft.elections.internal.database.entity;

/**
 * Audit row describing a change in an election's lifecycle or configuration.
 *
 * Constraints (see DatabaseSchema):
 * - PK id (auto-increment)
 * - FK electionId -> elections(id)
 * - INDEX (electionId, changedAtEpochMillis)
 */
public class StatusChangeEntity {
    /** Primary key. */
    public int id;
    /** Owning election identifier (FK to elections.id). */
    public int electionId;
    /** UTC epoch millis when the change was recorded. */
    public long changedAtEpochMillis;
    /** Change type enum name. */
    public String type;
    /** Actor who performed the change (player name, console, system). */
    public String actor; // nullable allowed by DB default
    /** Optional details string. */
    public String details; // nullable

    public StatusChangeEntity() {}
}

