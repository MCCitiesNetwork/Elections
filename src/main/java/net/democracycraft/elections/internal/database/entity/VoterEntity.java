package net.democracycraft.elections.internal.database.entity;

/**
 * Voter row within a specific election.
 *
 * Constraints (see DatabaseSchema):
 * - UNIQUE (electionId, name)
 * - FK electionId -> elections(id)
 */
public class VoterEntity {
    /** Primary key. */
    public int id;
    /** Owning election identifier (FK to elections.id). */
    public int electionId;
    /** Voter's display name (unique within the election). */
    public String name;
    public VoterEntity() {}
}
