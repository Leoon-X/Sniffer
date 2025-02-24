package me.leon.sniffer.api.events;

import me.leon.sniffer.data.enums.SniffType;
import org.bukkit.entity.Player;

public class SnifferStopEvent extends SnifferEvent {
    public SnifferStopEvent(Player player, SniffType type) {
        super(player, type);
    }
}