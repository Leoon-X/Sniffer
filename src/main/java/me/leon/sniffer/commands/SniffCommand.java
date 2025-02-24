package me.leon.sniffer.commands;

import me.leon.sniffer.Sniffer;
import me.leon.sniffer.commands.AbstractCommand;
import me.leon.sniffer.data.enums.SniffType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SniffCommand extends AbstractCommand {

    public SniffCommand(Sniffer plugin) {
        super(plugin,
                "sniff",
                "sniffer.command.sniff",
                "/sniff <type> [player]",
                "Toggle sniffing for a specific category",
                1);

        addAlias("toggle");
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

        Player target = player;
        if (args.length > 1 && player.hasPermission("sniffer.sniff.others")) {
            target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sendMessage(player, "&cPlayer not found!");
                return;
            }
        }

        if (plugin.getDataManager().isSniffing(target.getUniqueId(), type)) {
            plugin.getServer().dispatchCommand(player, "sniff stop " + typeStr.toLowerCase());
        } else {
            plugin.getServer().dispatchCommand(player, "sniff start " + typeStr.toLowerCase());
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
        } else if (position == 2 && player.hasPermission("sniffer.sniff.others")) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(currentLower)) {
                    completions.add(p.getName());
                }
            }
        }

        return completions;
    }
}