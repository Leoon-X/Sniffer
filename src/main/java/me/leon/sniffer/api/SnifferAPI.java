package me.leon.sniffer.api;

import lombok.Getter;
import me.leon.sniffer.Sniffer;
import me.leon.sniffer.data.container.*;
import me.leon.sniffer.data.enums.SniffType;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SnifferAPI {
    private final Sniffer plugin;
    @Getter private static SnifferAPI instance;

    public SnifferAPI(Sniffer plugin) {
        this.plugin = plugin;
        instance = this;
    }

    /**
     * Start sniffing a specific type of data for a player
     * @param player The player to sniff
     * @param type The type of data to sniff
     * @return true if sniffing started successfully
     */
    public boolean startSniffing(Player player, SniffType type) {
        UUID uuid = player.getUniqueId();
        if (plugin.getDataManager().isSniffing(uuid, type)) {
            return false;
        }

        switch (type) {
            case MOVEMENT:
                return plugin.getMovementCollector().startCollecting(player);
            case COMBAT:
                return plugin.getCombatCollector().startCollecting(player);
            case ROTATION:
                return plugin.getRotationCollector().startCollecting(player);
            case BLOCK:
                return plugin.getBlockCollector().startCollecting(player);
            default:
                return false;
        }
    }

    /**
     * Stop sniffing a specific type of data for a player
     * @param player The player to stop sniffing
     * @param type The type of data to stop sniffing
     */
    public void stopSniffing(Player player, SniffType type) {
        UUID uuid = player.getUniqueId();
        plugin.getDataManager().stopSniffing(uuid, type);
    }

    /**
     * Get current movement data for a player
     * @param player The player
     * @return MovementData or null if not sniffing
     */
    public MovementData getMovementData(Player player) {
        return plugin.getMovementCollector().getMovementData().get(player.getUniqueId());
    }

    /**
     * Get current combat data for a player
     * @param player The player
     * @return CombatData or null if not sniffing
     */
    public CombatData getCombatData(Player player) {
        return plugin.getCombatCollector().getCombatData().get(player.getUniqueId());
    }

    /**
     * Get current rotation data for a player
     * @param player The player
     * @return RotationData or null if not sniffing
     */
    public RotationData getRotationData(Player player) {
        return plugin.getRotationCollector().getRotationData().get(player.getUniqueId());
    }

    /**
     * Get current block data for a player
     * @param player The player
     * @return BlockData or null if not sniffing
     */
    public BlockData getBlockData(Player player) {
        return plugin.getBlockCollector().getBlockData().get(player.getUniqueId());
    }

    /**
     * Get all violations for a player
     * @param player The player
     * @return Map of SniffType to violation list
     */
    public Map<SniffType, List<String>> getAllViolations(Player player) {
        return plugin.getDataManager().getViolations(player.getUniqueId());
    }

    /**
     * Get analysis results for a player
     * @param player The player
     * @param type The type of data to analyze
     * @return Map of analysis results
     */
    public Map<String, Object> getAnalysisResults(Player player, SniffType type) {
        switch (type) {
            case MOVEMENT:
                MovementData moveData = getMovementData(player);
                return moveData != null ? moveData.getAnalysisResults() : null;
            case COMBAT:
                CombatData combatData = getCombatData(player);
                return combatData != null ? combatData.getAnalysisResults() : null;
            case ROTATION:
                RotationData rotData = getRotationData(player);
                return rotData != null ? rotData.getAnalysisResults() : null;
            case BLOCK:
                BlockData blockData = getBlockData(player);
                return blockData != null ? blockData.getAnalysisResults() : null;
            default:
                return null;
        }
    }

    /**
     * Export collected data to a file
     * @param player The player
     * @param type The type of data to export
     * @param format The export format (JSON, CSV, YAML)
     * @return true if export was successful
     */
    public boolean exportData(Player player, SniffType type, String format) {
        return plugin.getDataManager().exportData(player.getUniqueId(), type, format);
    }

    /**
     * Clear all collected data for a player
     * @param player The player
     */
    public void clearData(Player player) {
        plugin.getDataManager().clearData(player.getUniqueId());
    }

    /**
     * Check if a player is being sniffed for a specific type
     * @param player The player
     * @param type The type of data
     * @return true if being sniffed
     */
    public boolean isSniffing(Player player, SniffType type) {
        return plugin.getDataManager().isSniffing(player.getUniqueId(), type);
    }

    /**
     * Get current settings
     * @return The current settings
     */
    public Map<String, Object> getSettings() {
        return plugin.getSettings().toMap();
    }

    /**
     * Update a setting
     * @param path The setting path
     * @param value The new value
     */
    public void updateSetting(String path, Object value) {
        plugin.getSettings().update(path, value);
    }
}
