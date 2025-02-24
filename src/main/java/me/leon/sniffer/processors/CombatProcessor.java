package me.leon.sniffer.processors;

import io.github.retrooper.packetevents.event.impl.PacketPlayReceiveEvent;
import io.github.retrooper.packetevents.event.impl.PacketPlaySendEvent;
import io.github.retrooper.packetevents.packettype.PacketType;
import io.github.retrooper.packetevents.packetwrappers.play.in.useentity.WrappedPacketInUseEntity;
import io.github.retrooper.packetevents.packetwrappers.play.in.flying.WrappedPacketInFlying;
import lombok.RequiredArgsConstructor;
import me.leon.sniffer.Sniffer;
import me.leon.sniffer.data.container.CombatData;
import me.leon.sniffer.utils.LocationUtil;
import me.leon.sniffer.utils.MathUtil;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class CombatProcessor implements PacketProcessor {
    private final Sniffer plugin;
    private final Map<UUID, Long> lastAttack = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastLocation = new ConcurrentHashMap<>();
    private final Map<UUID, Entity> lastTarget = new ConcurrentHashMap<>();
    private final Map<UUID, Queue<Double>> reachSamples = new ConcurrentHashMap<>();
    private final Map<UUID, List<Float>> rotationSamples = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> clickCounter = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    private static final int MAX_REACH = 4;
    private static final double SUSPICIOUS_REACH = 3.3;
    private static final int MAX_CPS = 20;
    private static final int ROTATION_SAMPLE_SIZE = 20;

    @Override
    public void processIncoming(Player player, PacketPlayReceiveEvent event) {
        UUID uuid = player.getUniqueId();
        CombatData data = plugin.getCombatCollector().getCombatData().get(uuid);
        if (data == null) return;

        byte packetId = event.getPacketId();

        if (packetId == PacketType.Play.Client.USE_ENTITY) {
            processAttack(player, event, data);
        } else if (isMovementPacket(packetId)) {
            processMovement(player, event, data);
        }
    }

    private void processAttack(Player player, PacketPlayReceiveEvent event, CombatData data) {
        UUID uuid = player.getUniqueId();
        WrappedPacketInUseEntity packet = new WrappedPacketInUseEntity(event.getNMSPacket());

        if (packet.getAction() != WrappedPacketInUseEntity.EntityUseAction.ATTACK) return;

        Entity target = packet.getEntity();
        if (!(target instanceof LivingEntity)) return;

        long currentTime = System.currentTimeMillis();
        Location playerLoc = player.getLocation();
        Location targetLoc = target.getLocation();

        // Process click speed
        processClickSpeed(uuid, currentTime, data);

        // Process reach
        double reach = calculateReach(playerLoc, targetLoc);
        processReach(uuid, reach, data);

        // Process aim
        processAim(uuid, playerLoc, targetLoc, data);

        // Update states
        lastAttack.put(uuid, currentTime);
        lastTarget.put(uuid, target);
        data.addAttack(reach, playerLoc.getYaw(), playerLoc.getPitch(), player.isOnGround(), target.getUniqueId());
    }

    private void processClickSpeed(UUID uuid, long currentTime, CombatData data) {
        Long lastClickTime = lastClick.get(uuid);
        if (lastClickTime != null) {
            long timeDiff = currentTime - lastClickTime;
            if (timeDiff < 1000) { // Within 1 second
                int clicks = clickCounter.getOrDefault(uuid, 0) + 1;
                clickCounter.put(uuid, clicks);

                if (clicks > MAX_CPS) {
                    data.addClickSpeedViolation(clicks);
                }
            } else {
                // Reset counter after 1 second
                clickCounter.put(uuid, 1);
            }
        }
        lastClick.put(uuid, currentTime);
    }

    private void processReach(UUID uuid, double reach, CombatData data) {
        Queue<Double> samples = reachSamples.computeIfAbsent(uuid, k -> new LinkedList<>());
        samples.offer(reach);
        if (samples.size() > ROTATION_SAMPLE_SIZE) {
            samples.poll();
        }

        if (reach > SUSPICIOUS_REACH) {
            data.addReachViolation(reach);
        }

        // Calculate reach consistency
        if (samples.size() >= 5) {
            double[] reachArray = samples.stream().mapToDouble(Double::doubleValue).toArray();
            double variance = MathUtil.getVariance(reachArray);
            data.addReachConsistency(variance);
        }
    }

    private void processAim(UUID uuid, Location playerLoc, Location targetLoc, CombatData data) {
        // Calculate ideal aim angles
        double[] idealRotations = MathUtil.getRotations(playerLoc, targetLoc);
        float idealYaw = (float) idealRotations[0];
        float idealPitch = (float) idealRotations[1];

        // Calculate aim accuracy
        float yawDiff = (float) Math.abs(MathUtil.getAngleDifference(playerLoc.getYaw(), idealYaw));
        float pitchDiff = Math.abs(playerLoc.getPitch() - idealPitch);

        data.addAimAccuracy(yawDiff, pitchDiff);

        // Process rotation consistency
        List<Float> rotations = rotationSamples.computeIfAbsent(uuid, k -> new ArrayList<>());
        rotations.add(playerLoc.getYaw());
        rotations.add(playerLoc.getPitch());

        while (rotations.size() > ROTATION_SAMPLE_SIZE * 2) {
            rotations.remove(0);
            rotations.remove(0);
        }

        if (rotations.size() >= 4) {
            processRotationPattern(rotations, data);
        }
    }

    private void processRotationPattern(List<Float> rotations, CombatData data) {
        // Calculate rotation smoothness
        double smoothness = 0;
        for (int i = 2; i < rotations.size(); i += 2) {
            float yawDiff = (float) Math.abs(MathUtil.getAngleDifference(rotations.get(i), rotations.get(i-2)));
            float pitchDiff = Math.abs(rotations.get(i+1) - rotations.get(i-1));

            smoothness += Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
        }
        smoothness /= (rotations.size() / 2 - 1);

        data.addRotationSmoothness(smoothness);
    }

    private void processMovement(Player player, PacketPlayReceiveEvent event, CombatData data) {
        UUID uuid = player.getUniqueId();
        WrappedPacketInFlying packet = new WrappedPacketInFlying(event.getNMSPacket());

        if (packet.isPosition()) {
            Location current = new Location(player.getWorld(), packet.getX(), packet.getY(), packet.getZ());
            lastLocation.put(uuid, current);

            // Update target position if exists
            Entity target = lastTarget.get(uuid);
            if (target != null && target.isValid()) {
                data.addTargetPosition(target.getUniqueId(), target.getLocation().getX(),
                        target.getLocation().getY(), target.getLocation().getZ());
            }
        }
    }

    private double calculateReach(Location playerLoc, Location targetLoc) {
        // Account for eye height and hitbox
        Vector eyePos = playerLoc.clone().add(0, 1.62, 0).toVector();
        Vector targetPos = targetLoc.toVector();

        // Calculate center of hitbox
        targetPos.add(new Vector(0, 0.9, 0));

        return eyePos.distance(targetPos) - 0.4; // Subtract typical entity hitbox radius
    }

    private boolean isMovementPacket(byte packetId) {
        return packetId == PacketType.Play.Client.FLYING ||
                packetId == PacketType.Play.Client.POSITION ||
                packetId == PacketType.Play.Client.POSITION_LOOK ||
                packetId == PacketType.Play.Client.LOOK;
    }

    @Override
    public void processOutgoing(Player player, PacketPlaySendEvent event) {
        // Process any relevant outgoing packets if needed
    }

    @Override
    public void cleanup(Player player) {
        UUID uuid = player.getUniqueId();
        lastAttack.remove(uuid);
        lastLocation.remove(uuid);
        lastTarget.remove(uuid);
        reachSamples.remove(uuid);
        rotationSamples.remove(uuid);
        clickCounter.remove(uuid);
        lastClick.remove(uuid);
    }

    @Override
    public void reset() {
        lastAttack.clear();
        lastLocation.clear();
        lastTarget.clear();
        reachSamples.clear();
        rotationSamples.clear();
        clickCounter.clear();
        lastClick.clear();
    }
}