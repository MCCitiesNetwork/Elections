package net.democracycraft.elections.src.data;

import java.util.*;

/**
 * DTO representing an election and its mutable state during its lifecycle.
 * Contains metadata (title, system, minimum votes), requirements, candidates, polls,
 * ballots, voter registry, status changes, and temporal attributes (created/closes/duration).
 */
public class ElectionDto implements Dto {
    private final int id;
    private String title;
    private ElectionStatus status;
    private VotingSystem system;
    private int minimumVotes;
    private RequirementsDto requirements;
    private final List<CandidateDto> candidates = new ArrayList<>();
    private final List<PollDto> polls = new ArrayList<>();
    private final List<BallotDto> ballots = new ArrayList<>();
    /**
     * Internal voter registry. Marked transient to exclude from export JSON; voters can be embedded per-ballot when needed.
     */
    private final transient Map<Integer, VoterDto> votersById = new LinkedHashMap<>();
    private final List<StatusChangeDto> statusChanges = new ArrayList<>();
    /** When null, the election does not auto-close. */
    private TimeStampDto closesAt;
    private final TimeStampDto createdAt;

    /** Optional duration fields (null when not defined). */
    private Integer durationDays;
    private TimeDto durationTime;

    /** Per-election ballot UI mode (defaults to MANUAL). */
    private BallotMode ballotMode = BallotMode.MANUAL;

    public ElectionDto(int id, String title, VotingSystem system, int minimumVotes, RequirementsDto requirements, TimeStampDto createdAt) {
        this.id = id;
        this.title = title;
        this.status = ElectionStatus.CLOSED;
        this.system = system;
        this.minimumVotes = Math.max(1, minimumVotes);
        this.requirements = requirements;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public ElectionStatus getStatus() { return status; }
    public void setStatus(ElectionStatus status) { this.status = status; }

    public VotingSystem getSystem() { return system; }
    public void setSystem(VotingSystem system) { this.system = system; }

    public int getMinimumVotes() { return minimumVotes; }
    public void setMinimumVotes(int minimumVotes) { this.minimumVotes = Math.max(1, minimumVotes); }

    public RequirementsDto getRequirements() { return requirements; }
    public void setRequirements(RequirementsDto requirements) { this.requirements = requirements; }

    public List<CandidateDto> getCandidates() { return Collections.unmodifiableList(candidates); }
    public List<PollDto> getPolls() { return Collections.unmodifiableList(polls); }
    public List<BallotDto> getBallots() { return Collections.unmodifiableList(ballots); }
    public Map<Integer, VoterDto> getVotersById() { return Collections.unmodifiableMap(votersById); }
    public List<StatusChangeDto> getStatusChanges() { return Collections.unmodifiableList(statusChanges); }

    public TimeStampDto getClosesAt() { return closesAt; }
    public void setClosesAt(TimeStampDto closesAt) { this.closesAt = closesAt; }

    public TimeStampDto getCreatedAt() { return createdAt; }

    public void addCandidate(CandidateDto dto) { this.candidates.add(dto); }
    public boolean removeCandidate(int candidateId) { return this.candidates.removeIf(c -> c.getId() == candidateId); }

    public void addPoll(PollDto dto) { this.polls.add(dto); }
    public boolean removePoll(PollDto dto) { return this.polls.removeIf(p -> Objects.equals(p.world(), dto.world()) && p.x()==dto.x() && p.y()==dto.y() && p.z()==dto.z()); }

    public void addBallot(BallotDto ballot) { this.ballots.add(ballot); }
    public void addVoter(VoterDto voter) { this.votersById.put(voter.id(), voter); }

    /** Explicit alias for clarity. */
    public void appendBallot(BallotDto ballot) { this.ballots.add(ballot); }

    public void addStatusChange(StatusChangeDto change) { this.statusChanges.add(change); }

    // Duration
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public TimeDto getDurationTime() { return durationTime; }
    public void setDurationTime(TimeDto durationTime) { this.durationTime = durationTime; }

    // Ballot mode
    public BallotMode getBallotMode() { return ballotMode; }
    public void setBallotMode(BallotMode ballotMode) { this.ballotMode = ballotMode == null ? BallotMode.MANUAL : ballotMode; }
}
