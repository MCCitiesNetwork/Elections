package net.democracycraft.democracyelections.data;

public enum StateChangeType implements Dto {
    CREATED,
    OPENED,
    CLOSED,
    EXPORTED,
    DELETED,
    // Config changes
    TITLE_CHANGED,
    SYSTEM_CHANGED,
    MINIMUM_CHANGED,
    REQUIREMENTS_CHANGED,
    CLOSES_AT_SET,
    CLOSES_AT_CHANGED,
    CLOSES_AT_CLEARED,
    DURATION_SET,
    DURATION_CHANGED,
    DURATION_CLEARED,
    // Structure changes
    CANDIDATE_ADDED,
    CANDIDATE_REMOVED,
    POLL_ADDED,
    POLL_REMOVED
}
