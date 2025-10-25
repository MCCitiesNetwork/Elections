package net.democracycraft.elections.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

abstract class DemocracyEvent extends Event {

    public static HandlerList handlerList = new HandlerList();
    public static HandlerList getHandlerList(){
        return handlerList;
    }
}
