package net.democracycraft.elections.database.entity;

/**
 * Poll location row for an election. Represents a voting booth block location.
 *
 * Constraints (see DatabaseSchema):
 * - UNIQUE (electionId, world, x, y, z)
 * - FK electionId -> elections(id)
 */
public class PollEntity {
    /** Owning election identifier (FK to elections.id). */
    public int electionId;
    /** World name where the poll block is located. */
    public String world;
    /** Block X coordinate. */
    public int x;
    /** Block Y coordinate. */
    public int y;
    /** Block Z coordinate. */
    public int z;

    public PollEntity() {}
}

