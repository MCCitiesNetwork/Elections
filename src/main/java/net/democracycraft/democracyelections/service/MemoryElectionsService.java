package net.democracycraft.democracyelections.service;

import net.democracycraft.democracyelections.api.model.*;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.data.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * In-memory implementation of ElectionsService, without external persistence.
 * Focused on domain logic and independent from any UI concerns.
 */
public class MemoryElectionsService implements ElectionsService {

    private final Map<Integer, ElectionDto> elections = new LinkedHashMap<>();
    private final AtomicInteger electionIdSeq = new AtomicInteger(1);

    private static TimeStampDto now() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        return new TimeStampDto(
                new DateDto(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR)),
                new TimeDto(c.get(Calendar.SECOND), c.get(Calendar.MINUTE), c.get(Calendar.HOUR_OF_DAY))
        );
    }

    @Override
    public synchronized Election createElection(String title, VotingSystem system, int minimumVotes, RequirementsDto requirements) {
        int id = electionIdSeq.getAndIncrement();
        ElectionDto dto = new ElectionDto(id, title, system, minimumVotes, requirements, now());
        dto.addStatusChange(new StatusChangeDto(dto.getCreatedAt(), StateChangeType.CREATED));
        elections.put(id, dto);
        return wrapElection(dto);
    }

    @Override
    public synchronized Optional<Election> getElection(int id) {
        ElectionDto dto = elections.get(id);
        return Optional.ofNullable(dto).map(this::wrapElection);
    }

    @Override
    public synchronized List<Election> listElections() {
        return elections.values().stream().map(this::wrapElection).toList();
    }

    @Override
    public synchronized boolean deleteElection(int id) {
        ElectionDto dto = elections.get(id);
        if (dto == null) return false;
        if (dto.getStatus() == ElectionStatus.DELETED) return true;
        dto.setStatus(ElectionStatus.DELETED);
        dto.addStatusChange(new StatusChangeDto(now(), StateChangeType.DELETED));
        return true;
    }

    @Override
    public synchronized boolean setTitle(int electionId, String title) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        dto.setTitle(Objects.requireNonNullElse(title, ""));
        return true;
    }

    @Override
    public synchronized boolean setSystem(int electionId, VotingSystem system) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        dto.setSystem(system);
        return true;
    }

    @Override
    public synchronized boolean setMinimumVotes(int electionId, int minimum) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        dto.setMinimumVotes(Math.max(0, minimum));
        return true;
    }

    @Override
    public synchronized boolean setRequirements(int electionId, RequirementsDto requirements) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        dto.setRequirements(requirements);
        return true;
    }

    @Override
    public synchronized boolean openElection(int electionId) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        if (dto.getStatus() == ElectionStatus.OPEN) return true;
        dto.setStatus(ElectionStatus.OPEN);
        dto.addStatusChange(new StatusChangeDto(now(), StateChangeType.OPENED));
        return true;
    }

    @Override
    public synchronized boolean closeElection(int electionId) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        if (dto.getStatus() == ElectionStatus.CLOSED) return true;
        dto.setStatus(ElectionStatus.CLOSED);
        dto.addStatusChange(new StatusChangeDto(now(), StateChangeType.CLOSED));
        return true;
    }

    @Override
    public synchronized boolean setClosesAt(int electionId, TimeStampDto closesAt) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        dto.setClosesAt(closesAt);
        return true;
    }

    @Override
    public synchronized boolean setDuration(int electionId, Integer days, TimeDto time) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        dto.setDurationDays(days);
        dto.setDurationTime(time);
        return true;
    }

    @Override
    public synchronized Optional<Candidate> addCandidate(int electionId, String name) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return Optional.empty();
        int nextId = dto.getCandidates().stream().mapToInt(CandidateDto::getId).max().orElse(0) + 1;
        CandidateDto c = new CandidateDto(nextId, name);
        dto.addCandidate(c);
        return Optional.of(wrapCandidate(c));
    }

    @Override
    public synchronized boolean removeCandidate(int electionId, int candidateId) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        return dto.removeCandidate(candidateId);
    }

    @Override
    public synchronized Optional<Poll> addPoll(int electionId, String world, int x, int y, int z) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return Optional.empty();
        PollDto p = new PollDto(world, x, y, z);
        dto.addPoll(p);
        return Optional.of(wrapPoll(p));
    }

    @Override
    public synchronized boolean removePoll(int electionId, String world, int x, int y, int z) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        return dto.removePoll(new PollDto(world, x, y, z));
    }

    @Override
    public synchronized Voter registerVoter(int electionId, String name) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) throw new IllegalArgumentException("Election not found");
        Optional<VoterDto> existing = dto.getVotersById().values().stream()
                .filter(v -> v.getName().equalsIgnoreCase(name))
                .findFirst();
        if (existing.isPresent()) return wrapVoter(existing.get());
        int nextId = dto.getVotersById().keySet().stream().mapToInt(i -> i).max().orElse(0) + 1;
        VoterDto v = new VoterDto(nextId, name);
        dto.addVoter(v);
        return wrapVoter(v);
    }

    @Override
    public synchronized Optional<Voter> getVoterById(int electionId, int voterId) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return Optional.empty();
        VoterDto v = dto.getVotersById().get(voterId);
        return Optional.ofNullable(v).map(this::wrapVoter);
    }

    @Override
    public synchronized List<Voter> listVoters(int electionId) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return List.of();
        return dto.getVotersById().values().stream().map(this::wrapVoter).toList();
    }

    @Override
    public synchronized boolean submitPreferentialBallot(int electionId, int voterId, List<Integer> orderedCandidateIds) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        if (dto.getStatus() != ElectionStatus.OPEN) return false;
        if (dto.getSystem() != VotingSystem.PREFERENTIAL) return false;
        if (!dto.getVotersById().containsKey(voterId)) return false;
        if (hasSubmitted(dto, voterId)) return false;

        if (orderedCandidateIds == null) return false;
        List<Integer> clean = orderedCandidateIds.stream().filter(Objects::nonNull).toList();
        // Preserve order while removing duplicates
        LinkedHashSet<Integer> unique = new LinkedHashSet<>(clean);
        if (unique.size() < Math.max(1, dto.getMinimumVotes())) return false;
        Set<Integer> allowed = dto.getCandidates().stream().map(CandidateDto::getId).collect(Collectors.toSet());
        if (!allowed.containsAll(unique)) return false;
        if (unique.size() > allowed.size()) return false;

        BallotDto b = new BallotDto(nextBallotId(dto), dto.getId(), voterId);
        b.clearSelections();
        unique.forEach(b::addSelection);
        b.setSubmittedAt(now());
        dto.appendBallot(b);
        return true;
    }

    @Override
    public synchronized boolean submitBlockBallot(int electionId, int voterId, List<Integer> candidateIds) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        if (dto.getStatus() != ElectionStatus.OPEN) return false;
        if (dto.getSystem() != VotingSystem.BLOCK) return false;
        if (!dto.getVotersById().containsKey(voterId)) return false;
        if (hasSubmitted(dto, voterId)) return false;

        if (candidateIds == null) return false;
        Set<Integer> unique = new LinkedHashSet<>(candidateIds.stream().filter(Objects::nonNull).toList());
        if (unique.size() != Math.max(1, dto.getMinimumVotes())) return false; // must be exact
        Set<Integer> allowed = dto.getCandidates().stream().map(CandidateDto::getId).collect(Collectors.toSet());
        if (!allowed.containsAll(unique)) return false;

        BallotDto b = new BallotDto(nextBallotId(dto), dto.getId(), voterId);
        b.clearSelections();
        unique.forEach(b::addSelection);
        b.setSubmittedAt(now());
        dto.appendBallot(b);
        return true;
    }

    private static boolean hasSubmitted(ElectionDto dto, int voterId) {
        return dto.getBallots().stream().anyMatch(b -> b.getVoterId() == voterId && b.isSubmitted());
    }

    private static int nextBallotId(ElectionDto dto) {
        return dto.getBallots().stream().mapToInt(BallotDto::getId).max().orElse(0) + 1;
    }

    // Read-only wrappers to the public API interfaces
    private Election wrapElection(ElectionDto dto) { return new ElectionView(dto); }
    private Candidate wrapCandidate(CandidateDto dto) { return new CandidateView(dto); }
    private Poll wrapPoll(PollDto dto) { return new PollView(dto); }
    private Vote wrapVote(BallotDto dto) { return new VoteView(dto); }
    private Voter wrapVoter(VoterDto dto) { return new VoterView(dto); }

    private class ElectionView implements Election {
        private final ElectionDto dto;
        ElectionView(ElectionDto dto) { this.dto = dto; }
        @Override public int getId() { return dto.getId(); }
        @Override public String getTitle() { return dto.getTitle(); }
        @Override public ElectionStatus getStatus() { return dto.getStatus(); }
        @Override public VotingSystem getSystem() { return dto.getSystem(); }
        @Override public int getMinimumVotes() { return dto.getMinimumVotes(); }
        @Override public RequirementsDto getRequirements() { return dto.getRequirements(); }
        @Override public List<Candidate> getCandidates() { return dto.getCandidates().stream().map(MemoryElectionsService.this::wrapCandidate).toList(); }
        @Override public List<Poll> getPolls() { return dto.getPolls().stream().map(MemoryElectionsService.this::wrapPoll).toList(); }
        @Override public List<Vote> getBallots() { return dto.getBallots().stream().map(MemoryElectionsService.this::wrapVote).toList(); }
        @Override public int getVoterCount() { return dto.getVotersById().size(); }
        @Override public TimeStampDto getClosesAt() { return dto.getClosesAt(); }
        @Override public TimeStampDto getCreatedAt() { return dto.getCreatedAt(); }
        @Override public Integer getDurationDays() { return dto.getDurationDays(); }
        @Override public TimeDto getDurationTime() { return dto.getDurationTime(); }
        @Override public List<StatusChangeDto> getStatusChanges() { return dto.getStatusChanges(); }
    }

    private record CandidateView(CandidateDto dto) implements Candidate {
        @Override public int getId() { return dto.getId(); }
        @Override public String getName() { return dto.getName(); }
    }

    private record PollView(PollDto dto) implements Poll {
        @Override public String getWorld() { return dto.getWorld(); }
        @Override public int getX() { return dto.getX(); }
        @Override public int getY() { return dto.getY(); }
        @Override public int getZ() { return dto.getZ(); }
    }

    private record VoteView(BallotDto dto) implements Vote {
        @Override public int getId() { return dto.getId(); }
        @Override public int getElectionId() { return dto.getElectionId(); }
        @Override public int getVoterId() { return dto.getVoterId(); }
        @Override public List<Integer> getSelections() { return dto.getSelections(); }
        @Override public boolean isSubmitted() { return dto.isSubmitted(); }
        @Override public TimeStampDto getSubmittedAt() { return dto.getSubmittedAt(); }
    }

    private record VoterView(VoterDto dto) implements Voter {
        @Override public int getId() { return dto.getId(); }
        @Override public String getName() { return dto.getName(); }
    }
}
