package net.democracycraft.elections.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a voter successfully submits a ballot for an election.
 * Always dispatched on the Bukkit main thread.
 */
public class VoteSubmittedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final int electionId;
    private final int voterId;

    public VoteSubmittedEvent(int electionId, int voterId) {
        super(false);
        this.electionId = electionId;
        this.voterId = voterId;
    }

    public int getElectionId() { return electionId; }
    public int getVoterId() { return voterId; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

