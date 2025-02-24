package me.leon.sniffer.collector.impl;

import io.github.retrooper.packetevents.event.impl.PacketPlayReceiveEvent;
import io.github.retrooper.packetevents.packettype.PacketType;
import io.github.retrooper.packetevents.packetwrappers.play.in.flying.WrappedPacketInFlying;
import lombok.Getter;
import me.leon.sniffer.Sniffer;
import me.leon.sniffer.collector.interfaces.ICollector;
import me.leon.sniffer.data.container.RotationData;
import me.leon.sniffer.data.enums.SniffType;
import me.leon.sniffer.utils.FileUtil;
import me.leon.sniffer.utils.MathUtil;

import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RotationCollector extends ICollector {
    private final Sniffer plugin;
    @Getter private final Map<UUID, RotationData> rotationData;
    private final Map<UUID, float[]> lastRotation;
    private final Map<UUID, Long> lastRotationTime;
    private final Map<UUID, Queue<Float>> gcdSamples;

    private static final int GCD_SAMPLE_SIZE = 20;
    private static final double SENSITIVITY_THRESHOLD = 0.0001;
    private static final float MAX_PITCH = 90.0f;
    private static final double SNAP_THRESHOLD = 30.0;

    public RotationCollector(Sniffer plugin) {
        super(SniffType.ROTATION, true);
        this.plugin = plugin;
        this.rotationData = new ConcurrentHashMap<>();
        this.lastRotation = new ConcurrentHashMap<>();
        this.lastRotationTime = new ConcurrentHashMap<>();
        this.gcdSamples = new ConcurrentHashMap<>();
    }

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        byte packetId = event.getPacketId();

        if (packetId == PacketType.Play.Client.FLYING ||
                packetId == PacketType.Play.Client.POSITION ||
                packetId == PacketType.Play.Client.POSITION_LOOK ||
                packetId == PacketType.Play.Client.LOOK) {

            Player player = (Player) event.getPlayer();
            UUID uuid = player.getUniqueId();

            if (!isCollecting(uuid)) return;

            WrappedPacketInFlying packet = new WrappedPacketInFlying(event.getNMSPacket());

            // Check if packet contains rotation data
            boolean hasRotation = packetId == PacketType.Play.Client.LOOK ||
                    packetId == PacketType.Play.Client.POSITION_LOOK;

            if (hasRotation) {
                handleRotation(player, packet.getYaw(), packet.getPitch());
            }
        }
    }

    private void handleRotation(Player player, float yaw, float pitch) {
        UUID uuid = player.getUniqueId();
        RotationData data = rotationData.get(uuid);
        long currentTime = System.currentTimeMillis();

        // Normalize yaw to 0-360
        yaw = yaw % 360;
        if (yaw < 0) yaw += 360;

        // Check for invalid pitch
        if (Math.abs(pitch) > MAX_PITCH) {
            if (debug) {
                FileUtil.logData(plugin, "rotation", uuid,
                        String.format("Invalid pitch detected: %.2f", pitch));
            }
            return;
        }

        float[] lastRot = lastRotation.get(uuid);
        Long lastTime = lastRotationTime.get(uuid);

        if (lastRot != null && lastTime != null) {
            // Calculate deltas
            float deltaYaw = (float) MathUtil.getAngleDifference(yaw, lastRot[0]);
            float deltaPitch = Math.abs(pitch - lastRot[1]);
            double timeDelta = (currentTime - lastTime) / 1000.0;


            // Calculate rotation speed
            double rotationSpeed = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch) / timeDelta;
            data.addRotationSpeed(rotationSpeed);

            // Check for snap rotations
            if (rotationSpeed > SNAP_THRESHOLD) {
                analyzeSnapRotation(player, deltaYaw, deltaPitch, rotationSpeed);
            }

            // Calculate GCD
            if (deltaPitch > SENSITIVITY_THRESHOLD) {
                float gcd = calculateGCD(deltaYaw, deltaPitch);
                updateGCDSamples(uuid, gcd);
                data.addGCDValue(gcd);
            }

            // Analyze sensitivity
            if (deltaPitch > SENSITIVITY_THRESHOLD) {
                double sensitivity = deltaYaw / deltaPitch;
                data.addSensitivityEstimate(sensitivity);
            }

            // Calculate smoothness
            double smoothness = calculateSmoothness(uuid, rotationSpeed);
            data.addSmoothnessValue(smoothness);

            data.addSnapRotation(deltaYaw, deltaPitch);
            data.addRotation(yaw, pitch);
        }

        // Update state
        lastRotation.put(uuid, new float[]{yaw, pitch});
        lastRotationTime.put(uuid, currentTime);
    }

    private void analyzeSnapRotation(Player player, float deltaYaw, float deltaPitch, double speed) {
        UUID uuid = player.getUniqueId();
        RotationData data = rotationData.get(uuid);

        // Add the snap rotation with just the required data
        data.addSnapRotation(deltaYaw, deltaPitch);

        // Check for suspicious patterns
        if (speed > SNAP_THRESHOLD && debug) {
            FileUtil.logData(plugin, "rotation", uuid,
                    String.format("Suspicious snap rotation: speed=%.2f, deltaYaw=%.2f, deltaPitch=%.2f",
                            speed, deltaYaw, deltaPitch));
        }
    }

    private boolean isSnapAimPattern(float deltaYaw, float deltaPitch, double speed) {
        // Complex analysis of whether this snap rotation matches known cheat patterns
        return speed > SNAP_THRESHOLD * 2 &&
                (deltaYaw > SNAP_THRESHOLD || deltaPitch > SNAP_THRESHOLD / 2);
    }

    private float calculateGCD(float deltaYaw, float deltaPitch) {
        // Convert to a larger scale to work with integers
        long yawScaled = (long) (deltaYaw * 10000);
        long pitchScaled = (long) (deltaPitch * 10000);

        // Calculate GCD
        return (float) (MathUtil.getGCD(yawScaled, pitchScaled) / 10000.0);
    }

    private void updateGCDSamples(UUID uuid, float gcd) {
        Queue<Float> samples = gcdSamples.computeIfAbsent(uuid, k -> new LinkedList<>());
        samples.offer(gcd);
        if (samples.size() > GCD_SAMPLE_SIZE) {
            samples.poll();
        }
    }

    private double calculateSmoothness(UUID uuid, double currentSpeed) {
        RotationData data = rotationData.get(uuid);
        double[] speeds = data.getRecentRotationSpeeds(5);
        if (speeds.length < 2) return 1.0;

        double varianceSum = 0;
        for (int i = 1; i < speeds.length; i++) {
            double diff = speeds[i] - speeds[i-1];
            varianceSum += diff * diff;
        }

        // Normalize smoothness to 0-1 range
        return Math.exp(-varianceSum / speeds.length);
    }

    @Override
    public boolean startCollecting(Player player) {
        UUID uuid = player.getUniqueId();
        if (isCollecting(uuid)) return false;

        rotationData.put(uuid, new RotationData(uuid));
        lastRotation.put(uuid, new float[]{player.getLocation().getYaw(), player.getLocation().getPitch()});
        lastRotationTime.put(uuid, System.currentTimeMillis());
        gcdSamples.put(uuid, new LinkedList<>());

        return true;
    }

    @Override
    public void stopCollecting(Player player) {
        UUID uuid = player.getUniqueId();
        rotationData.remove(uuid);
        lastRotation.remove(uuid);
        lastRotationTime.remove(uuid);
        gcdSamples.remove(uuid);
    }

    @Override
    public boolean isCollecting(UUID uuid) {
        return rotationData.containsKey(uuid);
    }

    @Override
    public void saveData(UUID uuid) {
        RotationData data = rotationData.get(uuid);
        if (data != null) {
            FileUtil.saveData(plugin, "rotation", uuid, data);
        }
    }

    @Override
    public void clearData(UUID uuid) {
        rotationData.remove(uuid);
        lastRotation.remove(uuid);
        lastRotationTime.remove(uuid);
        gcdSamples.remove(uuid);
    }

    @Override
    public int getDataCount(UUID uuid) {
        RotationData data = rotationData.get(uuid);
        return data != null ? data.getTotalDataPoints() : 0;
    }

    public Map<UUID, RotationData> getCollectedData() {
        return rotationData;
    }

    public void clearData() {
        // Clear all maps and collections
        for (UUID uuid : getCollectedData().keySet()) {
            clearData(uuid);
        }
    }
}