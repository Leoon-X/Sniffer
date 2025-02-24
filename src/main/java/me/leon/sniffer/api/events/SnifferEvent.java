package me.leon.sniffer.api.events;

import lombok.Getter;
import me.leon.sniffer.data.enums.SniffType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SnifferEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    protected boolean cancelled;
    @Getter protected final Player player;
    @Getter protected final SniffType type;

    public SnifferEvent(Player player, SniffType type) {
        this.player = player;
        this.type = type;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}