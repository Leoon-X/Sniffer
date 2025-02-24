package me.leon.sniffer.collector.interfaces;

import io.github.retrooper.packetevents.event.PacketListenerDynamic;
import me.leon.sniffer.data.enums.SniffType;
import org.bukkit.entity.Player;

import java.util.UUID;

public abstract class ICollector extends PacketListenerDynamic {
    private final SniffType type;
    protected final boolean debug;

    public ICollector(SniffType type, boolean debug) {
        this.type = type;
        this.debug = debug;
    }

    /**
     * Start collecting data for a player
     * @param player The player to collect data for
     * @return true if collection started successfully
     */
    public abstract boolean startCollecting(Player player);

    /**
     * Stop collecting data for a player
     * @param player The player to stop collecting data for
     */
    public abstract void stopCollecting(Player player);

    /**
     * Get if currently collecting data for a player
     * @param uuid Player UUID
     * @return true if collecting
     */
    public abstract boolean isCollecting(UUID uuid);

    /**
     * Save collected data
     * @param uuid Player UUID
     */
    public abstract void saveData(UUID uuid);

    /**
     * Clear data for a player
     * @param uuid Player UUID
     */
    public abstract void clearData(UUID uuid);

    /**
     * Get the amount of data points collected
     * @param uuid Player UUID
     * @return Number of data points
     */
    public abstract int getDataCount(UUID uuid);

    /**
     * Get type of collector
     * @return SniffType
     */
    public SniffType getType() {
        return type;
    }

    /**
     * Get if debug mode is enabled
     * @return true if debug enabled
     */
    public boolean isDebug() {
        return debug;
    }
}
