package me.leon.sniffer.commands.subcommands;

import me.leon.sniffer.Sniffer;
import me.leon.sniffer.commands.AbstractCommand;
import me.leon.sniffer.data.enums.SniffType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StopCommand extends AbstractCommand {

    public StopCommand(Sniffer plugin) {
        super(plugin,
                "stop",
                "sniffer.command.stop",
                "/sniff stop <type>",
                "Stop sniffing packets for a specific category",
                1);

        addAlias("end");
        addAlias("halt");
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

        if (type == SniffType.ALL) {
            int stopped = 0;
            for (SniffType t : SniffType.values()) {
                if (t != SniffType.ALL && stopSniffing(player, t)) {
                    stopped++;
                }
            }

            if (stopped > 0) {
                sendMessage(player, String.format("&aStopped sniffing &e%d &atypes of data", stopped));
            } else {
                sendMessage(player, "&cNo active sniffers to stop!");
            }
            return;
        }

        if (stopSniffing(player, type)) {
            sendMessage(player, "&aStopped sniffing &e" + type.name().toLowerCase() + " &adata");
        } else {
            sendMessage(player, "&cNot currently sniffing " + type.name().toLowerCase() + " data!");
        }
    }

    private boolean stopSniffing(Player player, SniffType type) {
        if (!plugin.getDataManager().isSniffing(player.getUniqueId(), type)) {
            return false;
        }

        switch (type) {
            case MOVEMENT:
                plugin.getMovementCollector().stopCollecting(player);
                break;
            case COMBAT:
                plugin.getCombatCollector().stopCollecting(player);
                break;
            case ROTATION:
                plugin.getRotationCollector().stopCollecting(player);
                break;
            case BLOCK:
                plugin.getBlockCollector().stopCollecting(player);
                break;
        }

        // Save the data before stopping
        plugin.getDataManager().saveData(player.getUniqueId(), type);
        plugin.getDataManager().stopSniffing(player.getUniqueId(), type);
        return true;
    }

    @Override
    public List<String> getTabCompletions(Player player, int position, String current) {
        if (position == 1) {
            List<String> completions = new ArrayList<>();
            String currentLower = current.toLowerCase();
            Map<SniffType, Long> activeSniffers = plugin.getDataManager()
                    .getPlayerSniffers(player.getUniqueId());

            // Only show types that are currently active
            for (SniffType type : SniffType.values()) {
                String name = type.name().toLowerCase();
                if (name.startsWith(currentLower) &&
                        (type == SniffType.ALL || activeSniffers.containsKey(type))) {
                    completions.add(name);
                }
            }

            return completions;
        }

        return super.getTabCompletions(player, position, current);
    }
}