package net.democracycraft.democracyelections.data;

import java.util.*;

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
    private final Map<Integer, VoterDto> votersById = new LinkedHashMap<>();
    private final List<StatusChangeDto> statusChanges = new ArrayList<>();
    private TimeStampDto closesAt; // null = nunca
    private final TimeStampDto createdAt;

    private Integer durationDays; // null si no definido
    private TimeDto durationTime; // null si no definido

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
    public boolean removePoll(PollDto dto) { return this.polls.removeIf(p -> Objects.equals(p.getWorld(), dto.getWorld()) && p.getX()==dto.getX() && p.getY()==dto.getY() && p.getZ()==dto.getZ()); }

    public void addBallot(BallotDto ballot) { this.ballots.add(ballot); }
    public void addVoter(VoterDto voter) { this.votersById.put(voter.getId(), voter); }

    // Nuevo alias explícito
    public void appendBallot(BallotDto ballot) { this.ballots.add(ballot); }

    public void addStatusChange(StatusChangeDto change) { this.statusChanges.add(change); }

    // Duración
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public TimeDto getDurationTime() { return durationTime; }
    public void setDurationTime(TimeDto durationTime) { this.durationTime = durationTime; }
}
