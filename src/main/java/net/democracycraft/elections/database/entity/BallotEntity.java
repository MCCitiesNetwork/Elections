package net.democracycraft.elections.database.entity;

/**
 * Database row representing a single cast ballot in an election.
 *
 * Constraints (see DatabaseSchema):
 * - UNIQUE (electionId, voterId) to enforce one ballot per voter per election.
 * - FK electionId -> elections(id)
 * - FK voterId -> voters(id)
 */
public class BallotEntity {
    /** Primary key. */
    public int id;
    /** Owning election identifier (FK to elections.id). */
    public int electionId;
    /** Voter identifier (FK to voters.id). */
    public int voterId;
    /** Epoch millis when the ballot was submitted; nullable until finalized. */
    public Long submittedAtEpochMillis; // nullable

    public BallotEntity() {}
}
