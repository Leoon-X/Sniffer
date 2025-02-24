package me.leon.sniffer.api.events;

import lombok.Getter;
import me.leon.sniffer.data.enums.SniffType;
import org.bukkit.entity.Player;

import java.util.Map;

public class SnifferViolationEvent extends SnifferEvent {
    @Getter private final String violation;
    @Getter private final Map<String, Object> data;

    public SnifferViolationEvent(Player player, SniffType type, String violation, Map<String, Object> data) {
        super(player, type);
        this.violation = violation;
        this.data = data;
    }
}