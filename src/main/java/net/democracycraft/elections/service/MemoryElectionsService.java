package net.democracycraft.elections.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.democracycraft.elections.api.model.*;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.data.BallotMode;
import net.democracycraft.elections.data.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.democracycraft.elections.command.framework.CommandContext.tsToString;

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

    // Snapshot API
    @Override
    public synchronized List<Election> listElectionsSnapshot() { return listElections(); }
    @Override
    public synchronized Optional<Election> getElectionSnapshot(int id) { return getElection(id); }

    // Legacy synchronous helpers (no longer overriding interface)
    public synchronized Election createElection(String title, VotingSystem system, int minimumVotes, RequirementsDto requirements, String actor) {
        int id = electionIdSeq.getAndIncrement();
        ElectionDto dto = new ElectionDto(id, title, system, minimumVotes, requirements, now());
        dto.addStatusChange(new StatusChangeDto(dto.getCreatedAt(), StateChangeType.CREATED, actor, "title=" + title + ",system=" + system + ",min=" + Math.max(1, minimumVotes)));
        elections.put(id, dto);
        return wrapElection(dto);
    }

    public synchronized Optional<Election> getElection(int id) {
        ElectionDto dto = elections.get(id);
        return Optional.ofNullable(dto).map(this::wrapElection);
    }

    public synchronized List<Election> listElections() {
        return elections.values().stream().map(this::wrapElection).toList();
    }

    public synchronized boolean deleteElection(int id, String actor) {
        ElectionDto dto = elections.get(id);
        if (dto == null) return false;
        if (dto.getStatus() == ElectionStatus.DELETED) return true;
        dto.setStatus(ElectionStatus.DELETED);
        dto.addStatusChange(new StatusChangeDto(now(), StateChangeType.DELETED, actor, null));
        return true;
    }

    public synchronized boolean setTitle(int electionId, String title, String actor) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        String newTitle = Objects.requireNonNullElse(title, "");
        if (Objects.equals(dto.getTitle(), newTitle)) return true;
        String old = dto.getTitle();
        dto.setTitle(newTitle);
        dto.addStatusChange(new StatusChangeDto(now(), StateChangeType.TITLE_CHANGED, actor, "old=" + old + ",new=" + newTitle));
        return true;
    }

    public synchronized boolean setSystem(int electionId, VotingSystem system, String actor) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        if (dto.getSystem() == system) return true;
        VotingSystem old = dto.getSystem();
        dto.setSystem(system);
        dto.addStatusChange(new StatusChangeDto(now(), StateChangeType.SYSTEM_CHANGED, actor, "old=" + old + ",new=" + system));
        return true;
    }

    public synchronized boolean setMinimumVotes(int electionId, int minimum, String actor) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        int newMin = Math.max(0, minimum);
        int effectiveNewMin = Math.max(1, newMin);
        if (dto.getMinimumVotes() == effectiveNewMin) return true;
        int old = dto.getMinimumVotes();
        dto.setMinimumVotes(newMin);
        dto.addStatusChange(new StatusChangeDto(now(), StateChangeType.MINIMUM_CHANGED, actor, "old=" + old + ",new=" + effectiveNewMin));
        return true;
    }

    public synchronized boolean setRequirements(int electionId, RequirementsDto requirements, String actor) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        RequirementsDto old = dto.getRequirements();
        boolean changed;
        if (old == null && requirements == null) {
            changed = false;
        } else if (old == null || requirements == null) {
            changed = true;
        } else {
            changed = !Objects.equals(old.getPermissions(), requirements.getPermissions())
                    || old.getMinActivePlaytimeMinutes() != requirements.getMinActivePlaytimeMinutes();
        }
        if (!changed) return true;
        dto.setRequirements(requirements);
        String details = (requirements == null) ? "null" : "perms=" + (requirements.getPermissions()==null?0:requirements.getPermissions().size()) + ",minutes=" + requirements.getMinActivePlaytimeMinutes();
        dto.addStatusChange(new StatusChangeDto(now(), StateChangeType.REQUIREMENTS_CHANGED, actor, details));
        return true;
    }

    public synchronized boolean openElection(int electionId, String actor) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        if (dto.getStatus() == ElectionStatus.OPEN) return true;
        // Enforce Block system constraint: minVotes <= candidates
        if (dto.getSystem() == VotingSystem.BLOCK) {
            int min = Math.max(1, dto.getMinimumVotes());
            int cands = dto.getCandidates().size();
            if (min > cands) return false;
        }
        dto.setStatus(ElectionStatus.OPEN);
        dto.addStatusChange(new StatusChangeDto(now(), StateChangeType.OPENED, actor, null));
        return true;
    }

    public synchronized boolean closeElection(int electionId, String actor) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        if (dto.getStatus() == ElectionStatus.CLOSED) return true;
        dto.setStatus(ElectionStatus.CLOSED);
        dto.addStatusChange(new StatusChangeDto(now(), StateChangeType.CLOSED, actor, null));
        return true;
    }

    public synchronized boolean setClosesAt(int electionId, TimeStampDto closesAt, String actor) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        TimeStampDto old = dto.getClosesAt();
        if (Objects.equals(old, closesAt)) return true;
        StateChangeType type;
        if (old == null) type = StateChangeType.CLOSES_AT_SET;
        else if (closesAt == null) type = StateChangeType.CLOSES_AT_CLEARED;
        else type = StateChangeType.CLOSES_AT_CHANGED;
        dto.setClosesAt(closesAt);
        String det = (closesAt == null ? "null" : tsToString(closesAt));
        dto.addStatusChange(new StatusChangeDto(now(), type, actor, det));
        return true;
    }

    public synchronized boolean setDuration(int electionId, Integer days, TimeDto time, String actor) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        Integer oldDays = dto.getDurationDays();
        TimeDto oldTime = dto.getDurationTime();
        if (Objects.equals(oldDays, days) && Objects.equals(oldTime, time)) return true;
        boolean oldNull = oldDays == null && oldTime == null;
        boolean newNull = days == null && time == null;
        StateChangeType type;
        if (oldNull && !newNull) type = StateChangeType.DURATION_SET;
        else if (!oldNull && newNull) type = StateChangeType.DURATION_CLEARED;
        else type = StateChangeType.DURATION_CHANGED;
        dto.setDurationDays(days);
        dto.setDurationTime(time);
        String det = (days == null && time == null) ? "null" : ("days=" + (days==null?0:days) + ",time=" + (time==null?"00:00:00":(time.hour()+":"+time.minute()+":"+time.second())));
        dto.addStatusChange(new StatusChangeDto(now(), type, actor, det));
        return true;
    }

    /** Sets the per-election ballot UI mode. */
    public synchronized boolean setBallotMode(int electionId, BallotMode mode, String actor) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        BallotMode newMode = (mode == null ? BallotMode.MANUAL : mode);
        if (dto.getBallotMode() == newMode) return true;
        BallotMode old = dto.getBallotMode();
        dto.setBallotMode(newMode);
        dto.addStatusChange(new StatusChangeDto(now(), StateChangeType.BALLOT_MODE_CHANGED, actor, "old=" + old + ",new=" + newMode));
        return true;
    }

    public synchronized Optional<Candidate> addCandidate(int electionId, String name, String actor) {
        return addCandidate(electionId, name, null, actor);
    }

    public synchronized Optional<Candidate> addCandidate(int electionId, String name, String party, String actor) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return Optional.empty();
        int nextId = dto.getCandidates().stream().mapToInt(CandidateDto::getId).max().orElse(0) + 1;
        CandidateDto c = new CandidateDto(nextId, name);
        c.setParty(party);
        dto.addCandidate(c);
        dto.addStatusChange(new StatusChangeDto(now(), StateChangeType.CANDIDATE_ADDED, actor, "id=" + c.getId() + ",name=" + c.getName() + (c.getParty()==null?"":" ,party="+c.getParty())));
        return Optional.of(wrapCandidate(c));
    }

    public synchronized boolean removeCandidate(int electionId, int candidateId, String actor) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        boolean removed = dto.removeCandidate(candidateId);
        if (removed) dto.addStatusChange(new StatusChangeDto(now(), StateChangeType.CANDIDATE_REMOVED, actor, "id=" + candidateId));
        return removed;
    }

    public synchronized Optional<Poll> addPoll(int electionId, String world, int x, int y, int z, String actor) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return Optional.empty();
        // Cross-election conflict: forbid same world/x/y/z used by another election
        for (ElectionDto other : elections.values()) {
            if (other.getId() == electionId) continue;
            for (PollDto p0 : other.getPolls()) {
                if (p0.getWorld().equalsIgnoreCase(world) && p0.getX()==x && p0.getY()==y && p0.getZ()==z) {
                    return Optional.empty();
                }
            }
        }
        PollDto p = new PollDto(world, x, y, z);
        dto.addPoll(p);
        dto.addStatusChange(new StatusChangeDto(now(), StateChangeType.POLL_ADDED, actor, "world=" + world + ",x=" + x + ",y=" + y + ",z=" + z));
        return Optional.of(wrapPoll(p));
    }

    public synchronized boolean removePoll(int electionId, String world, int x, int y, int z, String actor) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        boolean removed = dto.removePoll(new PollDto(world, x, y, z));
        if (removed) dto.addStatusChange(new StatusChangeDto(now(), StateChangeType.POLL_REMOVED, actor, "world=" + world + ",x=" + x + ",y=" + y + ",z=" + z));
        return removed;
    }

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

    public synchronized Optional<Voter> getVoterById(int electionId, int voterId) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return Optional.empty();
        VoterDto v = dto.getVotersById().get(voterId);
        return Optional.ofNullable(v).map(this::wrapVoter);
    }

    public synchronized List<Voter> listVoters(int electionId) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return List.of();
        return dto.getVotersById().values().stream().map(this::wrapVoter).toList();
    }

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
        // attach voter details in the ballot for future export if needed
        VoterDto voter = dto.getVotersById().get(voterId);
        if (voter != null) b.setVoter(voter);
        b.clearSelections();
        unique.forEach(b::addSelection);
        b.setSubmittedAt(now());
        dto.appendBallot(b);
        return true;
    }

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
        // attach voter details in the ballot for future export if needed
        VoterDto voter = dto.getVotersById().get(voterId);
        if (voter != null) b.setVoter(voter);
        b.clearSelections();
        unique.forEach(b::addSelection);
        b.setSubmittedAt(now());
        dto.appendBallot(b);
        return true;
    }

    public synchronized boolean markExported(int electionId, String actor) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        dto.addStatusChange(new StatusChangeDto(now(), StateChangeType.EXPORTED, actor, null));
        return true;
    }

    public synchronized boolean setCandidateHeadItemBytes(int electionId, int candidateId, byte[] data) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return false;
        for (CandidateDto candidate : dto.getCandidates()) {
            if (candidate.getId() == candidateId) {
                candidate.setHeadItemBytes(data);
                return true;
            }
        }
        return false;
    }

    public synchronized byte[] getCandidateHeadItemBytes(int electionId, int candidateId) {
        ElectionDto dto = elections.get(electionId);
        if (dto == null) return null;
        for (CandidateDto candidate : dto.getCandidates()) {
            if (candidate.getId() == candidateId) return candidate.getHeadItemBytes();
        }
        return null;
    }

    /** Loads a full snapshot replacing existing in-memory state (not part of the public API). */
    public synchronized void loadSnapshot(List<ElectionDto> snapshot) {
        this.elections.clear();
        int maxId = 0;
        for (ElectionDto e : snapshot) {
            this.elections.put(e.getId(), e);
            maxId = Math.max(maxId, e.getId());
        }
        this.electionIdSeq.set(maxId + 1);
    }

    /** Inserts or replaces a single election DTO snapshot (internal helper). */
    public synchronized void upsertElection(ElectionDto dto) {
        if (dto == null) return;
        this.elections.put(dto.getId(), dto);
        this.electionIdSeq.set(Math.max(this.electionIdSeq.get(), dto.getId() + 1));
    }

    /** Removes an election from memory by id (internal helper). */
    public synchronized void removeElectionById(int id) {
        this.elections.remove(id);
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


        private static final Gson GSON = new GsonBuilder()
                .disableHtmlEscaping()
                .setPrettyPrinting()
                .create();

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
        @Override public BallotMode getBallotMode() { return dto.getBallotMode(); }
        @Override public List<StatusChangeDto> getStatusChanges() { return dto.getStatusChanges(); }

        private ElectionDto getSimpleDto() {
            ElectionDto electionDto = new ElectionDto(
                this.getId(),
                this.getTitle(),
                this.getSystem(),
                this.getMinimumVotes(),
                this.getRequirements(),
                this.getCreatedAt()
            );

            electionDto.setStatus(this.getStatus());
            electionDto.setClosesAt(this.getClosesAt());
            electionDto.setDurationDays(this.getDurationDays());
            electionDto.setDurationTime(this.getDurationTime());

            for (Candidate c : this.getCandidates()) {
                CandidateDto cd = new CandidateDto(c.getId(), c.getName());
                cd.setParty(c.getParty());
                electionDto.addCandidate(cd);
            }
            this.getPolls().forEach(p -> electionDto.addPoll(new PollDto(p.getWorld(), p.getX(), p.getY(), p.getZ())));

            this.getStatusChanges().forEach(electionDto::addStatusChange);

            return electionDto;
        }

        @Override
        public ElectionDto toDto() {
            ElectionDto electionDto = getSimpleDto();

            for (Vote b : this.getBallots()) {
                BallotDto bd = new BallotDto(b.getId(), b.getElectionId(), b.getVoterId());
                b.getSelections().forEach(bd::addSelection);
                bd.setSubmittedAt(b.getSubmittedAt());
                electionDto.appendBallot(bd);
            }

            return electionDto;

        }

        @Override
        public ElectionDto toDtoWithNamedBallots(@NotNull Function<Integer, String> voterNameProvider) {
            ElectionDto electionDto = getSimpleDto();

            for (Vote b : this.getBallots()) {
                BallotDto bd = new BallotDto(b.getId(), b.getElectionId(), b.getVoterId());
                b.getSelections().forEach(bd::addSelection);
                bd.setSubmittedAt(b.getSubmittedAt());
                String name = voterNameProvider.apply(b.getVoterId());
                if (name != null) bd.setVoter(new VoterDto(b.getVoterId(), name));

                electionDto.appendBallot(bd);
            }

            return electionDto;
        }

        @Override
        public String toJson(boolean includeVoterInBallots, Function<Integer, String> voterNameProvider) {
            ElectionDto electionDto = includeVoterInBallots ? this.toDtoWithNamedBallots(voterNameProvider) : this.toDto();

            return GSON.toJson(electionDto);
        }
    }

    private record CandidateView(CandidateDto dto) implements Candidate {
        @Override public int getId() { return dto.getId(); }
        @Override public String getName() { return dto.getName(); }
        @Override public String getParty() { return dto.getParty(); }
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

    // --- Async API wrappers ---
    @Override public CompletableFuture<Election> createElectionAsync(String title, VotingSystem system, int minimumVotes, RequirementsDto requirements, String actor) {
        return CompletableFuture.completedFuture(createElection(title, system, minimumVotes, requirements, actor));
    }
    @Override public CompletableFuture<Optional<Election>> getElectionAsync(int id) { return CompletableFuture.completedFuture(getElection(id)); }
    @Override public CompletableFuture<List<Election>> listElectionsAsync() { return CompletableFuture.completedFuture(listElections()); }
    @Override public CompletableFuture<Boolean> deleteElectionAsync(int id, String actor) { return CompletableFuture.completedFuture(deleteElection(id, actor)); }
    @Override public CompletableFuture<Boolean> setTitleAsync(int electionId, String title, String actor) { return CompletableFuture.completedFuture(setTitle(electionId, title, actor)); }
    @Override public CompletableFuture<Boolean> setSystemAsync(int electionId, VotingSystem system, String actor) { return CompletableFuture.completedFuture(setSystem(electionId, system, actor)); }
    @Override public CompletableFuture<Boolean> setMinimumVotesAsync(int electionId, int minimum, String actor) { return CompletableFuture.completedFuture(setMinimumVotes(electionId, minimum, actor)); }
    @Override public CompletableFuture<Boolean> setRequirementsAsync(int electionId, RequirementsDto requirements, String actor) { return CompletableFuture.completedFuture(setRequirements(electionId, requirements, actor)); }
    @Override public CompletableFuture<Boolean> openElectionAsync(int electionId, String actor) { return CompletableFuture.completedFuture(openElection(electionId, actor)); }
    @Override public CompletableFuture<Boolean> closeElectionAsync(int electionId, String actor) { return CompletableFuture.completedFuture(closeElection(electionId, actor)); }
    @Override public CompletableFuture<Boolean> setClosesAtAsync(int electionId, TimeStampDto closesAt, String actor) { return CompletableFuture.completedFuture(setClosesAt(electionId, closesAt, actor)); }
    @Override public CompletableFuture<Boolean> setDurationAsync(int electionId, Integer days, TimeDto time, String actor) { return CompletableFuture.completedFuture(setDuration(electionId, days, time, actor)); }
    @Override public CompletableFuture<Boolean> setBallotModeAsync(int electionId, BallotMode mode, String actor) { return CompletableFuture.completedFuture(setBallotMode(electionId, mode, actor)); }
    @Override public CompletableFuture<Optional<Candidate>> addCandidateAsync(int electionId, String name, String actor) { return CompletableFuture.completedFuture(addCandidate(electionId, name, actor)); }
    @Override public CompletableFuture<Optional<Candidate>> addCandidateAsync(int electionId, String name, String party, String actor) { return CompletableFuture.completedFuture(addCandidate(electionId, name, party, actor)); }
    @Override public CompletableFuture<Boolean> removeCandidateAsync(int electionId, int candidateId, String actor) { return CompletableFuture.completedFuture(removeCandidate(electionId, candidateId, actor)); }
    @Override public CompletableFuture<Optional<Poll>> addPollAsync(int electionId, String world, int x, int y, int z, String actor) { return CompletableFuture.completedFuture(addPoll(electionId, world, x, y, z, actor)); }
    @Override public CompletableFuture<Boolean> removePollAsync(int electionId, String world, int x, int y, int z, String actor) { return CompletableFuture.completedFuture(removePoll(electionId, world, x, y, z, actor)); }
    @Override public CompletableFuture<Voter> registerVoterAsync(int electionId, String name) { return CompletableFuture.completedFuture(registerVoter(electionId, name)); }
    @Override public CompletableFuture<Optional<Voter>> getVoterByIdAsync(int electionId, int voterId) { return CompletableFuture.completedFuture(getVoterById(electionId, voterId)); }
    @Override public CompletableFuture<List<Voter>> listVotersAsync(int electionId) { return CompletableFuture.completedFuture(listVoters(electionId)); }
    @Override public CompletableFuture<Boolean> submitPreferentialBallotAsync(int electionId, int voterId, List<Integer> orderedCandidateIds) { return CompletableFuture.completedFuture(submitPreferentialBallot(electionId, voterId, orderedCandidateIds)); }
    @Override public CompletableFuture<Boolean> submitBlockBallotAsync(int electionId, int voterId, List<Integer> candidateIds) { return CompletableFuture.completedFuture(submitBlockBallot(electionId, voterId, candidateIds)); }
    @Override public CompletableFuture<Boolean> setCandidateHeadItemBytesAsync(int electionId, int candidateId, byte[] data) { return CompletableFuture.completedFuture(setCandidateHeadItemBytes(electionId, candidateId, data)); }
    @Override public CompletableFuture<byte[]> getCandidateHeadItemBytesAsync(int electionId, int candidateId) { return CompletableFuture.completedFuture(getCandidateHeadItemBytes(electionId, candidateId)); }
    @Override public CompletableFuture<Boolean> markExportedAsync(int electionId, String actor) { return CompletableFuture.completedFuture(markExported(electionId, actor)); }
}
