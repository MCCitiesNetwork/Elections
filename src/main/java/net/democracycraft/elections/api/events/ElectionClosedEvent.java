package net.democracycraft.elections.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after an election transitions to CLOSED.
 * Always dispatched on the Bukkit main thread.
 */
public class ElectionClosedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final int electionId;
    private final String actor;

    public ElectionClosedEvent(int electionId, String actor) {
        super(false);
        this.electionId = electionId;
        this.actor = actor;
    }

    public int getElectionId() { return electionId; }
    public String getActor() { return actor; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

