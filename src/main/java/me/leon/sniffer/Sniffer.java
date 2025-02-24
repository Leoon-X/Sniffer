package me.leon.sniffer;

import io.github.retrooper.packetevents.PacketEvents;
import lombok.Getter;
import me.leon.sniffer.collector.impl.*;
import me.leon.sniffer.config.Settings;
import me.leon.sniffer.data.storage.DataManager;
import me.leon.sniffer.data.storage.FileManager;
import me.leon.sniffer.handlers.CommandHandler;
import me.leon.sniffer.handlers.PacketHandler;
import me.leon.sniffer.processors.*;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public class Sniffer extends JavaPlugin {

    @Getter
    private static Sniffer instance;

    /**
     * -- GETTER --
     *  Gets the plugin settings
     *
     * @return Settings instance
     */
    @Getter private Settings settings;

    // Managers
    private DataManager dataManager;
    private FileManager fileManager;
    private CommandHandler commandHandler;

    // Handlers
    private PacketHandler packetHandler;

    // Collectors
    private MovementCollector movementCollector;
    private CombatCollector combatCollector;
    private RotationCollector rotationCollector;
    private BlockCollector blockCollector;

    // Processors
    private MovementProcessor movementProcessor;
    private CombatProcessor combatProcessor;
    private RotationProcessor rotationProcessor;
    private BlockProcessor blockProcessor;

    @Override
    public void onLoad() {
        instance = this;

        // Initialize PacketEvents for 1.8
        PacketEvents.create(this).load();
    }
    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Initialize settings
        settings = new Settings(this);

        // Initialize managers
        fileManager = new FileManager(this);
        dataManager = new DataManager(this);
        commandHandler = new CommandHandler(this);

        // Initialize processors
        initializeProcessors();

        // Initialize collectors
        initializeCollectors();

        // Initialize packet handler
        packetHandler = new PacketHandler(this);

        // Register commands
        getCommand("sniff").setExecutor(commandHandler);

        // Register packet listeners
        PacketEvents.getAPI().getEventManager().registerListener(packetHandler);

        // Initialize PacketEvents
        PacketEvents.getAPI().init();
        PacketEvents.get().init();

        getLogger().info("Sniffer has been enabled!");
    }

    private void initializeProcessors() {
        movementProcessor = new MovementProcessor(this);
        combatProcessor = new CombatProcessor(this);
        rotationProcessor = new RotationProcessor(this);
        blockProcessor = new BlockProcessor(this);
    }

    private void initializeCollectors() {
        movementCollector = new MovementCollector(this);
        combatCollector = new CombatCollector(this);
        rotationCollector = new RotationCollector(this);
        blockCollector = new BlockCollector(this);
    }

    @Override
    public void onDisable() {
        // Save all data
        if (dataManager != null) {
            dataManager.saveAllData();
        }

        // Clean up PacketEvents
        if (PacketEvents.get() != null) {
            PacketEvents.get().stop();
        }

        PacketEvents.getAPI().terminate();

        getLogger().info("Sniffer has been disabled!");
    }

    public void reloadPlugin() {
        // Reload config
        reloadConfig();

        // Save and reload data
        dataManager.saveAllData();

        // Clear all collectors
        movementCollector.clearData();
        combatCollector.clearData();
        rotationCollector.clearData();
        blockCollector.clearData();

        // Reset all processors
        movementProcessor.reset();
        combatProcessor.reset();
        rotationProcessor.reset();
        blockProcessor.reset();

        // Reload managers
        dataManager = new DataManager(this);

        getLogger().info("Sniffer has been reloaded!");
    }
}