package me.leon.sniffer.commands.subcommands;

import me.leon.sniffer.Sniffer;
import me.leon.sniffer.commands.AbstractCommand;
import me.leon.sniffer.data.enums.SniffType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InfoCommand extends AbstractCommand {

    public InfoCommand(Sniffer plugin) {
        super(plugin,
                "info",
                "sniffer.command.info",
                "/sniff info [type]",
                "Shows information about sniffing data",
                0);

        addAlias("status");
        addAlias("stats");
    }

    @Override
    public void execute(Player player, String[] args) {
        if (args.length == 0) {
            showAllInfo(player);
            return;
        }

        String typeStr = args[0].toUpperCase();
        SniffType type;

        try {
            type = SniffType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            sendMessage(player, "&cInvalid sniff type! Available types: movement, combat, rotation, block, all");
            return;
        }

        if (type == SniffType.ALL) {
            showAllInfo(player);
            return;
        }

        showTypeInfo(player, type);
    }

    private void showAllInfo(Player player) {
        sendMessage(player, "&7&m----------------------------------------");
        sendMessage(player, "&e&lSniffer Status");
        sendMessage(player, "&7&m----------------------------------------");

        Map<SniffType, Long> activeSniffers = plugin.getDataManager().getPlayerSniffers(player.getUniqueId());

        if (activeSniffers.isEmpty()) {
            sendMessage(player, "&cNo active sniffers!");
        } else {
            for (SniffType type : SniffType.values()) {
                if (type == SniffType.ALL) continue;
                if (activeSniffers.containsKey(type)) {
                    showTypeInfo(player, type);
                }
            }
        }

        sendMessage(player, "&7&m----------------------------------------");
    }

    private void showTypeInfo(Player player, SniffType type) {
        switch (type) {
            case MOVEMENT:
                showMovementInfo(player);
                break;
            case COMBAT:
                showCombatInfo(player);
                break;
            case ROTATION:
                showRotationInfo(player);
                break;
            case BLOCK:
                showBlockInfo(player);
                break;
        }
    }

    private void showMovementInfo(Player player) {
        if (!plugin.getDataManager().isSniffing(player.getUniqueId(), SniffType.MOVEMENT)) {
            sendMessage(player, "&cMovement sniffing is not active!");
            return;
        }

        int dataPoints = plugin.getMovementCollector().getDataCount(player.getUniqueId());
        sendMessage(player, "&e&lMovement Data:");
        sendMessage(player, "&7» &fData points: &e" + dataPoints);
        sendMessage(player, "&7» &fPackets: &e" + plugin.getPacketHandler().packetCounter.getOrDefault(player.getUniqueId(), 0));
        // Add more specific movement stats here
    }

    private void showCombatInfo(Player player) {
        if (!plugin.getDataManager().isSniffing(player.getUniqueId(), SniffType.COMBAT)) {
            sendMessage(player, "&cCombat sniffing is not active!");
            return;
        }

        int dataPoints = plugin.getCombatCollector().getDataCount(player.getUniqueId());
        sendMessage(player, "&e&lCombat Data:");
        sendMessage(player, "&7» &fData points: &e" + dataPoints);
        // Add more specific combat stats here
    }

    private void showRotationInfo(Player player) {
        if (!plugin.getDataManager().isSniffing(player.getUniqueId(), SniffType.ROTATION)) {
            sendMessage(player, "&cRotation sniffing is not active!");
            return;
        }

        int dataPoints = plugin.getRotationCollector().getDataCount(player.getUniqueId());
        sendMessage(player, "&e&lRotation Data:");
        sendMessage(player, "&7» &fData points: &e" + dataPoints);
        // Add more specific rotation stats here
    }

    private void showBlockInfo(Player player) {
        if (!plugin.getDataManager().isSniffing(player.getUniqueId(), SniffType.BLOCK)) {
            sendMessage(player, "&cBlock sniffing is not active!");
            return;
        }

        int dataPoints = plugin.getBlockCollector().getDataCount(player.getUniqueId());
        sendMessage(player, "&e&lBlock Data:");
        sendMessage(player, "&7» &fData points: &e" + dataPoints);
        // Add more specific block stats here
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
