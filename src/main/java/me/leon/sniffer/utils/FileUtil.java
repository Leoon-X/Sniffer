package me.leon.sniffer.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.leon.sniffer.Sniffer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class FileUtil {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_FOLDER = "data";
    private static final String LOGS_FOLDER = "logs";

    public static void init(Sniffer plugin) {
        createDirectory(plugin.getDataFolder());
        createDirectory(new File(plugin.getDataFolder(), DATA_FOLDER));
        createDirectory(new File(plugin.getDataFolder(), LOGS_FOLDER));
    }

    public static void saveData(Sniffer plugin, String category, UUID uuid, Object data) {
        File dataFolder = new File(plugin.getDataFolder(), DATA_FOLDER);
        File categoryFolder = new File(dataFolder, category);
        createDirectory(categoryFolder);

        File outputFile = new File(categoryFolder, uuid.toString() + ".json.gz");
        String json = GSON.toJson(data);

        try (GZIPOutputStream gzipOut = new GZIPOutputStream(new FileOutputStream(outputFile));
             Writer writer = new OutputStreamWriter(gzipOut, StandardCharsets.UTF_8)) {
            writer.write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static <T> T loadData(Sniffer plugin, String category, UUID uuid, Class<T> type) {
        File dataFolder = new File(plugin.getDataFolder(), DATA_FOLDER);
        File categoryFolder = new File(dataFolder, category);
        File inputFile = new File(categoryFolder, uuid.toString() + ".json.gz");

        if (!inputFile.exists()) {
            return null;
        }

        try (GZIPInputStream gzipIn = new GZIPInputStream(new FileInputStream(inputFile));
             Reader reader = new InputStreamReader(gzipIn, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void logData(Sniffer plugin, String category, UUID uuid, String data) {
        File logsFolder = new File(plugin.getDataFolder(), LOGS_FOLDER);
        File categoryFolder = new File(logsFolder, category);
        createDirectory(categoryFolder);

        File logFile = new File(categoryFolder, uuid.toString() + ".log");
        String timestamp = TimeUtil.getCurrentTime() + ": ";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(timestamp + data);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<String> readLogFile(File file) {
        List<String> lines = new ArrayList<>();
        if (!file.exists()) return lines;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return lines;
    }

    public static void deleteData(Sniffer plugin, String category, UUID uuid) {
        File dataFolder = new File(plugin.getDataFolder(), DATA_FOLDER);
        File categoryFolder = new File(dataFolder, category);
        File dataFile = new File(categoryFolder, uuid.toString() + ".json.gz");
        File logFile = new File(new File(plugin.getDataFolder(), LOGS_FOLDER + "/" + category), uuid.toString() + ".log");

        if (dataFile.exists()) {
            dataFile.delete();
        }
        if (logFile.exists()) {
            logFile.delete();
        }
    }

    public static void cleanup(Sniffer plugin, long maxAge) {
        long now = System.currentTimeMillis();

        // Cleanup data files
        File dataFolder = new File(plugin.getDataFolder(), DATA_FOLDER);
        cleanupDirectory(dataFolder, now - maxAge);

        // Cleanup log files
        File logsFolder = new File(plugin.getDataFolder(), LOGS_FOLDER);
        cleanupDirectory(logsFolder, now - maxAge);
    }

    private static void cleanupDirectory(File directory, long threshold) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                cleanupDirectory(file, threshold);
                if (file.list() != null && file.list().length == 0) {
                    file.delete();
                }
            } else if (file.lastModified() < threshold) {
                file.delete();
            }
        }
    }

    private static void createDirectory(File directory) {
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    public static long getDirectorySize(File directory) {
        long size = 0;
        File[] files = directory.listFiles();

        if (files == null) return 0;

        for (File file : files) {
            if (file.isDirectory()) {
                size += getDirectorySize(file);
            } else {
                size += file.length();
            }
        }

        return size;
    }

    public static void compressOldLogs(Sniffer plugin, int daysOld) {
        File logsFolder = new File(plugin.getDataFolder(), LOGS_FOLDER);
        long threshold = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L);

        try {
            Files.walk(Paths.get(logsFolder.toURI()))
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".log"))
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis() < threshold;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(FileUtil::compressLog);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void compressLog(Path logPath) {
        Path gzipPath = Paths.get(logPath.toString() + ".gz");

        try (GZIPOutputStream gzipOut = new GZIPOutputStream(new FileOutputStream(gzipPath.toFile()));
             BufferedInputStream in = new BufferedInputStream(new FileInputStream(logPath.toFile()))) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) > 0) {
                gzipOut.write(buffer, 0, len);
            }

            Files.delete(logPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}