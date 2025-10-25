package net.democracycraft.elections.database.entity;

/**
 * Candidate row within an election.
 *
 * Constraints (see DatabaseSchema):
 * - UNIQUE (electionId, name)
 * - FK electionId -> elections(id)
 */
public class CandidateEntity {
    /** Primary key. */
    public int id;
    /** Owning election identifier (FK to elections.id). */
    public int electionId;
    /** Candidate display name. */
    public String name;
    public CandidateEntity() {}
}
