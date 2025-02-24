package me.leon.sniffer.commands.subcommands;

import me.leon.sniffer.Sniffer;
import me.leon.sniffer.commands.AbstractCommand;
import me.leon.sniffer.data.enums.SniffType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class StartCommand extends AbstractCommand {

    public StartCommand(Sniffer plugin) {
        super(plugin,
                "start",
                "sniffer.command.start",
                "/sniff start <type>",
                "Start sniffing packets for a specific category",
                1);

        addAlias("s");
        addAlias("begin");
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
            int started = 0;
            for (SniffType t : SniffType.values()) {
                if (t != SniffType.ALL && startSniffing(player, t)) {
                    started++;
                }
            }
            sendMessage(player, String.format("&aStarted sniffing &e%d &atypes of data", started));
            return;
        }

        if (startSniffing(player, type)) {
            sendMessage(player, "&aStarted sniffing &e" + type.name().toLowerCase() + " &adata");
        } else {
            sendMessage(player, "&cAlready sniffing " + type.name().toLowerCase() + " data!");
        }
    }

    private boolean startSniffing(Player player, SniffType type) {
        if (plugin.getDataManager().isSniffing(player.getUniqueId(), type)) {
            return false;
        }

        switch (type) {
            case MOVEMENT:
                plugin.getMovementCollector().startCollecting(player);
                break;
            case COMBAT:
                plugin.getCombatCollector().startCollecting(player);
                break;
            case ROTATION:
                plugin.getRotationCollector().startCollecting(player);
                break;
            case BLOCK:
                plugin.getBlockCollector().startCollecting(player);
                break;
        }

        plugin.getDataManager().startSniffing(player.getUniqueId(), type);
        return true;
    }

    @Override
    public List<String> getTabCompletions(Player player, int position, String current) {
        if (position == 1) {
            List<String> completions = new ArrayList<>();
            String currentLower = current.toLowerCase();

            for (SniffType type : SniffType.values()) {
                String name = type.name().toLowerCase();
                if (name.startsWith(currentLower)) {
                    completions.add(name);
                }
            }

            return completions;
        }

        return super.getTabCompletions(player, position, current);
    }
}