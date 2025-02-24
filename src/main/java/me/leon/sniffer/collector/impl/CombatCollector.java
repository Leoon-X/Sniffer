package me.leon.sniffer.collector.impl;

import io.github.retrooper.packetevents.event.impl.PacketPlayReceiveEvent;
import io.github.retrooper.packetevents.packettype.PacketType;
import io.github.retrooper.packetevents.packetwrappers.play.in.useentity.WrappedPacketInUseEntity;
import io.github.retrooper.packetevents.packetwrappers.play.in.flying.WrappedPacketInFlying;
import lombok.Getter;
import me.leon.sniffer.Sniffer;
import me.leon.sniffer.collector.interfaces.ICollector;
import me.leon.sniffer.data.container.CombatData;
import me.leon.sniffer.data.enums.SniffType;
import me.leon.sniffer.utils.FileUtil;
import me.leon.sniffer.utils.MathUtil;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CombatCollector extends ICollector {
    private final Sniffer plugin;
    @Getter
    private final Map<UUID, CombatData> combatData;
    private final Map<UUID, Location> lastLocation;
    private final Map<UUID, Long> lastAttack;
    private final Map<UUID, Queue<Double>> reachSamples;
    private final Map<UUID, Entity> lastTarget;
    private final Map<UUID, List<Float>> rotationSamples;

    private static final int REACH_SAMPLE_SIZE = 20;
    private static final int ROTATION_SAMPLE_SIZE = 10;
    private static final double MAX_REACH = 4.0;
    private static final double SUSPICIOUS_REACH_THRESHOLD = 3.3;
    private static final double SUSPICIOUS_YAW_CHANGE = 40.0;

    public CombatCollector(Sniffer plugin) {
        super(SniffType.COMBAT, true);
        this.plugin = plugin;
        this.combatData = new ConcurrentHashMap<>();
        this.lastLocation = new ConcurrentHashMap<>();
        this.lastAttack = new ConcurrentHashMap<>();
        this.reachSamples = new ConcurrentHashMap<>();
        this.lastTarget = new ConcurrentHashMap<>();
        this.rotationSamples = new ConcurrentHashMap<>();
    }

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!isCollecting(uuid)) return;

        byte packetId = event.getPacketId();

        if (packetId == PacketType.Play.Client.USE_ENTITY) {
            handleAttackPacket(player, event);
        } else if (packetId == PacketType.Play.Client.FLYING ||
                packetId == PacketType.Play.Client.POSITION ||
                packetId == PacketType.Play.Client.POSITION_LOOK ||
                packetId == PacketType.Play.Client.LOOK) {
            handleMovementPacket(player, event);
        }
    }

    private void handleAttackPacket(Player player, PacketPlayReceiveEvent event) {
        UUID uuid = player.getUniqueId();
        CombatData data = combatData.get(uuid);
        WrappedPacketInUseEntity packet = new WrappedPacketInUseEntity(event.getNMSPacket());

        if (packet.getAction() != WrappedPacketInUseEntity.EntityUseAction.ATTACK) return;

        Entity target = packet.getEntity();
        if (!(target instanceof LivingEntity)) return;

        Location playerLoc = player.getLocation();
        Location targetLoc = target.getLocation();

        // Calculate reach
        double reach = calculateReach(playerLoc, targetLoc);
        updateReachSamples(uuid, reach);

        // Calculate aim data
        double[] rotations = MathUtil.getRotations(playerLoc, targetLoc);
        float deltaYaw = Math.abs(playerLoc.getYaw() - (float) rotations[0]);
        float deltaPitch = Math.abs(playerLoc.getPitch() - (float) rotations[1]);

        // Get timing data
        long now = System.currentTimeMillis();
        Long lastAttackTime = lastAttack.get(uuid);
        double attackDelay = lastAttackTime != null ? (now - lastAttackTime) / 1000.0 : 0.0;

        // Record combat data
        data.addAttack(reach, playerLoc.getYaw(), playerLoc.getPitch(), player.isOnGround(), target.getUniqueId());
        data.addTargetPosition(target.getUniqueId(), targetLoc.getX(), targetLoc.getY(), targetLoc.getZ());
        data.addHitDelay(attackDelay);

        // Check for suspicious activity
        checkSuspiciousReach(player, reach);
        checkSuspiciousRotation(player, deltaYaw, deltaPitch);
        analyzeAimAccuracy(player, deltaYaw, deltaPitch);

        // Update state
        lastAttack.put(uuid, now);
        lastTarget.put(uuid, target);
    }

    private void handleMovementPacket(Player player, PacketPlayReceiveEvent event) {
        UUID uuid = player.getUniqueId();
        WrappedPacketInFlying packet = new WrappedPacketInFlying(event.getNMSPacket());

        // For 1.8.x PacketEvents
        byte packetId = event.getPacketId();
        boolean hasRotation = packetId == PacketType.Play.Client.LOOK ||
                packetId == PacketType.Play.Client.POSITION_LOOK;
        boolean hasPosition = packetId == PacketType.Play.Client.POSITION ||
                packetId == PacketType.Play.Client.POSITION_LOOK;

        if (hasRotation) {
            updateRotationSamples(uuid, packet.getYaw(), packet.getPitch());
        }

        if (hasPosition) {
            lastLocation.put(uuid, new Location(
                    player.getWorld(),
                    packet.getX(),
                    packet.getY(),
                    packet.getZ(),
                    player.getLocation().getYaw(),
                    player.getLocation().getPitch()
            ));
        }
    }

    private double calculateReach(Location from, Location to) {
        // Calculate precise reach considering hitboxes
        Vector direction = to.toVector().subtract(from.toVector());
        double distanceSquared = direction.lengthSquared();

        // Account for eye height and hitbox
        from.add(0, 1.62, 0); // Player eye height
        double horizontalDistance = Math.sqrt(
                Math.pow(to.getX() - from.getX(), 2) +
                        Math.pow(to.getZ() - from.getZ(), 2)
        );

        return Math.sqrt(distanceSquared) - 0.4; // Subtract typical entity hitbox radius
    }

    private void updateReachSamples(UUID uuid, double reach) {
        Queue<Double> samples = reachSamples.computeIfAbsent(uuid, k -> new LinkedList<>());
        samples.offer(reach);
        if (samples.size() > REACH_SAMPLE_SIZE) {
            samples.poll();
        }
    }

    private void updateRotationSamples(UUID uuid, float yaw, float pitch) {
        List<Float> samples = rotationSamples.computeIfAbsent(uuid, k -> new ArrayList<>());
        samples.add(yaw);
        samples.add(pitch);
        while (samples.size() > ROTATION_SAMPLE_SIZE * 2) {
            samples.remove(0);
            samples.remove(0);
        }
    }

    private void checkSuspiciousReach(Player player, double reach) {
        if (reach > SUSPICIOUS_REACH_THRESHOLD) {
            UUID uuid = player.getUniqueId();
            CombatData data = combatData.get(uuid);
            data.updateAttackPrediction(calculateReachProbability(reach));

            if (debug) {
                FileUtil.logData(plugin, "combat", uuid,
                        String.format("Suspicious reach: %.2f blocks (threshold: %.2f)", reach, SUSPICIOUS_REACH_THRESHOLD));
            }
        }
    }

    private void checkSuspiciousRotation(Player player, float deltaYaw, float deltaPitch) {
        if (deltaYaw > SUSPICIOUS_YAW_CHANGE) {
            UUID uuid = player.getUniqueId();
            if (debug) {
                FileUtil.logData(plugin, "combat", uuid,
                        String.format("Suspicious rotation: %.2f° yaw change", deltaYaw));
            }
        }
    }

    private void analyzeAimAccuracy(Player player, float deltaYaw, float deltaPitch) {
        UUID uuid = player.getUniqueId();
        CombatData data = combatData.get(uuid);

        // Calculate aim consistency
        double consistency = 1.0 - (Math.abs(deltaYaw) + Math.abs(deltaPitch)) / 180.0;
        data.updateAimConsistency(consistency);
    }

    private double calculateReachProbability(double reach) {
        if (reach > MAX_REACH) return 0.0;
        if (reach < SUSPICIOUS_REACH_THRESHOLD) return 1.0;

        return 1.0 - ((reach - SUSPICIOUS_REACH_THRESHOLD) / (MAX_REACH - SUSPICIOUS_REACH_THRESHOLD));
    }

    @Override
    public boolean startCollecting(Player player) {
        UUID uuid = player.getUniqueId();
        if (isCollecting(uuid)) return false;

        combatData.put(uuid, new CombatData(uuid));
        lastLocation.put(uuid, player.getLocation().clone());
        reachSamples.put(uuid, new LinkedList<>());
        rotationSamples.put(uuid, new ArrayList<>());

        return true;
    }

    @Override
    public void stopCollecting(Player player) {
        UUID uuid = player.getUniqueId();
        combatData.remove(uuid);
        lastLocation.remove(uuid);
        lastAttack.remove(uuid);
        reachSamples.remove(uuid);
        lastTarget.remove(uuid);
        rotationSamples.remove(uuid);
    }

    @Override
    public boolean isCollecting(UUID uuid) {
        return combatData.containsKey(uuid);
    }

    @Override
    public void saveData(UUID uuid) {
        CombatData data = combatData.get(uuid);
        if (data != null) {
            FileUtil.saveData(plugin, "combat", uuid, data);
        }
    }

    @Override
    public void clearData(UUID uuid) {
        combatData.remove(uuid);
        lastLocation.remove(uuid);
        lastAttack.remove(uuid);
        reachSamples.remove(uuid);
        lastTarget.remove(uuid);
        rotationSamples.remove(uuid);
    }

    @Override
    public int getDataCount(UUID uuid) {
        CombatData data = combatData.get(uuid);
        return data != null ? data.getTotalDataPoints() : 0;
    }

    public Map<UUID, CombatData> getCollectedData() {
        return combatData;
    }


    public void clearData() {
        // Clear all maps and collections
        for (UUID uuid : getCollectedData().keySet()) {
            clearData(uuid);
        }
    }
}