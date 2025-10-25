package net.democracycraft.elections.api.service;

import net.democracycraft.elections.api.model.*;
import net.democracycraft.elections.data.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Public ElectionsService API used by third-party plugins.
 *
 * Threading and blocking contract:
 * - All I/O and heavy work MUST NOT run on the Bukkit main thread.
 * - Async-first methods return CompletableFuture and complete on a background thread.
 * - Failures complete the future exceptionally with a descriptive RuntimeException.
 * - Snapshot getters below are in-memory only and safe to call on the main thread.
 *
 * Nullability and semantics:
 * - Optional return values indicate absence (e.g., missing election/voter).
 * - Mutator methods return true if a state change occurred; false for no-op or not found.
 */
public interface ElectionsService {
    // --- In-memory snapshots (safe on main thread) ---
    /** Returns a current immutable snapshot list of elections from memory. */
    List<Election> listElectionsSnapshot();
    /** Returns an election snapshot by id from memory. */
    Optional<Election> getElectionSnapshot(int id);

    // --- Async API ---

    /** Creates a new election. Never blocks the main thread. */
    CompletableFuture<Election> createElectionAsync(String title, VotingSystem system, int minimumVotes, RequirementsDto requirements, String actor);
    /** Fetches an election by id. Non-blocking. */
    CompletableFuture<Optional<Election>> getElectionAsync(int id);
    /** Lists all elections. Non-blocking. */
    CompletableFuture<List<Election>> listElectionsAsync();
    /** Marks an election as DELETED (data retained). */
    CompletableFuture<Boolean> deleteElectionAsync(int id, String actor);

    // Metadata editing
    CompletableFuture<Boolean> setTitleAsync(int electionId, String title, String actor);
    CompletableFuture<Boolean> setSystemAsync(int electionId, VotingSystem system, String actor);
    CompletableFuture<Boolean> setMinimumVotesAsync(int electionId, int minimum, String actor);
    CompletableFuture<Boolean> setRequirementsAsync(int electionId, RequirementsDto requirements, String actor);
    CompletableFuture<Boolean> openElectionAsync(int electionId, String actor);
    CompletableFuture<Boolean> closeElectionAsync(int electionId, String actor);
    /** Retained for compatibility; implementations may ignore explicit closesAt and rely on duration. */
    CompletableFuture<Boolean> setClosesAtAsync(int electionId, TimeStampDto closesAt, String actor);

    /** Sets duration components; null values clear the duration. */
    CompletableFuture<Boolean> setDurationAsync(int electionId, Integer days, TimeDto time, String actor);

    /** Sets per-election ballot UI mode (MANUAL or SIMPLE). */
    CompletableFuture<Boolean> setBallotModeAsync(int electionId, BallotMode mode, String actor);

    // Candidates
    CompletableFuture<Optional<Candidate>> addCandidateAsync(int electionId, String name, String actor);
    CompletableFuture<Boolean> removeCandidateAsync(int electionId, int candidateId, String actor);

    // Polls
    CompletableFuture<Optional<Poll>> addPollAsync(int electionId, String world, int x, int y, int z, String actor);
    CompletableFuture<Boolean> removePollAsync(int electionId, String world, int x, int y, int z, String actor);

    // Voters (no actor required)
    CompletableFuture<Voter> registerVoterAsync(int electionId, String name);
    CompletableFuture<Optional<Voter>> getVoterByIdAsync(int electionId, int voterId);
    CompletableFuture<List<Voter>> listVotersAsync(int electionId);

    // Voting
    CompletableFuture<Boolean> submitPreferentialBallotAsync(int electionId, int voterId, List<Integer> orderedCandidateIds);
    CompletableFuture<Boolean> submitBlockBallotAsync(int electionId, int voterId, List<Integer> candidateIds);

    /** Sets the serialized ItemStack bytes used to render a candidate's head item in UIs. */
    CompletableFuture<Boolean> setCandidateHeadItemBytesAsync(int electionId, int candidateId, byte[] data);

    /** Retrieves the serialized ItemStack bytes for a candidate head item, or null if not set. */
    CompletableFuture<byte[]> getCandidateHeadItemBytesAsync(int electionId, int candidateId);

    /** Records an EXPORTED status change in the election status log. */
    CompletableFuture<Boolean> markExportedAsync(int electionId, String actor);

    // --- Internal synchronous convenience wrappers (do not run on main thread unless noted) ---
    /** Snapshot-only: safe on main thread. */
    default Optional<Election> getElection(int id) { return getElectionSnapshot(id); }
    /** Snapshot-only: safe on main thread. */
    default List<Election> listElections() { return listElectionsSnapshot(); }

    // Mutations below may perform I/O; DO NOT call on main thread.
    default Election createElection(String title, VotingSystem system, int minimumVotes, RequirementsDto requirements, String actor) {
        return createElectionAsync(title, system, minimumVotes, requirements, actor).join();
    }
    default boolean deleteElection(int id, String actor) { return deleteElectionAsync(id, actor).join(); }
    default boolean setTitle(int electionId, String title, String actor) { return setTitleAsync(electionId, title, actor).join(); }
    default boolean setSystem(int electionId, VotingSystem system, String actor) { return setSystemAsync(electionId, system, actor).join(); }
    default boolean setMinimumVotes(int electionId, int minimum, String actor) { return setMinimumVotesAsync(electionId, minimum, actor).join(); }
    default boolean setRequirements(int electionId, RequirementsDto requirements, String actor) { return setRequirementsAsync(electionId, requirements, actor).join(); }
    default boolean openElection(int electionId, String actor) { return openElectionAsync(electionId, actor).join(); }
    default boolean closeElection(int electionId, String actor) { return closeElectionAsync(electionId, actor).join(); }
    default boolean setClosesAt(int electionId, TimeStampDto closesAt, String actor) { return setClosesAtAsync(electionId, closesAt, actor).join(); }
    default boolean setDuration(int electionId, Integer days, TimeDto time, String actor) { return setDurationAsync(electionId, days, time, actor).join(); }
    default boolean setBallotMode(int electionId, BallotMode mode, String actor) { return setBallotModeAsync(electionId, mode, actor).join(); }
    default Optional<Candidate> addCandidate(int electionId, String name, String actor) { return addCandidateAsync(electionId, name, actor).join(); }
    default boolean removeCandidate(int electionId, int candidateId, String actor) { return removeCandidateAsync(electionId, candidateId, actor).join(); }
    default Optional<Poll> addPoll(int electionId, String world, int x, int y, int z, String actor) { return addPollAsync(electionId, world, x, y, z, actor).join(); }
    default boolean removePoll(int electionId, String world, int x, int y, int z, String actor) { return removePollAsync(electionId, world, x, y, z, actor).join(); }
    default Voter registerVoter(int electionId, String name) { return registerVoterAsync(electionId, name).join(); }
    default Optional<Voter> getVoterById(int electionId, int voterId) { return getVoterByIdAsync(electionId, voterId).join(); }
    default List<Voter> listVoters(int electionId) { return listVotersAsync(electionId).join(); }
    default boolean submitPreferentialBallot(int electionId, int voterId, List<Integer> orderedCandidateIds) { return submitPreferentialBallotAsync(electionId, voterId, orderedCandidateIds).join(); }
    default boolean submitBlockBallot(int electionId, int voterId, List<Integer> candidateIds) { return submitBlockBallotAsync(electionId, voterId, candidateIds).join(); }
    default boolean setCandidateHeadItemBytes(int electionId, int candidateId, byte[] data) { return setCandidateHeadItemBytesAsync(electionId, candidateId, data).join(); }
    default byte[] getCandidateHeadItemBytes(int electionId, int candidateId) { return getCandidateHeadItemBytesAsync(electionId, candidateId).join(); }
    default boolean markExported(int electionId, String actor) { return markExportedAsync(electionId, actor).join(); }

    /** Snapshot helper for head bytes (safe on main thread when implementation supports it). */
    default byte[] getCandidateHeadItemBytesSnapshot(int electionId, int candidateId) {
        // By default, call the async variant (may involve I/O). Implementations may override for pure snapshot.
        return getCandidateHeadItemBytesAsync(electionId, candidateId).join();
    }
}
