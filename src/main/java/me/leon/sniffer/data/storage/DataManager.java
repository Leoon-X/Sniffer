package me.leon.sniffer.data.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import me.leon.sniffer.Sniffer;
import me.leon.sniffer.data.container.*;
import me.leon.sniffer.data.enums.SniffType;
import me.leon.sniffer.utils.FileUtil;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DataManager {
    private final Sniffer plugin;
    private final Gson gson;
    private final ScheduledExecutorService executor;

    @Getter private final Map<UUID, Map<SniffType, Long>> activeSniffers;
    private final Map<UUID, Long> lastSave;

    private static final long SAVE_INTERVAL = 300000; // 5 minutes
    private static final long CLEANUP_INTERVAL = 600000; // 10 minutes
    private static final long DATA_EXPIRY = 86400000; // 24 hours

    public DataManager(Sniffer plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.activeSniffers = new ConcurrentHashMap<>();
        this.lastSave = new ConcurrentHashMap<>();

        initializeScheduledTasks();
    }

    private void initializeScheduledTasks() {
        // Schedule periodic data saving
        executor.scheduleAtFixedRate(this::saveAllData,
                SAVE_INTERVAL, SAVE_INTERVAL, TimeUnit.MILLISECONDS);

        // Schedule periodic cleanup
        executor.scheduleAtFixedRate(this::cleanup,
                CLEANUP_INTERVAL, CLEANUP_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public void startSniffing(UUID uuid, SniffType type) {
        activeSniffers.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(type, System.currentTimeMillis());
    }

    public void stopSniffing(UUID uuid, SniffType type) {
        Map<SniffType, Long> playerSniffers = activeSniffers.get(uuid);
        if (playerSniffers != null) {
            playerSniffers.remove(type);
            if (playerSniffers.isEmpty()) {
                activeSniffers.remove(uuid);
            }
            saveData(uuid, type);
        }
    }

    public boolean isSniffing(UUID uuid, SniffType type) {
        Map<SniffType, Long> playerSniffers = activeSniffers.get(uuid);
        return playerSniffers != null && playerSniffers.containsKey(type);
    }

    public void saveData(UUID uuid, SniffType type) {
        try {
            switch (type) {
                case MOVEMENT:
                    MovementData movementData = plugin.getMovementCollector().getMovementData().get(uuid);
                    if (movementData != null) {
                        FileUtil.saveData(plugin, "movement", uuid, movementData);
                    }
                    break;

                case COMBAT:
                    CombatData combatData = plugin.getCombatCollector().getCombatData().get(uuid);
                    if (combatData != null) {
                        FileUtil.saveData(plugin, "combat", uuid, combatData);
                    }
                    break;

                case ROTATION:
                    RotationData rotationData = plugin.getRotationCollector().getRotationData().get(uuid);
                    if (rotationData != null) {
                        FileUtil.saveData(plugin, "rotation", uuid, rotationData);
                    }
                    break;

                case BLOCK:
                    BlockData blockData = plugin.getBlockCollector().getBlockData().get(uuid);
                    if (blockData != null) {
                        FileUtil.saveData(plugin, "block", uuid, blockData);
                    }
                    break;
            }

            lastSave.put(uuid, System.currentTimeMillis());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save data for " + uuid + " (" + type + "): " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveAllData() {
        for (UUID uuid : activeSniffers.keySet()) {
            Map<SniffType, Long> playerSniffers = activeSniffers.get(uuid);
            if (playerSniffers != null) {
                for (SniffType type : playerSniffers.keySet()) {
                    saveData(uuid, type);
                }
            }
        }
    }
    /**
     * Get all violations for a player
     * @param uuid Player UUID
     * @return Map of SniffType to violation list
     */
    public Map<SniffType, List<String>> getViolations(UUID uuid) {
        Map<SniffType, List<String>> violations = new HashMap<>();

        if (plugin.getMovementCollector().isCollecting(uuid)) {
            violations.put(SniffType.MOVEMENT,
                    plugin.getMovementCollector().getMovementData().get(uuid).getRecentViolations(20));
        }

        if (plugin.getCombatCollector().isCollecting(uuid)) {
            violations.put(SniffType.COMBAT,
                    plugin.getCombatCollector().getCombatData().get(uuid).getRecentViolations(20));
        }

        if (plugin.getRotationCollector().isCollecting(uuid)) {
            violations.put(SniffType.ROTATION,
                    plugin.getRotationCollector().getRotationData().get(uuid).getRecentViolations(20));
        }

        if (plugin.getBlockCollector().isCollecting(uuid)) {
            violations.put(SniffType.BLOCK,
                    plugin.getBlockCollector().getBlockData().get(uuid).getRecentViolations(20));
        }

        return violations;
    }

    /**
     * Export data to file
     * @param uuid Player UUID
     * @param type Type of data to export
     * @param format Export format
     * @return true if export successful
     */
    public boolean exportData(UUID uuid, SniffType type, String format) {
        try {
            // Create exports directory if it doesn't exist
            File exportsDir = new File(plugin.getDataFolder(), "exports");
            if (!exportsDir.exists()) {
                exportsDir.mkdirs();
            }

            // Get player name from UUID
            String playerName = plugin.getServer().getOfflinePlayer(uuid).getName();
            if (playerName == null) playerName = uuid.toString();

            // Create export file
            File exportFile = new File(exportsDir,
                    playerName + "_" + type.name().toLowerCase() + "_" +
                            System.currentTimeMillis() + "." + format.toLowerCase());

            if (type == SniffType.ALL) {
                // Export all data types
                for (SniffType t : SniffType.values()) {
                    if (t != SniffType.ALL) {
                        exportTypeData(uuid, t, format);
                    }
                }
                return true;
            } else {
                // Export specific type
                return exportTypeData(uuid, type, format);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error exporting data: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean exportTypeData(UUID uuid, SniffType type, String format) {
        try {
            switch (type) {
                case MOVEMENT:
                    MovementData movementData = plugin.getMovementCollector().getMovementData().get(uuid);
                    if (movementData != null) {
                        plugin.getFileManager().exportData(movementData, type, format, uuid);
                        return true;
                    }
                    break;

                case COMBAT:
                    CombatData combatData = plugin.getCombatCollector().getCombatData().get(uuid);
                    if (combatData != null) {
                        plugin.getFileManager().exportData(combatData, type, format, uuid);
                        return true;
                    }
                    break;

                case ROTATION:
                    RotationData rotationData = plugin.getRotationCollector().getRotationData().get(uuid);
                    if (rotationData != null) {
                        plugin.getFileManager().exportData(rotationData, type, format, uuid);
                        return true;
                    }
                    break;

                case BLOCK:
                    BlockData blockData = plugin.getBlockCollector().getBlockData().get(uuid);
                    if (blockData != null) {
                        plugin.getFileManager().exportData(blockData, type, format, uuid);
                        return true;
                    }
                    break;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error exporting data for type " + type + ": " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public void loadData(UUID uuid, SniffType type) {
        try {
            switch (type) {
                case MOVEMENT:
                    MovementData movementData = FileUtil.loadData(plugin, "movement", uuid, MovementData.class);
                    if (movementData != null) {
                        plugin.getMovementCollector().getMovementData().put(uuid, movementData);
                    }
                    break;

                case COMBAT:
                    CombatData combatData = FileUtil.loadData(plugin, "combat", uuid, CombatData.class);
                    if (combatData != null) {
                        plugin.getCombatCollector().getCombatData().put(uuid, combatData);
                    }
                    break;

                case ROTATION:
                    RotationData rotationData = FileUtil.loadData(plugin, "rotation", uuid, RotationData.class);
                    if (rotationData != null) {
                        plugin.getRotationCollector().getRotationData().put(uuid, rotationData);
                    }
                    break;

                case BLOCK:
                    BlockData blockData = FileUtil.loadData(plugin, "block", uuid, BlockData.class);
                    if (blockData != null) {
                        plugin.getBlockCollector().getBlockData().put(uuid, blockData);
                    }
                    break;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load data for " + uuid + " (" + type + "): " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void cleanup() {
        long now = System.currentTimeMillis();

        // Clean up expired data files
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (dataFolder.exists()) {
            for (SniffType type : SniffType.values()) {
                File typeFolder = new File(dataFolder, type.name().toLowerCase());
                if (typeFolder.exists()) {
                    File[] files = typeFolder.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (now - file.lastModified() > DATA_EXPIRY) {
                                file.delete();
                            }
                        }
                    }
                }
            }
        }

        // Clean up inactive sniffers
        activeSniffers.entrySet().removeIf(entry -> {
            Map<SniffType, Long> playerSniffers = entry.getValue();
            playerSniffers.entrySet().removeIf(snifferEntry ->
                    now - snifferEntry.getValue() > DATA_EXPIRY);
            return playerSniffers.isEmpty();
        });

        // Clean up last save times
        lastSave.entrySet().removeIf(entry -> now - entry.getValue() > DATA_EXPIRY);

        // Clean up collector data
        plugin.getMovementCollector().getMovementData().values().forEach(MovementData::cleanup);
        plugin.getCombatCollector().getCombatData().values().forEach(CombatData::cleanup);
        plugin.getRotationCollector().getRotationData().values().forEach(RotationData::cleanup);
        plugin.getBlockCollector().getBlockData().values().forEach(BlockData::cleanup);
    }

    public void shutdown() {
        // Save all pending data
        saveAllData();

        // Shutdown executor
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public Map<SniffType, Long> getPlayerSniffers(UUID uuid) {
        return activeSniffers.getOrDefault(uuid, new ConcurrentHashMap<>());
    }

    public void clearData(UUID uuid) {
        // Remove from active sniffers
        activeSniffers.remove(uuid);
        lastSave.remove(uuid);

        // Clear collector data
        plugin.getMovementCollector().clearData(uuid);
        plugin.getCombatCollector().clearData(uuid);
        plugin.getRotationCollector().clearData(uuid);
        plugin.getBlockCollector().clearData(uuid);

        // Delete saved files
        for (SniffType type : SniffType.values()) {
            FileUtil.deleteData(plugin, type.name().toLowerCase(), uuid);
        }
    }

    public long getLastSaveTime(UUID uuid) {
        return lastSave.getOrDefault(uuid, 0L);
    }
}