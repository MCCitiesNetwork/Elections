package net.democracycraft.elections.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after an election is marked as EXPORTED in the audit log.
 * Always dispatched on the Bukkit main thread.
 */
public class ElectionExportedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final int electionId;
    private final String actor;

    public ElectionExportedEvent(int electionId, String actor) {
        super(false);
        this.electionId = electionId;
        this.actor = actor;
    }

    public int getElectionId() { return electionId; }
    public String getActor() { return actor; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

