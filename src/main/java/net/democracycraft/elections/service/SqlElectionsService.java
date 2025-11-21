package net.democracycraft.elections.service;

import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.events.*;
import net.democracycraft.elections.api.model.*;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.data.*;
import net.democracycraft.elections.database.DatabaseSchema;
import net.democracycraft.elections.database.MySQLManager;
import net.democracycraft.elections.database.entity.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;

/** SQL-backed ElectionsService with an in-memory mirror for fast reads. */
public class SqlElectionsService implements ElectionsService {

    private final Elections plugin;
    private final MySQLManager mysql;
    private final DatabaseSchema schema;
    private final MemoryElectionsService mem = new MemoryElectionsService();
    private final ExecutorService executor;

    public SqlElectionsService(Elections plugin, MySQLManager mysql, DatabaseSchema schema) {
        this.plugin = plugin;
        this.mysql = mysql;
        this.schema = schema;
        // Dedicated thread pool for async operations (avoid common pool contention)
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "DemocracyElections-IO");
            t.setDaemon(true);
            return t;
        };
        this.executor = Executors.newFixedThreadPool(Math.min(8, cores * 2), tf);
        reloadFromDatabase();
    }

    // --- Snapshot API ---
    @Override public List<Election> listElectionsSnapshot() { return mem.listElections(); }
    @Override public Optional<Election> getElectionSnapshot(int id) { return mem.getElection(id); }

    // --- helpers ---

    private static long nowEpochMillis() { return System.currentTimeMillis(); }

    private static TimeStampDto epochToTs(Long epoch) {
        if (epoch == null) return null;
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(epoch);
        return new TimeStampDto(new DateDto(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH)+1, c.get(Calendar.YEAR)), new TimeDto(c.get(Calendar.SECOND), c.get(Calendar.MINUTE), c.get(Calendar.HOUR_OF_DAY)));
    }

    private static Long tsToEpoch(TimeStampDto ts) {
        if (ts == null) return null;
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.set(Calendar.YEAR, ts.date().year());
        c.set(Calendar.MONTH, ts.date().month()-1);
        c.set(Calendar.DAY_OF_MONTH, ts.date().day());
        c.set(Calendar.HOUR_OF_DAY, ts.time().hour());
        c.set(Calendar.MINUTE, ts.time().minute());
        c.set(Calendar.SECOND, ts.time().second());
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static String tsToString(TimeStampDto ts) {
        if (ts == null) return null;
        DateDto d = ts.date();
        TimeDto t = ts.time();
        return String.format(Locale.ROOT, "%04d-%02d-%02d %02d:%02d:%02dZ", d.year(), d.month(), d.day(), t.hour(), t.minute(), t.second());
    }

    private static TimeDto normalize(TimeDto t) {
        return t == null ? null : new TimeDto(Math.max(0, Math.min(59, t.second())), Math.max(0, Math.min(59, t.minute())), Math.max(0, Math.min(23, t.hour())));
    }

    private static Long computeEndEpochFromDuration(long startEpoch, Integer days, Integer hour, Integer minute, Integer second) {
        if (days == null && hour == null && minute == null && second == null) return null;
        long ms = startEpoch;
        long d = (days == null ? 0 : Math.max(0, days));
        long h = (hour == null ? 0 : Math.max(0, hour));
        long m = (minute == null ? 0 : Math.max(0, minute));
        long s = (second == null ? 0 : Math.max(0, second));
        long add = d*24L*60L*60L*1000L + h*60L*60L*1000L + m*60L*1000L + s*1000L;
        return ms + add;
    }

    /** Returns the start epoch to compute duration from: last OPENED change or createdAt. */
    private long startEpochForElection(ElectionEntity row) {
        // Look for last OPENED status change
        List<StatusChangeEntity> changes = schema.statusChanges().findAllBy("electionId", row.id, "changedAtEpochMillis");
        long start = row.createdAtEpochMillis;
        for (StatusChangeEntity sc : changes) {
            if (sc != null && sc.type != null && sc.type.equalsIgnoreCase(StateChangeType.OPENED.name())) {
                start = sc.changedAtEpochMillis;
            }
        }
        return start;
    }

    private ElectionDto buildElectionDtoFromDb(ElectionEntity eRow) {
        int id = eRow.id;
        VotingSystem system = VotingSystem.valueOf(eRow.system);
        ElectionDto e = new ElectionDto(id, eRow.title, system, eRow.minimumVotes, null, epochToTs(eRow.createdAtEpochMillis));
        e.setStatus(ElectionStatus.valueOf(eRow.status));
        if (eRow.durationDays != null) e.setDurationDays(eRow.durationDays);
        if (eRow.durationHour != null || eRow.durationMinute != null || eRow.durationSecond != null) {
            int hour = eRow.durationHour == null ? 0 : Math.max(0, eRow.durationHour);
            int minute = eRow.durationMinute == null ? 0 : Math.max(0, eRow.durationMinute);
            int second = eRow.durationSecond == null ? 0 : Math.max(0, eRow.durationSecond);
            e.setDurationTime(new TimeDto(second, minute, hour));
        }
        BallotMode mode = BallotMode.MANUAL;
        if (eRow.ballotMode != null) {
            try { mode = BallotMode.valueOf(eRow.ballotMode); } catch (IllegalArgumentException ignored) {}
        }
        e.setBallotMode(mode);
        ElectionRequirementsEntity req = schema.electionRequirements().findBy("electionId", id);
        List<String> perms = schema.requirementPermissions().findAllBy("electionId", id, "permission").stream().map(p -> p.permission).collect(Collectors.toList());
        if ((req != null && req.minActivePlaytimeMinutes != null) || !perms.isEmpty()) {
            long minutes = (req == null || req.minActivePlaytimeMinutes == null) ? 0L : req.minActivePlaytimeMinutes;
            e.setRequirements(new RequirementsDto(perms, minutes));
        }
        for (CandidateEntity cRow : schema.candidates().findAllBy("electionId", id, "id")) {
            CandidateDto cDto = new CandidateDto(cRow.id, cRow.name);
            CandidateHeadItemEntity head = schema.candidateHeadItems().findBy("candidateId", cRow.id);
            if (head != null) cDto.setHeadItemBytes(head.headItemBytes);
            e.addCandidate(cDto);
        }
        for (PollEntity p : schema.polls().findAllBy("electionId", id, null)) {
            e.addPoll(new PollDto(p.world, p.x, p.y, p.z));
        }
        for (VoterEntity v : schema.voters().findAllBy("electionId", id, "id")) {
            e.addVoter(new VoterDto(v.id, v.name));
        }
        for (BallotEntity b : schema.ballots().findAllBy("electionId", id, "id")) {
            BallotDto bDto = new BallotDto(b.id, id, b.voterId);
            if (b.submittedAtEpochMillis != null) bDto.setSubmittedAt(epochToTs(b.submittedAtEpochMillis));
            bDto.clearSelections();
            for (BallotSelectionEntity s : schema.ballotSelections().findAllBy("ballotId", b.id, "position")) {
                bDto.addSelection(s.candidateId);
            }
            e.appendBallot(bDto);
        }
        List<StatusChangeEntity> scs = schema.statusChanges().findAllBy("electionId", id, "changedAtEpochMillis");
        for (StatusChangeEntity sc : scs) {
            e.addStatusChange(new StatusChangeDto(epochToTs(sc.changedAtEpochMillis), StateChangeType.valueOf(sc.type), sc.actor, sc.details));
        }
        long start = startEpochForElection(eRow);
        Long end = computeEndEpochFromDuration(start, eRow.durationDays, eRow.durationHour, eRow.durationMinute, eRow.durationSecond);
        e.setClosesAt(epochToTs(end));
        return e;
    }

    private void refreshElection(int id) {
        ElectionEntity row = schema.elections().findBy("id", id);
        if (row == null) {
            mem.removeElectionById(id);
            return;
        }
        ElectionDto dto = buildElectionDtoFromDb(row);
        mem.upsertElection(dto);
    }

    private void reloadFromDatabase() {
        List<ElectionDto> list = new ArrayList<>();
        // Elections first
        for (ElectionEntity eRow : schema.elections().getAll(null)) {
            ElectionDto e = buildElectionDtoFromDb(eRow);
            list.add(e);
        }
        mem.loadSnapshot(list);
    }

    private void logChange(int electionId, StateChangeType type, String actor, String details) {
        StatusChangeEntity row = new StatusChangeEntity();
        row.electionId = electionId;
        row.changedAtEpochMillis = nowEpochMillis();
        row.type = type.name();
        row.actor = actor;
        row.details = details;
        schema.statusChanges().insertNonPkSync(row);
    }

    /** Closes any OPEN election whose duration has expired. */
    public void runAutoCloseSweep() {
        long now = nowEpochMillis();
        List<Integer> toClose = new ArrayList<>();
        for (Election e : mem.listElections()) {
            if (e.getStatus() != ElectionStatus.OPEN) continue;
            // fetch backing row to compute start + duration
            ElectionEntity row = schema.elections().findBy("id", e.getId());
            if (row == null) continue;
            long start = startEpochForElection(row);
            Long end = computeEndEpochFromDuration(start, row.durationDays, row.durationHour, row.durationMinute, row.durationSecond);
            if (end != null && now >= end) toClose.add(e.getId());
        }
        for (Integer id : toClose) {
            closeElection(id, "system");
        }
    }

    /**
     * Purges elections marked as DELETED whose last DELETED status change is older than the given retention days.
     * Deletes dependent rows in a safe order before removing the election row.
     * @param retentionDays number of days to keep DELETED elections before purging (non-negative)
     */
    public void runDeletedPurgeSweep(int retentionDays) {
        int rd = Math.max(0, retentionDays);
        long now = nowEpochMillis();
        long threshold = now - rd * 24L * 60L * 60L * 1000L;
        List<Integer> toPurge = new ArrayList<>();
        for (ElectionEntity e : schema.elections().getAll(null)) {
            if (e == null) continue;
            if (!net.democracycraft.elections.data.ElectionStatus.DELETED.name().equals(e.status)) continue;
            // find last DELETED change time
            long deletedAt = -1L;
            List<StatusChangeEntity> changes = schema.statusChanges().findAllBy("electionId", e.id, "changedAtEpochMillis");
            for (StatusChangeEntity sc : changes) {
                if (sc != null && sc.type != null && sc.type.equalsIgnoreCase(net.democracycraft.elections.data.StateChangeType.DELETED.name())) {
                    deletedAt = Math.max(deletedAt, sc.changedAtEpochMillis);
                }
            }
            if (deletedAt > 0 && deletedAt <= threshold) toPurge.add(e.id);
        }
        if (toPurge.isEmpty()) return;
        for (Integer id : toPurge) {
            try {
                // ballots -> selections (CASCADE)
                Map<String,Object> where = new HashMap<>();
                where.put("electionId", id);
                schema.ballots().deleteWhereSync(where);
                // voters
                schema.voters().deleteWhereSync(where);
                // polls
                schema.polls().deleteWhereSync(where);
                // candidates -> head items (CASCADE)
                schema.candidates().deleteWhereSync(where);
                // requirement permissions
                schema.requirementPermissions().deleteWhereSync(where);
                // requirements 1:1
                Map<String,Object> reqWhere = new HashMap<>();
                reqWhere.put("electionId", id);
                schema.electionRequirements().deleteWhereSync(reqWhere);
                // status changes
                schema.statusChanges().deleteWhereSync(where);
                // election row
                schema.elections().deleteById(id);
            } catch (Exception ex) {
                // skip on error; continue with others
            }
        }
        reloadFromDatabase();
    }

    // --- Internal synchronous helpers (not part of API) ---

    public Election createElection(String title, VotingSystem system, int minimumVotes, RequirementsDto requirements, String actor) {
        ElectionEntity row = new ElectionEntity();
        row.title = title;
        row.status = ElectionStatus.CLOSED.name();
        row.system = system.name();
        row.minimumVotes = Math.max(1, minimumVotes);
        row.createdAtEpochMillis = nowEpochMillis();
        row.ballotMode = BallotMode.MANUAL.name();
        Integer id = schema.elections().insertReturningIntKey(row);
        if (id == null) throw new IllegalStateException("No key generated");
        if (requirements != null) setRequirements(id, requirements, actor);
        logChange(id, StateChangeType.CREATED, actor, "title="+title+",system="+system+",min="+Math.max(1, minimumVotes));
        refreshElection(id);
        return getElection(id).orElseThrow();
    }

    public Optional<Election> getElection(int id) { return mem.getElection(id); }
    public List<Election> listElections() { return mem.listElections(); }

    public boolean deleteElection(int id, String actor) {
        ElectionEntity e = schema.elections().findBy("id", id);
        if (e == null) return false;
        ElectionStatus current = ElectionStatus.valueOf(e.status);
        if (current == ElectionStatus.DELETED) return false;
        e.status = ElectionStatus.DELETED.name();
        schema.elections().insertOrUpdateSync(e);
        logChange(id, StateChangeType.DELETED, actor, null);
        refreshElection(id);
        return true;
    }

    public boolean setTitle(int electionId, String title, String actor) {
        ElectionEntity e = schema.elections().findBy("id", electionId);
        if (e == null) return false;
        e.title = title==null?"":title;
        schema.elections().insertOrUpdateSync(e);
        logChange(electionId, StateChangeType.TITLE_CHANGED, actor, "new="+(title==null?"":title));
        refreshElection(electionId);
        return true;
    }

    public boolean setSystem(int electionId, VotingSystem system, String actor) {
        ElectionEntity e = schema.elections().findBy("id", electionId);
        if (e == null) return false;
        e.system = system.name();
        schema.elections().insertOrUpdateSync(e);
        logChange(electionId, StateChangeType.SYSTEM_CHANGED, actor, "new="+system.name());
        refreshElection(electionId);
        return true;
    }

    public boolean setMinimumVotes(int electionId, int minimum, String actor) {
        int eff = Math.max(1, Math.max(0, minimum));
        ElectionEntity e = schema.elections().findBy("id", electionId);
        if (e == null) return false;
        e.minimumVotes = eff;
        schema.elections().insertOrUpdateSync(e);
        logChange(electionId, StateChangeType.MINIMUM_CHANGED, actor, "new="+eff);
        refreshElection(electionId);
        return true;
    }

    public boolean setRequirements(int electionId, RequirementsDto requirements, String actor) {
        // upsert main row
        ElectionRequirementsEntity req = new ElectionRequirementsEntity();
        req.electionId = electionId;
        req.minActivePlaytimeMinutes = (requirements == null) ? null : requirements.minActivePlaytimeMinutes();
        schema.electionRequirements().insertOrUpdateSync(req);
        // replace permissions
        Map<String, Object> where = new HashMap<>();
        where.put("electionId", electionId);
        schema.requirementPermissions().deleteWhereSync(where);
        if (requirements != null && requirements.permissions() != null) {
            for (String p : requirements.permissions()) {
                ElectionRequirementPermissionEntity rp = new ElectionRequirementPermissionEntity();
                rp.electionId = electionId; rp.permission = p;
                schema.requirementPermissions().insertNonPkSync(rp);
            }
        }
        logChange(electionId, StateChangeType.REQUIREMENTS_CHANGED, actor, "perms=" + (requirements==null?0:requirements.permissions().size()) + ",minutes=" + (requirements==null?0:requirements.minActivePlaytimeMinutes()));
        refreshElection(electionId);
        return true;
    }

    public boolean openElection(int electionId, String actor) {
        ElectionEntity e = schema.elections().findBy("id", electionId);
        if (e == null) return false;
        ElectionStatus current = ElectionStatus.valueOf(e.status);
        if (current == ElectionStatus.DELETED) return false; // cannot open deleted
        if (current == ElectionStatus.OPEN) return true; // idempotent
        if (current != ElectionStatus.CLOSED) return false; // only CLOSED -> OPEN
        e.status = ElectionStatus.OPEN.name();
        schema.elections().insertOrUpdateSync(e);
        logChange(electionId, StateChangeType.OPENED, actor, null);
        refreshElection(electionId);
        return true;
    }

    public boolean closeElection(int electionId, String actor) {
        ElectionEntity e = schema.elections().findBy("id", electionId);
        if (e == null) return false;
        ElectionStatus current = ElectionStatus.valueOf(e.status);
        if (current == ElectionStatus.CLOSED) return true; // idempotent
        if (current != ElectionStatus.OPEN) return false; // only OPEN -> CLOSED
        e.status = ElectionStatus.CLOSED.name();
        schema.elections().insertOrUpdateSync(e);
        logChange(electionId, StateChangeType.CLOSED, actor, null);
        refreshElection(electionId);
        return true;
    }

    public boolean setClosesAt(int electionId, TimeStampDto closesAt, String actor) {
        // Deprecated: ignore explicit closesAt and rely solely on duration.
        ElectionEntity e = schema.elections().findBy("id", electionId);
        if (e == null) return false;
        // no DB write required beyond potential audit
        logChange(electionId, closesAt==null? StateChangeType.CLOSES_AT_CLEARED : StateChangeType.CLOSES_AT_CHANGED, actor, "ignored; duration-based");
        refreshElection(electionId);
        return true;
    }

    public boolean setDuration(int electionId, Integer days, TimeDto time, String actor) {
        TimeDto t = normalize(time);
        ElectionEntity e = schema.elections().findBy("id", electionId);
        if (e == null) return false;
        e.durationDays = days;
        e.durationHour = (t==null? null : t.hour());
        e.durationMinute = (t==null? null : t.minute());
        e.durationSecond = (t==null? null : t.second());
        schema.elections().insertOrUpdateSync(e);
        logChange(electionId, (days==null && t==null)? StateChangeType.DURATION_CLEARED : StateChangeType.DURATION_CHANGED, actor, null);
        refreshElection(electionId);
        return true;
    }

    /** Sets per-election ballot UI mode and records a status change. */
    public boolean setBallotMode(int electionId, BallotMode mode, String actor) {
        ElectionEntity e = schema.elections().findBy("id", electionId);
        if (e == null) return false;
        BallotMode newMode = (mode == null ? BallotMode.MANUAL : mode);
        BallotMode current = (e.ballotMode == null ? BallotMode.MANUAL : BallotMode.valueOf(e.ballotMode));
        if (current == newMode) return true;
        e.ballotMode = newMode.name();
        schema.elections().insertOrUpdateSync(e);
        logChange(electionId, StateChangeType.BALLOT_MODE_CHANGED, actor, "old=" + current + ",new=" + newMode);
        refreshElection(electionId);
        return true;
    }

    public Optional<Candidate> addCandidate(int electionId, String name, String actor) {
        // Prevent duplicate candidate by (electionId, name)
        Map<String, Object> where = new HashMap<>();
        where.put("electionId", electionId);
        where.put("name", name);
        if (!schema.candidates().findAllByMany(where, "id").isEmpty()) {
            return Optional.empty();
        }
        CandidateEntity row = new CandidateEntity();
        row.electionId = electionId;
        row.name = name;
        Integer id = schema.candidates().insertReturningIntKey(row);
        if (id == null) return Optional.empty();
        logChange(electionId, StateChangeType.CANDIDATE_ADDED, actor, "id="+id+",name="+name);
        refreshElection(electionId);
        return mem.getElection(electionId).flatMap(e -> e.getCandidates().stream().filter(c -> c.getId()==id).findFirst());
    }

    public boolean removeCandidate(int electionId, int candidateId, String actor) {
        Map<String, Object> where = new HashMap<>();
        where.put("id", candidateId);
        where.put("electionId", electionId);
        int affected = schema.candidates().deleteWhereSync(where);
        if (affected > 0) logChange(electionId, StateChangeType.CANDIDATE_REMOVED, actor, "id="+candidateId);
        refreshElection(electionId);
        return affected > 0;
    }

    public Optional<Poll> addPoll(int electionId, String world, int x, int y, int z, String actor) {
        // Prevent duplicate poll globally (across all elections) at same coordinates
        Map<String, Object> globalWhere = new HashMap<>();
        globalWhere.put("world", world);
        globalWhere.put("x", x);
        globalWhere.put("y", y);
        globalWhere.put("z", z);
        if (!schema.polls().findAllByMany(globalWhere, null).isEmpty()) {
            return Optional.empty();
        }
        // Prevent duplicate poll by (electionId, world, x, y, z)
        Map<String, Object> where = new HashMap<>();
        where.put("electionId", electionId);
        where.put("world", world);
        where.put("x", x);
        where.put("y", y);
        where.put("z", z);
        if (!schema.polls().findAllByMany(where, null).isEmpty()) {
            return Optional.empty();
        }
        PollEntity row = new PollEntity();
        row.electionId = electionId; row.world = world; row.x = x; row.y = y; row.z = z;
        boolean ok = schema.polls().insertNonPkSync(row);
        if (!ok) return Optional.empty();
        logChange(electionId, StateChangeType.POLL_ADDED, actor, "world="+world+",x="+x+",y="+y+",z="+z);
        refreshElection(electionId);
        return mem.getElection(electionId).flatMap(e -> e.getPolls().stream().filter(p -> p.getWorld().equalsIgnoreCase(world) && p.getX()==x && p.getY()==y && p.getZ()==z).findFirst());
    }

    public boolean removePoll(int electionId, String world, int x, int y, int z, String actor) {
        Map<String, Object> where = new HashMap<>();
        where.put("electionId", electionId);
        where.put("world", world);
        where.put("x", x);
        where.put("y", y);
        where.put("z", z);
        int affected = schema.polls().deleteWhereSync(where);
        if (affected > 0) logChange(electionId, StateChangeType.POLL_REMOVED, actor, "world="+world+",x="+x+",y="+y+",z="+z);
        refreshElection(electionId);
        return affected > 0;
    }

    public Voter registerVoter(int electionId, String name) {
        // Check existing by unique (electionId, name)
        Map<String, Object> where = new HashMap<>();
        where.put("electionId", electionId);
        where.put("name", name);
        List<VoterEntity> existing = schema.voters().findAllByMany(where, "id");
        int id;
        if (!existing.isEmpty()) {
            id = existing.getFirst().id;
        } else {
            VoterEntity row = new VoterEntity();
            row.electionId = electionId;
            row.name = name;
            try {
                id = schema.voters().insertReturningIntKey(row);
            } catch (RuntimeException ex) {
                // Fallback in case of race: fetch existing id
                existing = schema.voters().findAllByMany(where, "id");
                if (!existing.isEmpty()) id = existing.getFirst().id;
                else throw ex;
            }
        }
        refreshElection(electionId);
        return mem.getVoterById(electionId, id).orElseGet(() -> mem.registerVoter(electionId, name));
    }

    public Optional<Voter> getVoterById(int electionId, int voterId) { return mem.getVoterById(electionId, voterId); }
    public List<Voter> listVoters(int electionId) { return mem.listVoters(electionId); }

    public boolean submitPreferentialBallot(int electionId, int voterId, List<Integer> orderedCandidateIds) {
        // Idempotency and race safety: check DB first
        Map<String, Object> where = new HashMap<>();
        where.put("electionId", electionId);
        where.put("voterId", voterId);
        if (!schema.ballots().findAllByMany(where, "id").isEmpty()) return false;
        if (!mem.submitPreferentialBallot(electionId, voterId, orderedCandidateIds)) return false;
        // persist
        try {
            BallotEntity b = new BallotEntity();
            b.electionId = electionId; b.voterId = voterId; b.submittedAtEpochMillis = nowEpochMillis();
            Integer ballotId = schema.ballots().insertReturningIntKey(b);
            if (ballotId != null) {
                int pos = 0;
                for (Integer c : orderedCandidateIds) {
                    if (c == null) continue;
                    BallotSelectionEntity s = new BallotSelectionEntity();
                    s.ballotId = ballotId; s.candidateId = c; s.position = ++pos;
                    schema.ballotSelections().insertNonPkSync(s);
                }
            }
        } catch (RuntimeException ex) {
            // Likely unique constraint under race; revert to DB state
            refreshElection(electionId);
            return false;
        }
        refreshElection(electionId);
        return true;
    }

    public boolean submitBlockBallot(int electionId, int voterId, List<Integer> candidateIds) {
        // Idempotency and race safety: check DB first
        Map<String, Object> where = new HashMap<>();
        where.put("electionId", electionId);
        where.put("voterId", voterId);
        if (!schema.ballots().findAllByMany(where, "id").isEmpty()) return false;
        if (!mem.submitBlockBallot(electionId, voterId, candidateIds)) return false;
        try {
            BallotEntity ballotEntity = new BallotEntity();
            ballotEntity.electionId = electionId; ballotEntity.voterId = voterId; ballotEntity.submittedAtEpochMillis = nowEpochMillis();
            Integer ballotId = schema.ballots().insertReturningIntKey(ballotEntity);
            if (ballotId != null) {
                int pos = 0;
                for (Integer id : candidateIds) {
                    if (id == null) continue;
                    BallotSelectionEntity ballotSelectionEntity = new BallotSelectionEntity();
                    ballotSelectionEntity.ballotId = ballotId; ballotSelectionEntity.candidateId = id; ballotSelectionEntity.position = ++pos;
                    schema.ballotSelections().insertNonPkSync(ballotSelectionEntity);
                }
            }
        } catch (RuntimeException ex) {
            refreshElection(electionId);
            return false;
        }
        refreshElection(electionId);
        return true;
    }

    public boolean setCandidateHeadItemBytes(int electionId, int candidateId, byte[] data) {
        CandidateHeadItemEntity row = new CandidateHeadItemEntity();
        row.candidateId = candidateId; row.headItemBytes = data;
        schema.candidateHeadItems().insertOrUpdateSync(row);
        refreshElection(electionId);
        return true;
    }

    public byte[] getCandidateHeadItemBytes(int electionId, int candidateId) {
        CandidateHeadItemEntity row = schema.candidateHeadItems().findBy("candidateId", candidateId);
        return row == null ? null : row.headItemBytes;
    }

    public byte[] getCandidateHeadItemBytesSnapshot(int electionId, int candidateId) {
        return mem.getCandidateHeadItemBytes(electionId, candidateId);
    }

    public boolean markExported(int electionId, String actor) {
        logChange(electionId, StateChangeType.EXPORTED, actor, null);
        refreshElection(electionId);
        return true;
    }

    // --- ElectionsService (async wrappers) ---

    @Override public CompletableFuture<Election> createElectionAsync(String title, VotingSystem system, int minimumVotes, RequirementsDto requirements, String actor) {
        return CompletableFuture.supplyAsync(() -> {
            Election e = createElection(title, system, minimumVotes, requirements, actor);
            runOnMain(() -> callEvent(new ElectionCreatedEvent(e.getId(), actor)));
            return e;
        }, executor);
    }

    @Override public CompletableFuture<Optional<Election>> getElectionAsync(int id) {
        return CompletableFuture.supplyAsync(() -> getElection(id), executor);
    }

    @Override public CompletableFuture<List<Election>> listElectionsAsync() {
        return CompletableFuture.supplyAsync(this::listElections, executor);
    }

    @Override public CompletableFuture<Boolean> deleteElectionAsync(int id, String actor) {
        return CompletableFuture.supplyAsync(() -> deleteElection(id, actor), executor);
    }

    @Override public CompletableFuture<Boolean> setTitleAsync(int electionId, String title, String actor) {
        return CompletableFuture.supplyAsync(() -> setTitle(electionId, title, actor), executor);
    }

    @Override public CompletableFuture<Boolean> setSystemAsync(int electionId, VotingSystem system, String actor) {
        return CompletableFuture.supplyAsync(() -> setSystem(electionId, system, actor), executor);
    }

    @Override public CompletableFuture<Boolean> setMinimumVotesAsync(int electionId, int minimum, String actor) {
        return CompletableFuture.supplyAsync(() -> setMinimumVotes(electionId, minimum, actor), executor);
    }

    @Override public CompletableFuture<Boolean> setRequirementsAsync(int electionId, RequirementsDto requirements, String actor) {
        return CompletableFuture.supplyAsync(() -> setRequirements(electionId, requirements, actor), executor);
    }

    @Override public CompletableFuture<Boolean> openElectionAsync(int electionId, String actor) {
        return CompletableFuture.supplyAsync(() -> {
            boolean ok = openElection(electionId, actor);
            if (ok) runOnMain(() -> callEvent(new ElectionOpenedEvent(electionId, actor)));
            return ok;
        }, executor);
    }

    @Override public CompletableFuture<Boolean> closeElectionAsync(int electionId, String actor) {
        return CompletableFuture.supplyAsync(() -> {
            boolean ok = closeElection(electionId, actor);
            if (ok) runOnMain(() -> callEvent(new ElectionClosedEvent(electionId, actor)));
            return ok;
        }, executor);
    }

    @Override public CompletableFuture<Boolean> setClosesAtAsync(int electionId, TimeStampDto closesAt, String actor) {
        return CompletableFuture.supplyAsync(() -> setClosesAt(electionId, closesAt, actor), executor);
    }

    @Override public CompletableFuture<Boolean> setDurationAsync(int electionId, Integer days, TimeDto time, String actor) {
        return CompletableFuture.supplyAsync(() -> setDuration(electionId, days, time, actor), executor);
    }

    @Override public CompletableFuture<Boolean> setBallotModeAsync(int electionId, BallotMode mode, String actor) {
        return CompletableFuture.supplyAsync(() -> setBallotMode(electionId, mode, actor), executor);
    }

    @Override public CompletableFuture<Optional<Candidate>> addCandidateAsync(int electionId, String name, String actor) {
        return CompletableFuture.supplyAsync(() -> addCandidate(electionId, name, actor), executor);
    }

    @Override public CompletableFuture<Boolean> removeCandidateAsync(int electionId, int candidateId, String actor) {
        return CompletableFuture.supplyAsync(() -> removeCandidate(electionId, candidateId, actor), executor);
    }

    @Override public CompletableFuture<Optional<Poll>> addPollAsync(int electionId, String world, int x, int y, int z, String actor) {
        return CompletableFuture.supplyAsync(() -> addPoll(electionId, world, x, y, z, actor), executor);
    }

    @Override public CompletableFuture<Boolean> removePollAsync(int electionId, String world, int x, int y, int z, String actor) {
        return CompletableFuture.supplyAsync(() -> removePoll(electionId, world, x, y, z, actor), executor);
    }

    @Override public CompletableFuture<Voter> registerVoterAsync(int electionId, String name) {
        return CompletableFuture.supplyAsync(() -> registerVoter(electionId, name), executor);
    }

    @Override public CompletableFuture<Optional<Voter>> getVoterByIdAsync(int electionId, int voterId) {
        return CompletableFuture.supplyAsync(() -> getVoterById(electionId, voterId), executor);
    }

    @Override public CompletableFuture<List<Voter>> listVotersAsync(int electionId) {
        return CompletableFuture.supplyAsync(() -> listVoters(electionId), executor);
    }

    @Override public CompletableFuture<Boolean> submitPreferentialBallotAsync(int electionId, int voterId, List<Integer> orderedCandidateIds) {
        return CompletableFuture.supplyAsync(() -> {
            boolean ok = submitPreferentialBallot(electionId, voterId, orderedCandidateIds);
            if (ok) runOnMain(() -> callEvent(new VoteSubmittedEvent(electionId, voterId)));
            return ok;
        }, executor);
    }

    @Override public CompletableFuture<Boolean> submitBlockBallotAsync(int electionId, int voterId, List<Integer> candidateIds) {
        return CompletableFuture.supplyAsync(() -> {
            boolean ok = submitBlockBallot(electionId, voterId, candidateIds);
            if (ok) runOnMain(() -> callEvent(new VoteSubmittedEvent(electionId, voterId)));
            return ok;
        }, executor);
    }

    @Override public CompletableFuture<Boolean> setCandidateHeadItemBytesAsync(int electionId, int candidateId, byte[] data) {
        return CompletableFuture.supplyAsync(() -> setCandidateHeadItemBytes(electionId, candidateId, data), executor);
    }

    @Override public CompletableFuture<byte[]> getCandidateHeadItemBytesAsync(int electionId, int candidateId) {
        return CompletableFuture.supplyAsync(() -> getCandidateHeadItemBytes(electionId, candidateId), executor);
    }

    @Override public CompletableFuture<Boolean> markExportedAsync(int electionId, String actor) {
        return CompletableFuture.supplyAsync(() -> {
            boolean ok = markExported(electionId, actor);
            if (ok) runOnMain(() -> callEvent(new ElectionExportedEvent(electionId, actor)));
            return ok;
        }, executor);
    }

    public void shutdown() {
        try {
            executor.shutdownNow();
        } catch (Exception ignored) {}
    }

    private void runOnMain(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }
    private void callEvent(org.bukkit.event.Event e) {
        Bukkit.getPluginManager().callEvent(e);
    }
}
