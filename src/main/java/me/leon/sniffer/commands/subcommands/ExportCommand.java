package me.leon.sniffer.commands.subcommands;

import lombok.var;
import me.leon.sniffer.Sniffer;
import me.leon.sniffer.commands.AbstractCommand;
import me.leon.sniffer.data.enums.SniffType;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ExportCommand extends AbstractCommand {

    public ExportCommand(Sniffer plugin) {
        super(plugin,
                "export",
                "sniffer.command.export",
                "/sniff export <type> [format]",
                "Exports collected data to a file",
                1);

        addAlias("save");
    }

    @Override
    public void execute(Player player, String[] args) {
        String typeStr = args[0].toUpperCase();
        SniffType type;

        try {
            type = SniffType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            sendMessage(player, "&cInvalid sniff type! Available types: movement, combat, rotation, block, all");
            return;
        }

        String format = args.length > 1 ? args[1].toLowerCase() : "json";
        if (!format.equals("json") && !format.equals("csv") && !format.equals("yaml")) {
            sendMessage(player, "&cInvalid format! Available formats: json, csv, yaml");
            return;
        }

        if (type == SniffType.ALL) {
            exportAllTypes(player, format);
        } else {
            exportType(player, type, format);
        }
    }

    private void exportAllTypes(Player player, String format) {
        int exported = 0;
        for (SniffType type : SniffType.values()) {
            if (type != SniffType.ALL && plugin.getDataManager().isSniffing(player.getUniqueId(), type)) {
                if (exportType(player, type, format)) {
                    exported++;
                }
            }
        }

        if (exported > 0) {
            sendMessage(player, String.format("&aSuccessfully exported &e%d &atypes of data!", exported));
        } else {
            sendMessage(player, "&cNo data available to export!");
        }
    }

        private boolean exportType(Player player, SniffType type, String format) {
        UUID uuid = player.getUniqueId();
        if (!plugin.getDataManager().isSniffing(uuid, type)) {
            sendMessage(player, "&cNo data available for " + type.name().toLowerCase() + "!");
            return false;
        }

        try {
            Object data = null;
            switch (type) {
                case MOVEMENT:
                    data = plugin.getMovementCollector().getMovementData().get(uuid);
                    break;
                case COMBAT:
                    data = plugin.getCombatCollector().getCombatData().get(uuid);
                    break;
                case ROTATION:
                    data = plugin.getRotationCollector().getRotationData().get(uuid);
                    break;
                case BLOCK:
                    data = plugin.getBlockCollector().getBlockData().get(uuid);
                    break;
                default:
                    sendMessage(player, "&cUnsupported data type!");
                    return false;
            }

            if (data == null) {
                sendMessage(player, "&cNo data found for " + type.name().toLowerCase() + "!");
                return false;
            }

            plugin.getFileManager().exportData(data, type, format, uuid);

            sendMessage(player, "&aExported " + type.name().toLowerCase() + " data!");
            return true;
        } catch (Exception e) {
            sendMessage(player, "&cExport failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<String> getTabCompletions(Player player, int position, String current) {
        List<String> completions = new ArrayList<>();
        String currentLower = current.toLowerCase();

        if (position == 1) {
            for (SniffType type : SniffType.values()) {
                String name = type.name().toLowerCase();
                if (name.startsWith(currentLower)) {
                    completions.add(name);
                }
            }
        } else if (position == 2) {
            List<String> formats = new ArrayList<>(Arrays.asList("json", "csv", "yaml"));
            for (String format : formats) {
                if (format.startsWith(currentLower)) {
                    completions.add(format);
                }
            }
        }

        return completions;
    }
}