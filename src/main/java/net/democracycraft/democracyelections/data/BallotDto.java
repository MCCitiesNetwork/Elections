package net.democracycraft.democracyelections.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DTO representing a submitted or in-progress ballot for a specific election and voter.
 * For preferential voting, the order of selections matters; for block voting, the set is evaluated.
 */
public class BallotDto implements Dto {
    private final int id;
    private final int electionId;
    private final int voterId;
    /** Candidate IDs; for PREFERENTIAL voting the order is significant. */
    private final List<Integer> selections;
    /** When non-null, indicates the ballot has been submitted at that timestamp. */
    private TimeStampDto submittedAt;

    public BallotDto(int id, int electionId, int voterId) {
        this.id = id;
        this.electionId = electionId;
        this.voterId = voterId;
        this.selections = new ArrayList<>();
    }

    public int getId() { return id; }
    public int getElectionId() { return electionId; }
    public int getVoterId() { return voterId; }

    public List<Integer> getSelections() { return Collections.unmodifiableList(selections); }
    public void clearSelections() { selections.clear(); }
    public void addSelection(int candidateId) { if (!selections.contains(candidateId)) selections.add(candidateId); }
    public void removeSelection(int candidateId) { selections.remove(Integer.valueOf(candidateId)); }

    public boolean isSubmitted() { return submittedAt != null; }
    public TimeStampDto getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(TimeStampDto submittedAt) { this.submittedAt = submittedAt; }
}
