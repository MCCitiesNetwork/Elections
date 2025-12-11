package net.democracycraft.elections.src.database;

import net.democracycraft.elections.src.database.entity.*;
import net.democracycraft.elections.src.database.table.AutoTable;

/**
 * Creates database tables and exposes AutoTable helpers for ORM-style access.
 */
public class DatabaseSchema {
    private final MySQLManager mysql;

    // Exposed tables
    private final AutoTable<ElectionEntity> elections;
    private final AutoTable<ElectionRequirementsEntity> electionRequirements;
    private final AutoTable<ElectionRequirementPermissionEntity> requirementPermissions;
    private final AutoTable<CandidateEntity> candidates;
    private final AutoTable<CandidateHeadItemEntity> candidateHeadItems;
    private final AutoTable<PollEntity> polls;
    private final AutoTable<VoterEntity> voters;
    private final AutoTable<BallotEntity> ballots;
    private final AutoTable<BallotSelectionEntity> ballotSelections;
    private final AutoTable<StatusChangeEntity> statusChanges;

    public DatabaseSchema(MySQLManager mysql) {
        this.mysql = mysql;
        this.elections = new AutoTable<>(mysql, ElectionEntity.class, "elections", "id");
        this.electionRequirements = new AutoTable<>(mysql, ElectionRequirementsEntity.class, "election_requirements", "electionId");
        this.requirementPermissions = new AutoTable<>(mysql, ElectionRequirementPermissionEntity.class, "election_requirement_permissions");
        this.candidates = new AutoTable<>(mysql, CandidateEntity.class, "candidates", "id");
        this.candidateHeadItems = new AutoTable<>(mysql, CandidateHeadItemEntity.class, "candidate_head_item", "candidateId");
        this.polls = new AutoTable<>(mysql, PollEntity.class, "polls");
        this.voters = new AutoTable<>(mysql, VoterEntity.class, "voters", "id");
        this.ballots = new AutoTable<>(mysql, BallotEntity.class, "ballots", "id");
        this.ballotSelections = new AutoTable<>(mysql, BallotSelectionEntity.class, "ballot_selections");
        this.statusChanges = new AutoTable<>(mysql, StatusChangeEntity.class, "election_status_changes", "id");
    }

    /** Creates all tables and adds necessary indexes/constraints. */
    public void createAll() {
        // Base tables via AutoTable
        elections.createTable();
        electionRequirements.createTable();
        requirementPermissions.createTable();
        candidates.createTable();
        candidateHeadItems.createTable();
        polls.createTable();
        voters.createTable();
        ballots.createTable();
        ballotSelections.createTable();
        statusChanges.createTable();

        // Add unique constraints and FKs (ignore if existing)
        mysql.withConnection(conn -> {
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `elections` ADD INDEX `idx_elections_status` (`status`)");
            } catch (Exception ignored) {}
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `elections` ADD INDEX `idx_elections_system` (`system`)");
            } catch (Exception ignored) {}
            // Add ballotMode column for per-election voting mode (ignore if exists)
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `elections` ADD COLUMN `ballotMode` VARCHAR(32) NULL AFTER `durationSecond`");
            } catch (Exception ignored) {}

            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `candidates` ADD COLUMN `party` VARCHAR(128) NULL AFTER `name`");
            } catch (Exception ignored) {}

            // requirements 1:1
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `election_requirements` ADD CONSTRAINT `fk_req_election` FOREIGN KEY (`electionId`) REFERENCES `elections`(`id`) ON DELETE RESTRICT ON UPDATE RESTRICT");
            } catch (Exception ignored) {}

            // requirement permissions unique + fk
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `election_requirement_permissions` ADD UNIQUE `uq_reqperm` (`electionId`,`permission`)");
            } catch (Exception ignored) {}
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `election_requirement_permissions` ADD CONSTRAINT `fk_reqperm_election` FOREIGN KEY (`electionId`) REFERENCES `elections`(`id`) ON DELETE RESTRICT ON UPDATE RESTRICT");
            } catch (Exception ignored) {}

            // candidates unique + fk
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `candidates` ADD UNIQUE `uq_cand_name` (`electionId`,`name`)");
            } catch (Exception ignored) {}
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `candidates` ADD CONSTRAINT `fk_cand_election` FOREIGN KEY (`electionId`) REFERENCES `elections`(`id`) ON DELETE RESTRICT ON UPDATE RESTRICT");
            } catch (Exception ignored) {}

            // candidate head item 1:1
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `candidate_head_item` ADD CONSTRAINT `fk_head_cand` FOREIGN KEY (`candidateId`) REFERENCES `candidates`(`id`) ON DELETE CASCADE ON UPDATE RESTRICT");
            } catch (Exception ignored) {}

            // polls unique + fk
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `polls` ADD UNIQUE `uq_poll_loc` (`electionId`,`world`,`x`,`y`,`z`)");
            } catch (Exception ignored) {}
            // global unique to forbid same (world,x,y,z) across elections
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `polls` ADD UNIQUE `uq_poll_global` (`world`,`x`,`y`,`z`)");
            } catch (Exception ignored) {}
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `polls` ADD CONSTRAINT `fk_poll_election` FOREIGN KEY (`electionId`) REFERENCES `elections`(`id`) ON DELETE RESTRICT ON UPDATE RESTRICT");
            } catch (Exception ignored) {}

            // voters unique + fk
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `voters` ADD UNIQUE `uq_voter_name` (`electionId`,`name`)");
            } catch (Exception ignored) {}
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `voters` ADD CONSTRAINT `fk_voter_election` FOREIGN KEY (`electionId`) REFERENCES `elections`(`id`) ON DELETE RESTRICT ON UPDATE RESTRICT");
            } catch (Exception ignored) {}

            // ballots unique + fks
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `ballots` ADD UNIQUE `uq_ballot_once` (`electionId`,`voterId`)");
            } catch (Exception ignored) {}
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `ballots` ADD CONSTRAINT `fk_ballot_election` FOREIGN KEY (`electionId`) REFERENCES `elections`(`id`) ON DELETE RESTRICT ON UPDATE RESTRICT");
            } catch (Exception ignored) {}
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `ballots` ADD CONSTRAINT `fk_ballot_voter` FOREIGN KEY (`voterId`) REFERENCES `voters`(`id`) ON DELETE RESTRICT ON UPDATE RESTRICT");
            } catch (Exception ignored) {}

            // ballot selections uniques + fks
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `ballot_selections` ADD UNIQUE `uq_sel_candidate` (`ballotId`,`candidateId`)");
            } catch (Exception ignored) {}
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `ballot_selections` ADD UNIQUE `uq_sel_position` (`ballotId`,`position`)");
            } catch (Exception ignored) {}
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `ballot_selections` ADD CONSTRAINT `fk_sel_ballot` FOREIGN KEY (`ballotId`) REFERENCES `ballots`(`id`) ON DELETE CASCADE ON UPDATE RESTRICT");
            } catch (Exception ignored) {}
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `ballot_selections` ADD CONSTRAINT `fk_sel_candidate` FOREIGN KEY (`candidateId`) REFERENCES `candidates`(`id`) ON DELETE RESTRICT ON UPDATE RESTRICT");
            } catch (Exception ignored) {}

            // status changes fk + index
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `election_status_changes` ADD INDEX `idx_sc_election_time` (`electionId`,`changedAtEpochMillis`)");
            } catch (Exception ignored) {}
            try (var st = conn.createStatement()) {
                st.execute("ALTER TABLE `election_status_changes` ADD CONSTRAINT `fk_sc_election` FOREIGN KEY (`electionId`) REFERENCES `elections`(`id`) ON DELETE RESTRICT ON UPDATE RESTRICT");
            } catch (Exception ignored) {}

            // Ensure AUTO_INCREMENT on integer PK 'id' for existing installs (ignore if already correct)
            try (var st = conn.createStatement()) { st.execute("ALTER TABLE `elections` MODIFY `id` INT NOT NULL AUTO_INCREMENT"); } catch (Exception ignored) {}
            try (var st = conn.createStatement()) { st.execute("ALTER TABLE `candidates` MODIFY `id` INT NOT NULL AUTO_INCREMENT"); } catch (Exception ignored) {}
            try (var st = conn.createStatement()) { st.execute("ALTER TABLE `voters` MODIFY `id` INT NOT NULL AUTO_INCREMENT"); } catch (Exception ignored) {}
            try (var st = conn.createStatement()) { st.execute("ALTER TABLE `ballots` MODIFY `id` INT NOT NULL AUTO_INCREMENT"); } catch (Exception ignored) {}
            try (var st = conn.createStatement()) { st.execute("ALTER TABLE `election_status_changes` MODIFY `id` INT NOT NULL AUTO_INCREMENT"); } catch (Exception ignored) {}

            return null;
        });
    }

    // Getters for tables
    public AutoTable<ElectionEntity> elections() { return elections; }
    public AutoTable<ElectionRequirementsEntity> electionRequirements() { return electionRequirements; }
    public AutoTable<ElectionRequirementPermissionEntity> requirementPermissions() { return requirementPermissions; }
    public AutoTable<CandidateEntity> candidates() { return candidates; }
    public AutoTable<CandidateHeadItemEntity> candidateHeadItems() { return candidateHeadItems; }
    public AutoTable<PollEntity> polls() { return polls; }
    public AutoTable<VoterEntity> voters() { return voters; }
    public AutoTable<BallotEntity> ballots() { return ballots; }
    public AutoTable<BallotSelectionEntity> ballotSelections() { return ballotSelections; }
    public AutoTable<StatusChangeEntity> statusChanges() { return statusChanges; }
}
