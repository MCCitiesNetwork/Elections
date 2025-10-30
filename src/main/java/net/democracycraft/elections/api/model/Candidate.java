package net.democracycraft.elections.api.model;

/**
 * Immutable view of a candidate within an election.
 *
 * Contract:
 * - Implementations are read-only snapshots; fields do not change after retrieval.
 * - Names are non-null, user-facing labels.
 */
public interface Candidate {
    /** Unique identifier of the candidate within its election. */
    int getId();
    /** Display name of the candidate (non-null, may be empty). */
    String getName();
    /** Optional party name for the candidate; may be null or empty if not set. */
    String getParty();
}
