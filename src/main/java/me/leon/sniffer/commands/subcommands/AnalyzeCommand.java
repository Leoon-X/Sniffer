package me.leon.sniffer.commands.subcommands;

import lombok.var;
import me.leon.sniffer.Sniffer;
import me.leon.sniffer.commands.AbstractCommand;
import me.leon.sniffer.data.enums.SniffType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class AnalyzeCommand extends AbstractCommand {

    public AnalyzeCommand(Sniffer plugin) {
        super(plugin,
                "analyze",
                "sniffer.command.analyze",
                "/sniff analyze <type>",
                "Analyzes collected data for patterns",
                1);

        addAlias("scan");
        addAlias("check");
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

        if (!plugin.getDataManager().isSniffing(player.getUniqueId(), type) && type != SniffType.ALL) {
            sendMessage(player, "&cNo data available for analysis! Start sniffing first.");
            return;
        }

        sendMessage(player, "&aAnalyzing collected data...");

        if (type == SniffType.ALL) {
            analyzeAllTypes(player);
        } else {
            analyzeType(player, type);
        }
    }

    private void analyzeAllTypes(Player player) {
        for (SniffType type : SniffType.values()) {
            if (type != SniffType.ALL && plugin.getDataManager().isSniffing(player.getUniqueId(), type)) {
                analyzeType(player, type);
            }
        }
    }

    private void analyzeType(Player player, SniffType type) {
        sendMessage(player, "&7&m----------------------------------------");
        sendMessage(player, "&e&l" + type.name() + " Analysis");
        sendMessage(player, "&7&m----------------------------------------");

        switch (type) {
            case MOVEMENT:
                analyzeMovement(player);
                break;
            case COMBAT:
                analyzeCombat(player);
                break;
            case ROTATION:
                analyzeRotation(player);
                break;
            case BLOCK:
                analyzeBlock(player);
                break;
        }
    }

    private void analyzeMovement(Player player) {
        if (!plugin.getMovementCollector().isCollecting(player.getUniqueId())) return;
        var data = plugin.getMovementCollector().getMovementData().get(player.getUniqueId());

        sendMessage(player, "&7» &fSpeed Analysis:");
        sendMessage(player, "&7  Average Speed: &e" + String.format("%.2f", data.getAverageSpeed()));
        sendMessage(player, "&7  Max Speed: &e" + String.format("%.2f", data.getMaxSpeed()));
        sendMessage(player, "&7  Suspicious Movements: &e" + data.getSuspiciousMovements());
    }

    private void analyzeCombat(Player player) {
        if (!plugin.getCombatCollector().isCollecting(player.getUniqueId())) return;
        var data = plugin.getCombatCollector().getCombatData().get(player.getUniqueId());

        sendMessage(player, "&7» &fCombat Analysis:");
        sendMessage(player, "&7  Average Reach: &e" + String.format("%.2f", data.getAverageReach()));
        sendMessage(player, "&7  Max Reach: &e" + String.format("%.2f", data.getMaxReach()));
        sendMessage(player, "&7  Suspicious Hits: &e" + data.getSuspiciousHits());
    }

    private void analyzeRotation(Player player) {
        if (!plugin.getRotationCollector().isCollecting(player.getUniqueId())) return;
        var data = plugin.getRotationCollector().getRotationData().get(player.getUniqueId());

        sendMessage(player, "&7» &fRotation Analysis:");
        //sendMessage(player, "&7  GCD Analysis: &e" + String.format("%.4f", data.getAverageGCD()));
        //sendMessage(player, "&7  Snap Count: &e" + data.getSnapRotations());
        //sendMessage(player, "&7  Smoothness: &e" + String.format("%.2f", data.getSmoothness()));
    }

    private void analyzeBlock(Player player) {
        if (!plugin.getBlockCollector().isCollecting(player.getUniqueId())) return;
        var data = plugin.getBlockCollector().getBlockData().get(player.getUniqueId());

        sendMessage(player, "&7» &fBlock Analysis:");
        //sendMessage(player, "&7  Average Break Time: &e" + String.format("%.2f", data.getAverageBreakTime()));
        //sendMessage(player, "&7  Fast Breaks: &e" + data.getFastBreaks());
        //sendMessage(player, "&7  Suspicious Reach: &e" + data.getSuspiciousReachCount());
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