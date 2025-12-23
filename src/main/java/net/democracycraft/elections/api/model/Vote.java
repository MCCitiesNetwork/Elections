package net.democracycraft.elections.api.model;

import net.democracycraft.elections.internal.data.TimeStampDto;

import java.util.List;

/**
 * Read-only view of a submitted ballot.
 * Selections contain candidate ids in the chosen order.
 */
public interface Vote {
    /** Unique ballot identifier. */
    int getId();
    /** Owning election id. */
    int getElectionId();
    /** Voter id who owns this ballot. */
    int getVoterId();
    /** Candidate ids (order-preserving for preferential, set-like for block). */
    List<Integer> getSelections(); // candidate ids
    /** Whether the ballot has been submitted. */
    boolean isSubmitted();
    /** Submission timestamp (nullable if not submitted). */
    TimeStampDto getSubmittedAt();
}
