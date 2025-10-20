package net.democracycraft.democracyelections.api.model;

import net.democracycraft.democracyelections.data.TimeStampDto;

import java.util.List;

public interface Vote {
    int getId();
    int getElectionId();
    int getVoterId();
    List<Integer> getSelections(); // candidate ids
    boolean isSubmitted();
    TimeStampDto getSubmittedAt();
}

