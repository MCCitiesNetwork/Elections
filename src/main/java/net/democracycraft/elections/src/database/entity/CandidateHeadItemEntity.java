package net.democracycraft.elections.src.database.entity;

/**
 * Optional one-to-one row holding candidate head item serialized bytes.
 * <p>
 * Constraints (see DatabaseSchema):
 * - PK and FK candidateId -> candidates(id)
 * - ON DELETE CASCADE from candidates
 */
public class CandidateHeadItemEntity {
    /** Primary key and FK to candidates.id. */
    public int candidateId;
    /** Optional serialized item bytes for the candidate's head icon. */
    public byte[] headItemBytes;
    public CandidateHeadItemEntity() {}
}
