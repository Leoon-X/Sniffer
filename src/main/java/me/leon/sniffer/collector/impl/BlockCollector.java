package me.leon.sniffer.collector.impl;

import io.github.retrooper.packetevents.event.impl.PacketPlayReceiveEvent;
import io.github.retrooper.packetevents.packettype.PacketType;
import io.github.retrooper.packetevents.packetwrappers.play.in.blockdig.WrappedPacketInBlockDig;
import io.github.retrooper.packetevents.packetwrappers.play.in.blockplace.WrappedPacketInBlockPlace;
import lombok.Getter;
import me.leon.sniffer.Sniffer;
import me.leon.sniffer.collector.interfaces.ICollector;
import me.leon.sniffer.data.container.BlockData;
import me.leon.sniffer.data.enums.SniffType;
import me.leon.sniffer.utils.FileUtil;
import me.leon.sniffer.utils.LocationUtil;
import me.leon.sniffer.utils.MathUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BlockCollector extends ICollector {
    private final Sniffer plugin;
    @Getter
    private final Map<UUID, BlockData> blockData;
    private final Map<UUID, Location> lastBlockInteraction;
    private final Map<UUID, Long> lastInteractionTime;
    private final Map<UUID, Queue<Double>> reachSamples;

    private static final double MAX_REACH = 5.0;
    private static final double SUSPICIOUS_REACH_THRESHOLD = 4.5;
    private static final int MIN_BREAK_TIME = 50; // ms
    private static final int REACH_SAMPLE_SIZE = 20;

    public BlockCollector(Sniffer plugin) {
        super(SniffType.BLOCK, true);
        this.plugin = plugin;
        this.blockData = new ConcurrentHashMap<>();
        this.lastBlockInteraction = new ConcurrentHashMap<>();
        this.lastInteractionTime = new ConcurrentHashMap<>();
        this.reachSamples = new ConcurrentHashMap<>();
    }

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!isCollecting(uuid)) return;

        byte packetId = event.getPacketId();

        if (packetId == PacketType.Play.Client.BLOCK_DIG) {
            handleBlockDig(player, event);
        } else if (packetId == PacketType.Play.Client.BLOCK_PLACE) {
            handleBlockPlace(player, event);
        }
    }

    private void handleBlockDig(Player player, PacketPlayReceiveEvent event) {
        UUID uuid = player.getUniqueId();
        BlockData data = blockData.get(uuid);
        WrappedPacketInBlockDig packet = new WrappedPacketInBlockDig(event.getNMSPacket());

        Location blockLoc = new Location(player.getWorld(),
                packet.getBlockPosition().getX(),
                packet.getBlockPosition().getY(),
                packet.getBlockPosition().getZ());

        double reach = calculateReach(player.getLocation(), blockLoc);
        updateReachSamples(uuid, reach);

        long now = System.currentTimeMillis();
        Long lastTime = lastInteractionTime.get(uuid);
        double timeDelta = lastTime != null ? (now - lastTime) / 1000.0 : 0.0;

        Block block = blockLoc.getBlock();
        WrappedPacketInBlockDig.PlayerDigType digType = packet.getDigType();

        data.addBlockBreak(blockLoc, block.getType(), reach, timeDelta);

        // Check patterns
        if (digType == WrappedPacketInBlockDig.PlayerDigType.START_DESTROY_BLOCK) {
            checkSuspiciousBreakPattern(player, block, timeDelta);
        } else if (digType == WrappedPacketInBlockDig.PlayerDigType.STOP_DESTROY_BLOCK) {
            checkBreakTime(player, block, timeDelta);
        }

        checkSuspiciousReach(player, reach);

        lastBlockInteraction.put(uuid, blockLoc);
        lastInteractionTime.put(uuid, now);
    }

    private void handleBlockPlace(Player player, PacketPlayReceiveEvent event) {
        UUID uuid = player.getUniqueId();
        BlockData data = blockData.get(uuid);
        WrappedPacketInBlockPlace packet = new WrappedPacketInBlockPlace(event.getNMSPacket());

        Location blockLoc = new Location(player.getWorld(),
                packet.getBlockPosition().getX(),
                packet.getBlockPosition().getY(),
                packet.getBlockPosition().getZ());

        double reach = calculateReach(player.getLocation(), blockLoc);
        updateReachSamples(uuid, reach);

        long now = System.currentTimeMillis();
        Long lastTime = lastInteractionTime.get(uuid);
        double timeDelta = lastTime != null ? (now - lastTime) / 1000.0 : 0.0;

        Material material = player.getItemInHand().getType();
        data.addBlockPlace(blockLoc, material, reach, timeDelta);

        checkSuspiciousReach(player, reach);
        checkPlacePattern(player, blockLoc, timeDelta);

        lastBlockInteraction.put(uuid, blockLoc);
        lastInteractionTime.put(uuid, now);
    }

    private double calculateReach(Location playerLoc, Location blockLoc) {
        // Account for eye height
        playerLoc = playerLoc.clone().add(0, 1.62, 0);
        return playerLoc.distance(blockLoc);
    }

    private void updateReachSamples(UUID uuid, double reach) {
        Queue<Double> samples = reachSamples.computeIfAbsent(uuid, k -> new LinkedList<>());
        samples.offer(reach);
        if (samples.size() > REACH_SAMPLE_SIZE) {
            samples.poll();
        }
    }

    private void checkSuspiciousReach(Player player, double reach) {
        if (reach > SUSPICIOUS_REACH_THRESHOLD) {
            UUID uuid = player.getUniqueId();
            BlockData data = blockData.get(uuid);
            data.addViolation("Suspicious reach: " + String.format("%.2f", reach));

            if (debug) {
                FileUtil.logData(plugin, "block", uuid,
                        String.format("Suspicious reach: %.2f blocks (threshold: %.2f)", reach, SUSPICIOUS_REACH_THRESHOLD));
            }
        }
    }

    private void checkSuspiciousBreakPattern(Player player, Block block, double timeDelta) {
        UUID uuid = player.getUniqueId();
        BlockData data = blockData.get(uuid);

        if (timeDelta < MIN_BREAK_TIME / 1000.0) {
            data.addViolation("Fast break: " + String.format("%.3f", timeDelta));

            if (debug) {
                FileUtil.logData(plugin, "block", uuid,
                        String.format("Suspicious break time: %.3fs (minimum: %.3fs)", timeDelta, MIN_BREAK_TIME / 1000.0));
            }
        }
    }

    private void checkBreakTime(Player player, Block block, double timeDelta) {
        // Calculate expected break time based on material and tool
        double expectedTime = calculateExpectedBreakTime(block.getType(), player.getItemInHand().getType());

        if (timeDelta < expectedTime * 0.8) { // 20% tolerance
            UUID uuid = player.getUniqueId();
            BlockData data = blockData.get(uuid);
            data.addViolation("Fast break: " + String.format("%.3f (expected: %.3f)", timeDelta, expectedTime));

            if (debug) {
                FileUtil.logData(plugin, "block", uuid,
                        String.format("Break time too fast: %.3fs (expected: %.3fs)", timeDelta, expectedTime));
            }
        }
    }

    private void checkPlacePattern(Player player, Location location, double timeDelta) {
        UUID uuid = player.getUniqueId();
        BlockData data = blockData.get(uuid);
        Location lastLoc = lastBlockInteraction.get(uuid);

        if (lastLoc != null && timeDelta < 0.05) { // 50ms between places
            data.addViolation("Fast place: " + String.format("%.3f", timeDelta));

            if (debug) {
                FileUtil.logData(plugin, "block", uuid,
                        String.format("Suspicious place timing: %.3fs", timeDelta));
            }
        }
    }

    private double calculateExpectedBreakTime(Material blockType, Material toolType) {
        // This would be a complex calculation based on block hardness, tool efficiency, etc.
        // Simplified version for example
        double baseTime = 0.5;

        switch (blockType) {
            case OBSIDIAN:
                return baseTime * 25;
            case STONE:
                return baseTime * (toolType == Material.DIAMOND_PICKAXE ? 1 : 2);
            case DIRT:
            case GRASS:
                return baseTime * 0.5;
            default:
                return baseTime;
        }
    }

    @Override
    public boolean startCollecting(Player player) {
        UUID uuid = player.getUniqueId();
        if (isCollecting(uuid)) return false;

        blockData.put(uuid, new BlockData(uuid));
        return true;
    }

    @Override
    public void stopCollecting(Player player) {
        UUID uuid = player.getUniqueId();
        blockData.remove(uuid);
        lastBlockInteraction.remove(uuid);
        lastInteractionTime.remove(uuid);
        reachSamples.remove(uuid);
    }

    @Override
    public boolean isCollecting(UUID uuid) {
        return blockData.containsKey(uuid);
    }

    @Override
    public void saveData(UUID uuid) {
        BlockData data = blockData.get(uuid);
        if (data != null) {
            FileUtil.saveData(plugin, "block", uuid, data);
        }
    }

    @Override
    public void clearData(UUID uuid) {
        blockData.remove(uuid);
        lastBlockInteraction.remove(uuid);
        lastInteractionTime.remove(uuid);
        reachSamples.remove(uuid);
    }

    @Override
    public int getDataCount(UUID uuid) {
        BlockData data = blockData.get(uuid);
        return data != null ? data.getTotalDataPoints() : 0;
    }

    public Map<UUID, BlockData> getCollectedData() {
        return blockData;
    }


    public void clearData() {
        // Clear all maps and collections
        for (UUID uuid : getCollectedData().keySet()) {
            clearData(uuid);
        }
    }
}