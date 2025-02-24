package me.leon.sniffer.config;

public class ConfigKeys {
    // Analysis Constants
    public static final int DEFAULT_SAMPLE_SIZE = 20;
    public static final int MIN_SAMPLES_FOR_ANALYSIS = 5;
    public static final long DEFAULT_SAVE_INTERVAL = 300000L; // 5 minutes
    public static final long DEFAULT_CLEANUP_INTERVAL = 600000L; // 10 minutes

    // Movement Constants
    public static final double MAX_VANILLA_SPEED = 10.0;
    public static final double MAX_VANILLA_ACCELERATION = 5.0;
    public static final double MAX_VANILLA_VERTICAL_SPEED = 4.0;
    public static final int MAX_VANILLA_AIR_TICKS = 20;

    // Combat Constants
    public static final double MAX_VANILLA_REACH = 3.1;
    public static final int MAX_VANILLA_CPS = 20;
    public static final double MIN_AIM_SENSITIVITY = 0.00001;

    // Rotation Constants
    public static final double MAX_VANILLA_ROTATION_SPEED = 180.0;
    public static final double MIN_VANILLA_GCD = 0.00001;

    // Block Constants
    public static final double MAX_VANILLA_BLOCK_REACH = 5.5;
    public static final int MIN_VANILLA_BREAK_TIME = 50;

    // Storage Constants
    public static final int MAX_FILE_SIZE_MB = 10;
    public static final int DEFAULT_CLEANUP_DAYS = 7;

    // Buffer Constants
    public static final int POSITION_BUFFER_SIZE = 20;
    public static final int ROTATION_BUFFER_SIZE = 20;
    public static final int TIMING_BUFFER_SIZE = 40;

    // Violation Constants
    public static final int MAX_VIOLATIONS_PER_CHECK = 50;
    public static final long VIOLATION_EXPIRY_TIME = 3600000L; // 1 hour

    private ConfigKeys() {
        // Prevent instantiation
    }
}