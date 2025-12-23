package net.democracycraft.elections.internal.data;

import java.util.List;

/**
 * Immutable data-transfer object representing eligibility requirements for an election.
 * Contains a list of required permission nodes and a minimum active playtime in minutes.
 *
 * @param permissions              List of permission node strings that a player must have to be eligible. May be empty.
 * @param minActivePlaytimeMinutes Minimum active playtime in minutes required to be eligible. 0 if not applicable.
 */
public record RequirementsDto(List<String> permissions, long minActivePlaytimeMinutes) implements Dto {
    /**
     * Creates a new requirements DTO.
     *
     * @param permissions              list of permission node strings (may be empty, non-null recommended)
     * @param minActivePlaytimeMinutes minimum active playtime in minutes (negative values are clamped to 0)
     */
    public RequirementsDto(List<String> permissions, long minActivePlaytimeMinutes) {

        this.permissions = permissions;
        this.minActivePlaytimeMinutes = Math.max(0, minActivePlaytimeMinutes);
    }

    /**
     * @return the list of required permission nodes.
     */
    @Override
    public List<String> permissions() {
        return permissions;
    }

    /**
     * @return the minimum active playtime in minutes.
     */
    @Override
    public long minActivePlaytimeMinutes() {
        return minActivePlaytimeMinutes;
    }

    /**
     * Replaces the current permission list contents with the provided list.
     * Note: this mutates the underlying list returned by getPermissions().
     *
     * @param newPermissions the new set of permission nodes to apply.
     */
    public void setNewPermissions(List<String> newPermissions) {
        permissions().clear();
        permissions().addAll(newPermissions);
    }
}
