package net.democracycraft.elections.internal.data;

/**
 * Per-election ballot UI mode.
 * MANUAL = current implementation (candidate list -> per-candidate menus).
 * SIMPLE = single-screen cycling/toggle controls per candidate.
 */
public enum BallotMode implements Dto {
    MANUAL,
    SIMPLE
}

