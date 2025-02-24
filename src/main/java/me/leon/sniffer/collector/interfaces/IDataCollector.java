package me.leon.sniffer.collector.interfaces;

import io.github.retrooper.packetevents.event.PacketListenerAbstract;
import me.leon.sniffer.data.enums.SniffType;
import org.bukkit.entity.Player;

public abstract class IDataCollector extends PacketListenerAbstract {

    /**
     * Start collecting data for a specific player
     * @param player The player to collect data for
     * @return true if collection started successfully
     */
    public abstract boolean startCollecting(Player player);

    /**
     * Stop collecting data for a specific player
     * @param player The player to stop collecting data for
     */
    public abstract void stopCollecting(Player player);

    /**
     * Save collected data to file
     * @param player The player whose data to save
     */
    public abstract void saveData(Player player);

    /**
     * Clear collected data for a player
     * @param player The player whose data to clear
     */
    public abstract void clearData(Player player);

    /**
     * Get the type of data this collector handles
     * @return The SniffType this collector is responsible for
     */
    public abstract SniffType getType();

    /**
     * Check if currently collecting data for a player
     * @param player The player to check
     * @return true if collecting data for this player
     */
    public abstract boolean isCollecting(Player player);

    /**
     * Get the current collected data size for a player
     * @param player The player to check
     * @return The amount of data points collected
     */
    public abstract int getCollectedDataSize(Player player);
}
