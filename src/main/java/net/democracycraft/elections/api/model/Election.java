package net.democracycraft.elections.api.model;

import net.democracycraft.elections.src.data.*;

import java.util.List;
import java.util.function.Function;

/**
 * Read-only view of an election and its current state.
 *
 * Semantics:
 * - Implementations are immutable snapshots at the moment of retrieval.
 * - Values are non-null unless documented otherwise (e.g., closesAt, duration may be null).
 */
public interface Election {
    /** Unique identifier for this election. */
    int getId();
    /** Human-friendly title (non-null, may be empty). */
    String getTitle();
    /** Current lifecycle status. */
    ElectionStatus getStatus();
    /** Voting system in use. */
    VotingSystem getSystem();
    /** Minimum number of selections required. Always >= 1 logically. */
    int getMinimumVotes();
    /** Requirements applied to voters (may be null). */
    RequirementsDto getRequirements();

    /** Candidates participating (immutable list). */
    List<Candidate> getCandidates();
    /** Polling booth locations (immutable list). */
    List<Poll> getPolls();
    /** Submitted ballots (immutable list). */
    List<Vote> getBallots();

    /** Total registered voters count. */
    int getVoterCount();

    /** Scheduled close timestamp computed from duration (nullable). */
    TimeStampDto getClosesAt();
    /** Creation timestamp (non-null). */
    TimeStampDto getCreatedAt();

    /** Configured duration days (nullable). */
    Integer getDurationDays();
    /** Configured duration time components (nullable). */
    TimeDto getDurationTime();

    /** Returns the per-election ballot UI mode (MANUAL or SIMPLE). */
    BallotMode getBallotMode();

    /** Chronological audit log of status changes. */
    List<StatusChangeDto> getStatusChanges();

    ElectionDto toDto();

    ElectionDto toDtoWithNamedBallots(Function<Integer, String> voterNameProvider);

    String toJson(boolean includeVoterInBallots, Function<Integer, String> voterNameProvider);
}
