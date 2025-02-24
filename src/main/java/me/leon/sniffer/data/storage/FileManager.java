package me.leon.sniffer.data.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import me.leon.sniffer.Sniffer;
import me.leon.sniffer.data.enums.SniffType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class FileManager {
    private final Sniffer plugin;
    private final Gson gson;
    private final File dataFolder;
    private final File sniffFolder;
    private final File logsFolder;
    private final File reportsFolder;

    public FileManager(Sniffer plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataFolder = plugin.getDataFolder();
        this.sniffFolder = new File(dataFolder, "sniff");
        this.logsFolder = new File(dataFolder, "logs");
        this.reportsFolder = new File(dataFolder, "reports");

        initializeFolders();
    }

    private void initializeFolders() {
        createFolder(dataFolder);
        createFolder(sniffFolder);
        createFolder(logsFolder);
        createFolder(reportsFolder);

        // Create folders for each sniff type
        for (SniffType type : SniffType.values()) {
            createFolder(new File(sniffFolder, type.name().toLowerCase()));
            createFolder(new File(logsFolder, type.name().toLowerCase()));
        }
    }

    private void createFolder(File folder) {
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    public void saveSniffData(SniffType type, UUID uuid, Object data) {
        File typeFolder = new File(sniffFolder, type.name().toLowerCase());
        File outputFile = new File(typeFolder, uuid.toString() + ".json.gz");

        try (GZIPOutputStream gzipOut = new GZIPOutputStream(new FileOutputStream(outputFile));
             Writer writer = new OutputStreamWriter(gzipOut, StandardCharsets.UTF_8)) {

            gson.toJson(data, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save sniff data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public <T> T loadSniffData(SniffType type, UUID uuid, Class<T> dataClass) {
        File typeFolder = new File(sniffFolder, type.name().toLowerCase());
        File inputFile = new File(typeFolder, uuid.toString() + ".json.gz");

        if (!inputFile.exists()) {
            return null;
        }

        try (GZIPInputStream gzipIn = new GZIPInputStream(new FileInputStream(inputFile));
             Reader reader = new InputStreamReader(gzipIn, StandardCharsets.UTF_8)) {

            return gson.fromJson(reader, dataClass);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load sniff data: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public void logData(SniffType type, UUID uuid, String data) {
        File typeFolder = new File(logsFolder, type.name().toLowerCase());
        File logFile = new File(typeFolder, uuid.toString() + ".log");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(System.currentTimeMillis() + ": " + data);
            writer.newLine();
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to write log: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<String> readLog(SniffType type, UUID uuid) {
        File typeFolder = new File(logsFolder, type.name().toLowerCase());
        File logFile = new File(typeFolder, uuid.toString() + ".log");
        List<String> lines = new ArrayList<>();

        if (!logFile.exists()) {
            return lines;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to read log: " + e.getMessage());
            e.printStackTrace();
        }

        return lines;
    }

    public void saveReport(UUID playerUuid, UUID reporterUuid, JsonObject reportData) {
        File reportFile = new File(reportsFolder, playerUuid.toString() + "_" +
                System.currentTimeMillis() + ".json");

        try (Writer writer = new FileWriter(reportFile)) {
            reportData.addProperty("reporter", reporterUuid.toString());
            reportData.addProperty("timestamp", System.currentTimeMillis());
            gson.toJson(reportData, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<JsonObject> getReports(UUID playerUuid) {
        List<JsonObject> reports = new ArrayList<>();
        File[] files = reportsFolder.listFiles((dir, name) ->
                name.startsWith(playerUuid.toString()));

        if (files != null) {
            for (File file : files) {
                try (Reader reader = new FileReader(file)) {
                    reports.add(gson.fromJson(reader, JsonObject.class));
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to read report: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        return reports;
    }

    public void deleteData(SniffType type, UUID uuid) {
        // Delete sniff data
        File sniffFile = new File(sniffFolder, type.name().toLowerCase() + "/" + uuid.toString() + ".json.gz");
        if (sniffFile.exists()) {
            sniffFile.delete();
        }

        // Delete log file
        File logFile = new File(logsFolder, type.name().toLowerCase() + "/" + uuid.toString() + ".log");
        if (logFile.exists()) {
            logFile.delete();
        }
    }

    public void cleanupOldData(long maxAge) {
        long threshold = System.currentTimeMillis() - maxAge;

        // Cleanup sniff data
        cleanupFolder(sniffFolder, threshold);

        // Cleanup logs
        cleanupFolder(logsFolder, threshold);

        // Cleanup old reports
        cleanupFolder(reportsFolder, threshold);
    }

    private void cleanupFolder(File folder, long threshold) {
        if (!folder.exists() || !folder.isDirectory()) {
            return;
        }

        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    cleanupFolder(file, threshold);
                    if (file.list() != null && file.list().length == 0) {
                        file.delete();
                    }
                } else if (file.lastModified() < threshold) {
                    file.delete();
                }
            }
        }
    }

    public void compressOldLogs(int daysOld) {
        long threshold = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L);

        try {
            Files.walk(logsFolder.toPath())
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".log"))
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis() < threshold;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(this::compressLog);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to compress old logs: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Add these methods to the existing FileManager class:

    /**
     * Export data to file
     * @param data Data object to export
     * @param type Type of data
     * @param format Export format
     * @param uuid Player UUID
     * @throws Exception if export fails
     */
    public void exportData(Object data, SniffType type, String format, UUID uuid) throws Exception {
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

        // Export data based on format
        switch (format.toLowerCase()) {
            case "json":
                exportJson(data, exportFile);
                break;
            case "csv":
                exportCsv(data, exportFile, type);
                break;
            case "yaml":
                exportYaml(data, exportFile);
                break;
            default:
                throw new IllegalArgumentException("Unsupported export format: " + format);
        }
    }

    private void exportJson(Object data, File file) throws Exception {
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(data, writer);
        }
    }

    private void exportYaml(Object data, File file) throws Exception {
        // Convert to Map first
        String json = gson.toJson(data);
        Map<String, Object> map = gson.fromJson(json, Map.class);

        // Write as YAML
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("# Sniffer Data Export\n");
            writer.write("# Generated: " + new Date() + "\n\n");

            writeYamlMap(map, writer, 0);
        }
    }


    private void writeYamlMap(Map<String, Object> map, FileWriter writer, int indent) throws IOException {
        // Create indent string using StringBuilder for Java 8
        String indentStr = String.format("%" + indent + "s", "").replace(' ', ' ');
        // Alternative method:
        // StringBuilder indentStr = new StringBuilder();
        // for (int i = 0; i < indent; i++) {
        //     indentStr.append(" ");
        // }

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                writer.write(indentStr + key + ":\n");
                writeYamlMap((Map<String, Object>) value, writer, indent + 2);
            } else if (value instanceof List) {
                writer.write(indentStr + key + ":\n");
                for (Object item : (List) value) {
                    writer.write(indentStr + "- " + item + "\n");
                }
            } else {
                writer.write(indentStr + key + ": " + value + "\n");
            }
        }
    }

    private void exportCsv(Object data, File file, SniffType type) throws Exception {
        // Convert to Map first
        String json = gson.toJson(data);
        Map<String, Object> map = gson.fromJson(json, Map.class);

        try (FileWriter writer = new FileWriter(file)) {
            switch (type) {
                case MOVEMENT:
                    exportMovementCsv(map, writer);
                    break;
                case COMBAT:
                    exportCombatCsv(map, writer);
                    break;
                case ROTATION:
                    exportRotationCsv(map, writer);
                    break;
                case BLOCK:
                    exportBlockCsv(map, writer);
                    break;
            }
        }
    }

    private void exportMovementCsv(Map<String, Object> data, FileWriter writer) throws IOException {
        // Headers
        writer.write("timestamp,x,y,z,speed,onGround\n");

        // Position data
        Map<String, Object> positions = (Map<String, Object>) data.get("positions");
        Map<String, Object> groundStates = (Map<String, Object>) data.get("groundStates");

        if (positions != null) {
            for (String timestamp : positions.keySet()) {
                List<Double> position = (List<Double>) positions.get(timestamp);
                Boolean onGround = groundStates != null ? (Boolean) groundStates.get(timestamp) : null;

                writer.write(timestamp + "," +
                        position.get(0) + "," +
                        position.get(1) + "," +
                        position.get(2) + "," +
                        "0.0" + "," + // We don't have speed per position
                        (onGround != null ? onGround : "unknown") + "\n");
            }
        }
    }

    private void exportCombatCsv(Map<String, Object> data, FileWriter writer) throws IOException {
        // Implement CSV export for combat data
        writer.write("timestamp,reach,yaw,pitch,targetId\n");

        // Basic implementation - extend for your data structure
        Map<String, Object> reaches = (Map<String, Object>) data.get("reachDistances");
        Map<String, Object> angles = (Map<String, Object>) data.get("hitAngles");

        if (reaches != null) {
            for (String timestamp : reaches.keySet()) {
                Double reach = (Double) reaches.get(timestamp);
                List<Double> angle = angles != null ? (List<Double>) angles.get(timestamp) : null;

                writer.write(timestamp + "," +
                        reach + "," +
                        (angle != null ? angle.get(0) : "0") + "," +
                        (angle != null ? angle.get(1) : "0") + "," +
                        "unknown" + "\n");
            }
        }
    }

    private void exportRotationCsv(Map<String, Object> data, FileWriter writer) throws IOException {
        // Implement CSV export for rotation data
        writer.write("timestamp,yaw,pitch,speed,gcd\n");

        // Basic implementation - extend for your data structure
        Map<String, Object> rotations = (Map<String, Object>) data.get("rotations");

        if (rotations != null) {
            for (String timestamp : rotations.keySet()) {
                List<Double> rotation = (List<Double>) rotations.get(timestamp);

                writer.write(timestamp + "," +
                        rotation.get(0) + "," +
                        rotation.get(1) + "," +
                        "0.0" + "," + // Speed not available per rotation
                        "0.0" + "\n"); // GCD not available per rotation
            }
        }
    }

    private void exportBlockCsv(Map<String, Object> data, FileWriter writer) throws IOException {
        // Implement CSV export for block data
        writer.write("timestamp,x,y,z,type,breakTime\n");

        // Basic implementation - extend for your data structure
        Map<String, Object> locations = (Map<String, Object>) data.get("breakLocations");
        Map<String, Object> times = (Map<String, Object>) data.get("breakTimes");

        if (locations != null) {
            for (String timestamp : locations.keySet()) {
                List<Double> location = (List<Double>) locations.get(timestamp);
                Double breakTime = times != null ? (Double) times.get(timestamp) : null;

                writer.write(timestamp + "," +
                        location.get(0) + "," +
                        location.get(1) + "," +
                        location.get(2) + "," +
                        "unknown" + "," + // Block type
                        (breakTime != null ? breakTime : "0.0") + "\n");
            }
        }
    }

    private void compressLog(Path logPath) {
        Path gzipPath = logPath.getParent().resolve(logPath.getFileName().toString() + ".gz");

        try (GZIPOutputStream gzipOut = new GZIPOutputStream(new FileOutputStream(gzipPath.toFile()));
             BufferedInputStream in = new BufferedInputStream(new FileInputStream(logPath.toFile()))) {

            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                gzipOut.write(buffer, 0, len);
            }

            Files.delete(logPath);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to compress log file: " + logPath);
            e.printStackTrace();
        }
    }

    public long getFolderSize(File folder) {
        long size = 0;
        File[] files = folder.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    size += getFolderSize(file);
                } else {
                    size += file.length();
                }
            }
        }

        return size;
    }
}