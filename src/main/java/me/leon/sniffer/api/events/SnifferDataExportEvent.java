package me.leon.sniffer.api.events;

import lombok.Getter;
import me.leon.sniffer.data.enums.SniffType;
import org.bukkit.entity.Player;
import java.io.File;

public class SnifferDataExportEvent extends SnifferEvent {
    @Getter private final File exportFile;
    @Getter private final String format;

    public SnifferDataExportEvent(Player player, SniffType type, File exportFile, String format) {
        super(player, type);
        this.exportFile = exportFile;
        this.format = format;
    }
}