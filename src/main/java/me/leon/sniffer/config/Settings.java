package me.leon.sniffer.config;

import lombok.Getter;
import me.leon.sniffer.Sniffer;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

@Getter
public class Settings {
    private final Sniffer plugin;

    // Movement Settings
    private double maxSpeed;
    private double maxAcceleration;
    private double maxVerticalSpeed;
    private int maxAirTicks;
    private boolean checkTimer;

    // Combat Settings
    private double maxReach;
    private int maxCPS;
    private double aimSensitivity;
    private boolean checkAutoclicker;

    // Rotation Settings
    private double maxRotationSpeed;
    private double minGCD;
    private boolean checkAimbot;

    // Block Settings
    private double maxBlockReach;
    private int minBreakTime;
    private boolean checkNuker;

    // Debug Settings
    private boolean debug;
    private boolean verboseLogging;
    private boolean saveViolations;

    public Settings(Sniffer plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        // Load Movement Settings
        maxSpeed = config.getDouble("checks.movement.max-speed", 10.0);
        maxAcceleration = config.getDouble("checks.movement.max-acceleration", 5.0);
        maxVerticalSpeed = config.getDouble("checks.movement.max-vertical-speed", 4.0);
        maxAirTicks = config.getInt("checks.movement.max-air-ticks", 20);
        checkTimer = config.getBoolean("checks.movement.check-timer", true);

        // Load Combat Settings
        maxReach = config.getDouble("checks.combat.max-reach", 3.1);
        maxCPS = config.getInt("checks.combat.max-cps", 20);
        aimSensitivity = config.getDouble("checks.combat.aim-sensitivity", 0.00001);
        checkAutoclicker = config.getBoolean("checks.combat.check-autoclicker", true);

        // Load Rotation Settings
        maxRotationSpeed = config.getDouble("checks.rotation.max-speed", 180.0);
        minGCD = config.getDouble("checks.rotation.min-gcd", 0.00001);
        checkAimbot = config.getBoolean("checks.rotation.check-aimbot", true);

        // Load Block Settings
        maxBlockReach = config.getDouble("checks.block.max-reach", 5.5);
        minBreakTime = config.getInt("checks.block.min-break-time", 50);
        checkNuker = config.getBoolean("checks.block.check-nuker", true);

        // Load Debug Settings
        debug = config.getBoolean("debug", false);
        verboseLogging = config.getBoolean("verbose-logging", false);
        saveViolations = config.getBoolean("save-violations", true);
    }

    public void save() {
        FileConfiguration config = plugin.getConfig();

        // Save Movement Settings
        config.set("checks.movement.max-speed", maxSpeed);
        config.set("checks.movement.max-acceleration", maxAcceleration);
        config.set("checks.movement.max-vertical-speed", maxVerticalSpeed);
        config.set("checks.movement.max-air-ticks", maxAirTicks);
        config.set("checks.movement.check-timer", checkTimer);

        // Save Combat Settings
        config.set("checks.combat.max-reach", maxReach);
        config.set("checks.combat.max-cps", maxCPS);
        config.set("checks.combat.aim-sensitivity", aimSensitivity);
        config.set("checks.combat.check-autoclicker", checkAutoclicker);

        // Save Rotation Settings
        config.set("checks.rotation.max-speed", maxRotationSpeed);
        config.set("checks.rotation.min-gcd", minGCD);
        config.set("checks.rotation.check-aimbot", checkAimbot);

        // Save Block Settings
        config.set("checks.block.max-reach", maxBlockReach);
        config.set("checks.block.min-break-time", minBreakTime);
        config.set("checks.block.check-nuker", checkNuker);

        // Save Debug Settings
        config.set("debug", debug);
        config.set("verbose-logging", verboseLogging);
        config.set("save-violations", saveViolations);

        plugin.saveConfig();
    }

    /**
     * Convert settings to map
     *
     * @return Map of settings
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();

        // Movement settings
        Map<String, Object> movement = new HashMap<>();
        movement.put("max-speed", maxSpeed);
        movement.put("max-acceleration", maxAcceleration);
        movement.put("max-vertical-speed", maxVerticalSpeed);
        movement.put("max-air-ticks", maxAirTicks);
        movement.put("check-timer", checkTimer);
        map.put("movement", movement);

        // Combat settings
        Map<String, Object> combat = new HashMap<>();
        combat.put("max-reach", maxReach);
        combat.put("max-cps", maxCPS);
        combat.put("aim-sensitivity", aimSensitivity);
        combat.put("check-autoclicker", checkAutoclicker);
        map.put("combat", combat);

        // Rotation settings
        Map<String, Object> rotation = new HashMap<>();
        rotation.put("max-speed", maxRotationSpeed);
        rotation.put("min-gcd", minGCD);
        rotation.put("check-aimbot", checkAimbot);
        map.put("rotation", rotation);

        // Block settings
        Map<String, Object> block = new HashMap<>();
        block.put("max-reach", maxBlockReach);
        block.put("min-break-time", minBreakTime);
        block.put("check-nuker", checkNuker);
        map.put("block", block);

        // Debug settings
        map.put("debug", debug);
        map.put("verbose-logging", verboseLogging);
        map.put("save-violations", saveViolations);

        return map;
    }

    /**
     * Update a setting
     *
     * @param path  Setting path
     * @param value New value
     */
    public void update(String path, Object value) {
        // Parse the path
        String[] parts = path.split("\\.");

        if (parts.length == 1) {
            // Root level settings
            switch (parts[0]) {
                case "debug":
                    if (value instanceof Boolean) debug = (Boolean) value;
                    break;
                case "verbose-logging":
                    if (value instanceof Boolean) verboseLogging = (Boolean) value;
                    break;
                case "save-violations":
                    if (value instanceof Boolean) saveViolations = (Boolean) value;
                    break;
            }
        } else if (parts.length == 2) {
            // Category settings
            String category = parts[0];
            String setting = parts[1];

            switch (category) {
                case "movement":
                    updateMovementSetting(setting, value);
                    break;
                case "combat":
                    updateCombatSetting(setting, value);
                    break;
                case "rotation":
                    updateRotationSetting(setting, value);
                    break;
                case "block":
                    updateBlockSetting(setting, value);
                    break;
            }
        }

        save();
    }

    private void updateMovementSetting(String setting, Object value) {
        switch (setting) {
            case "max-speed":
                if (value instanceof Number) maxSpeed = ((Number) value).doubleValue();
                break;
            case "max-acceleration":
                if (value instanceof Number) maxAcceleration = ((Number) value).doubleValue();
                break;
            case "max-vertical-speed":
                if (value instanceof Number) maxVerticalSpeed = ((Number) value).doubleValue();
                break;
            case "max-air-ticks":
                if (value instanceof Number) maxAirTicks = ((Number) value).intValue();
                break;
            case "check-timer":
                if (value instanceof Boolean) checkTimer = (Boolean) value;
                break;
        }
    }

    private void updateCombatSetting(String setting, Object value) {
        switch (setting) {
            case "max-reach":
                if (value instanceof Number) maxReach = ((Number) value).doubleValue();
                break;
            case "max-cps":
                if (value instanceof Number) maxCPS = ((Number) value).intValue();
                break;
            case "aim-sensitivity":
                if (value instanceof Number) aimSensitivity = ((Number) value).doubleValue();
                break;
            case "check-autoclicker":
                if (value instanceof Boolean) checkAutoclicker = (Boolean) value;
                break;
        }
    }

    private void updateRotationSetting(String setting, Object value) {
        switch (setting) {
            case "max-speed":
                if (value instanceof Number) maxRotationSpeed = ((Number) value).doubleValue();
                break;
            case "min-gcd":
                if (value instanceof Number) minGCD = ((Number) value).doubleValue();
                break;
            case "check-aimbot":
                if (value instanceof Boolean) checkAimbot = (Boolean) value;
                break;
        }
    }

    private void updateBlockSetting(String setting, Object value) {
        switch (setting) {
            case "max-reach":
                if (value instanceof Number) maxBlockReach = ((Number) value).doubleValue();
                break;
            case "min-break-time":
                if (value instanceof Number) minBreakTime = ((Number) value).intValue();
                break;
            case "check-nuker":
                if (value instanceof Boolean) checkNuker = (Boolean) value;
                break;
        }
    }
}