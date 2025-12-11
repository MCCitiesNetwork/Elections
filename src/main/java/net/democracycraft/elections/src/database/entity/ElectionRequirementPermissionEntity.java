package net.democracycraft.elections.src.database.entity;

/**
 * Single permission requirement entry for an election.
 *
 * Constraints (see DatabaseSchema):
 * - UNIQUE (electionId, permission)
 * - FK electionId -> elections(id)
 */
public class ElectionRequirementPermissionEntity {
    /** Owning election identifier (FK to elections.id). */
    public int electionId;
    /** Bukkit permission node required to participate. */
    public String permission;
    public ElectionRequirementPermissionEntity() {}
}
