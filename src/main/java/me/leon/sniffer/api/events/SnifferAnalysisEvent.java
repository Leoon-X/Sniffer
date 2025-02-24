package me.leon.sniffer.api.events;

import lombok.Getter;
import me.leon.sniffer.data.enums.SniffType;
import org.bukkit.entity.Player;
import java.util.Map;

public class SnifferAnalysisEvent extends SnifferEvent {
    @Getter private final Map<String, Object> results;

    public SnifferAnalysisEvent(Player player, SniffType type, Map<String, Object> results) {
        super(player, type);
        this.results = results;
    }
}