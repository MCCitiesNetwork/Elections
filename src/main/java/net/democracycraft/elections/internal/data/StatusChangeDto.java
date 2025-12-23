package net.democracycraft.elections.internal.data;

/**
 * Describes a state change event with timestamp, type, the responsible actor, and optional details.
 * actor may be null if unknown; details may be null when not applicable (e.g., OPEN/CLOSE).
 */
public record StatusChangeDto(TimeStampDto at, StateChangeType type, String actor, String details) implements Dto {


}
