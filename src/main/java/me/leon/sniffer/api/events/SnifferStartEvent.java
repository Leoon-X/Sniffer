package me.leon.sniffer.api.events;

import me.leon.sniffer.data.enums.SniffType;
import org.bukkit.entity.Player;

public class SnifferStartEvent extends SnifferEvent {
    public SnifferStartEvent(Player player, SniffType type) {
        super(player, type);
    }
}