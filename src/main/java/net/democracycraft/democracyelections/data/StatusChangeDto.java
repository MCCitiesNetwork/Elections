package net.democracycraft.democracyelections.data;

public class StatusChangeDto implements Dto {
    private final TimeStampDto at;
    private final StateChangeType type;

    public StatusChangeDto(TimeStampDto at, StateChangeType type) {
        this.at = at;
        this.type = type;
    }

    public TimeStampDto getAt() { return at; }
    public StateChangeType getType() { return type; }
}

