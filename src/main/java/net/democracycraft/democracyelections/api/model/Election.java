package net.democracycraft.democracyelections.api.model;

import net.democracycraft.democracyelections.data.*;

import java.util.List;

public interface Election {
    int getId();
    String getTitle();
    ElectionStatus getStatus();
    VotingSystem getSystem();
    int getMinimumVotes();
    RequirementsDto getRequirements();

    List<Candidate> getCandidates();
    List<Poll> getPolls();
    List<Vote> getBallots();

    int getVoterCount();

    TimeStampDto getClosesAt();
    TimeStampDto getCreatedAt();

    Integer getDurationDays();
    TimeDto getDurationTime();

    List<StatusChangeDto> getStatusChanges();
}
