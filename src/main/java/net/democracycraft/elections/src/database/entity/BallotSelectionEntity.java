package net.democracycraft.elections.src.database.entity;

/**
 * Database row representing a single selection within a ballot.
 *
 * Constraints (see DatabaseSchema):
 * - UNIQUE (ballotId, candidateId)
 * - UNIQUE (ballotId, position)
 * - FK ballotId -> ballots(id) ON DELETE CASCADE
 * - FK candidateId -> candidates(id)
 */
public class BallotSelectionEntity {
    /** Owning ballot identifier (FK to ballots.id). */
    public int ballotId;
    /** Selected candidate identifier (FK to candidates.id). */
    public int candidateId;
    /** One-based rank or insertion order within the ballot. */
    public int position;

    public BallotSelectionEntity() {}
}

