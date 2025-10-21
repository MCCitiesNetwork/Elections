package net.democracycraft.democracyelections.data;

public record StatusChangeDto(TimeStampDto at, StateChangeType type) implements Dto {
}

