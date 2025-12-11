package net.democracycraft.elections.src.ui.vote;

import net.democracycraft.elections.src.data.VotingSystem;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ephemeral per-player per-election ballot session to persist selections while navigating the UI.
 */
public final class BallotSessions {
    private BallotSessions() {}

    private static final Map<Key, Session> SESSIONS = new ConcurrentHashMap<>();

    public static Session get(UUID playerId, int electionId, VotingSystem system) {
        Key k = new Key(playerId, electionId);
        return SESSIONS.computeIfAbsent(k, x -> new Session(electionId, system));
    }

    public static void clear(UUID playerId, int electionId) {
        SESSIONS.remove(new Key(playerId, electionId));
    }

    private record Key(UUID playerId, int electionId) {}

    public static class Session {
        private final int electionId;
        private VotingSystem system;
        // Block system: selected candidate ids
        private final LinkedHashSet<Integer> selected = new LinkedHashSet<>();
        // Preferential system: candidateId -> rank
        private final Map<Integer, Integer> ranks = new HashMap<>();

        Session(int electionId, VotingSystem system) {
            this.electionId = electionId;
            this.system = system;
        }

        public int getElectionId() { return electionId; }
        public VotingSystem getSystem() { return system; }
        public void setSystem(VotingSystem system) { this.system = system; }

        // Block API
        public boolean isSelected(int candidateId) { return selected.contains(candidateId); }
        public void setSelected(int candidateId, boolean value) { if (value) selected.add(candidateId); else selected.remove(candidateId); }
        public int selectedCount() { return selected.size(); }
        public List<Integer> getSelected() { return new ArrayList<>(selected); }

        // Preferential API
        public Integer getRank(int candidateId) { return ranks.get(candidateId); }
        public void setRank(int candidateId, Integer rank) { if (rank == null) ranks.remove(candidateId); else ranks.put(candidateId, rank); }
        public void clearRank(int candidateId) { ranks.remove(candidateId); }
        public void clearAll() { selected.clear(); ranks.clear(); }
        public Map<Integer, Integer> getAllRanks() { return new HashMap<>(ranks); }
    }
}

