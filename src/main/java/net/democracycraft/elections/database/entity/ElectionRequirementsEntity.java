package net.democracycraft.elections.database.entity;

/**
 * One-to-one requirements row for an election holding minimal requirement fields.
 *
 * Constraints (see DatabaseSchema):
 * - PK and FK electionId -> elections(id)
 */
public class ElectionRequirementsEntity {
    /** Primary key and FK to elections.id. */
    public int electionId;
    /** Minimum active playtime in minutes required to participate; nullable. */
    public Long minActivePlaytimeMinutes; // nullable
    public ElectionRequirementsEntity() {}
}
