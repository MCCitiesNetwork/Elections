package net.democracycraft.elections.src.vote;

import net.democracycraft.elections.src.data.VotingSystem;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-player vote session storage used by the ballot UI.
 * Sessions are ephemeral and cleared on submit or when a new election is opened.
 */
public final class VoteSessionManager {
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private VoteSessionManager() {}

    /**
     * Retrieves or creates a new session for the given player and election.
     */
    public static Session open(UUID playerId, int electionId, int voterId, VotingSystem system) {
        Session s = new Session(playerId, electionId, voterId, system);
        SESSIONS.put(playerId, s);
        return s;
    }

    public static Optional<Session> get(UUID playerId) { return Optional.ofNullable(SESSIONS.get(playerId)); }

    public static void close(UUID playerId) { SESSIONS.remove(playerId); }

    public static final class Session {
        private final UUID playerId;
        private final int electionId;
        private final int voterId;
        private final VotingSystem system;
        // For BLOCK: use a set
        private final LinkedHashSet<Integer> selected = new LinkedHashSet<>();
        // For PREFERENTIAL: preserve order
        private final LinkedHashSet<Integer> ordered = new LinkedHashSet<>();

        Session(UUID playerId, int electionId, int voterId, VotingSystem system) {
            this.playerId = playerId; this.electionId = electionId; this.voterId = voterId; this.system = system;
        }
        public UUID getPlayerId() { return playerId; }
        public int getElectionId() { return electionId; }
        public int getVoterId() { return voterId; }
        public VotingSystem getSystem() { return system; }

        public Set<Integer> getSelected() { return selected; }
        public List<Integer> getOrdered() { return new ArrayList<>(ordered); }

        public void toggle(int candidateId) {
            if (system == VotingSystem.BLOCK) {
                if (!selected.add(candidateId)) selected.remove(candidateId);
            } else {
                if (!ordered.add(candidateId)) ordered.remove(candidateId);
            }
        }

        public void removeFromOrder(int candidateId) { ordered.remove(candidateId); }
        public void clear() { selected.clear(); ordered.clear(); }
    }
}

