package me.leon.sniffer.processors;

import io.github.retrooper.packetevents.event.impl.PacketPlayReceiveEvent;
import io.github.retrooper.packetevents.event.impl.PacketPlaySendEvent;
import io.github.retrooper.packetevents.packettype.PacketType;
import lombok.RequiredArgsConstructor;
import me.leon.sniffer.Sniffer;
import me.leon.sniffer.data.container.RotationData;
import me.leon.sniffer.utils.MathUtil;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class RotationProcessor implements PacketProcessor {
    private final Sniffer plugin;
    private final Map<UUID, float[]> lastRotations = new ConcurrentHashMap<>();
    private final Map<UUID, List<Float>> rotationBuffer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRotationTime = new ConcurrentHashMap<>();
    private final Map<UUID, List<Double>> sensitivitySamples = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lastRotationSpeed = new ConcurrentHashMap<>();

    private static final int BUFFER_SIZE = 20;
    private static final double SENS_THRESHOLD = 0.0001;
    private static final double SNAP_THRESHOLD = 30.0;

    @Override
    public void processIncoming(Player player, PacketPlayReceiveEvent event) {
        byte packetId = event.getPacketId();
        if (!isRotationPacket(packetId)) return;

        UUID uuid = player.getUniqueId();
        RotationData data = plugin.getRotationCollector().getRotationData().get(uuid);
        if (data == null) return;

        // Extract rotations from packet
        float[] rotations = extractRotations(event);
        if (rotations == null) return;

        processRotations(player, rotations[0], rotations[1], data);
    }

    private void processRotations(Player player, float yaw, float pitch, RotationData data) {
        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Normalize yaw to 0-360
        yaw = normalizeYaw(yaw);

        // Check pitch bounds
        if (Math.abs(pitch) > 90.0f) {
            data.addViolation("Illegal pitch: " + pitch);
            return;
        }

        // Get previous rotations
        float[] lastRots = lastRotations.get(uuid);
        Long lastTime = lastRotationTime.get(uuid);

        if (lastRots != null && lastTime != null) {
            // Calculate deltas
            double deltaYaw = calculateDeltaYaw(yaw, lastRots[0]);
            double deltaPitch = Math.abs(pitch - lastRots[1]);
            double deltaTime = (currentTime - lastTime) / 1000.0;

            // Add to rotation buffer for analysis
            updateRotationBuffer(uuid, deltaYaw, deltaPitch);

            // Process aim pattern
            processAimPattern(uuid, deltaYaw, deltaPitch, deltaTime, data);

            // Process sensitivity
            if (deltaPitch > SENS_THRESHOLD) {
                processSensitivity(uuid, deltaYaw, deltaPitch, data);
            }

            // Process cinematic pattern
            processCinematicPattern(uuid, data);

            // Calculate GCD if we have enough samples
            List<Float> buffer = rotationBuffer.get(uuid);
            if (buffer != null && buffer.size() >= 4) {
                processGCD(buffer, data);
            }
        }

        // Update states
        lastRotations.put(uuid, new float[]{yaw, pitch});
        lastRotationTime.put(uuid, currentTime);
        data.addRotation(yaw, pitch);
    }

    private void updateRotationBuffer(UUID uuid, double deltaYaw, double deltaPitch) {
        List<Float> buffer = rotationBuffer.computeIfAbsent(uuid, k -> new ArrayList<>());
        buffer.add((float) deltaYaw);
        buffer.add((float) deltaPitch);

        while (buffer.size() > BUFFER_SIZE * 2) {
            buffer.remove(0);
            buffer.remove(0);
        }
    }

    private void processAimPattern(UUID uuid, double deltaYaw, double deltaPitch, double deltaTime, RotationData data) {
        if (deltaTime <= 0) return;

        // Calculate rotation speed vector
        double rotationSpeed = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch) / deltaTime;
        Double lastSpeed = lastRotationSpeed.get(uuid);

        if (lastSpeed != null) {
            // Calculate acceleration
            double acceleration = (rotationSpeed - lastSpeed) / deltaTime;
            data.addAcceleration(acceleration);

            // Check for potential aim assistance
            if (rotationSpeed > SNAP_THRESHOLD) {
                analyzePotentialSnap(uuid, rotationSpeed, acceleration, data);
            }

            // Analyze smoothness
            analyzeSmoothness(uuid, rotationSpeed, lastSpeed, data);
        }

        lastRotationSpeed.put(uuid, rotationSpeed);
    }

    private void analyzePotentialSnap(UUID uuid, double speed, double acceleration, RotationData data) {
        List<Float> buffer = rotationBuffer.get(uuid);
        if (buffer == null || buffer.size() < 4) return;

        // Calculate consistency in the snap
        double[] speeds = new double[buffer.size() / 2];
        for (int i = 0; i < buffer.size(); i += 2) {
            speeds[i/2] = Math.sqrt(buffer.get(i) * buffer.get(i) + buffer.get(i+1) * buffer.get(i+1));
        }

        double variance = MathUtil.getVariance(speeds);
        if (variance < 0.1 && speed > SNAP_THRESHOLD) {
            data.addViolation("Suspicious snap rotation - Speed: " + String.format("%.2f", speed) +
                    ", Variance: " + String.format("%.5f", variance));
        }
    }

    private void analyzeSmoothness(UUID uuid, double currentSpeed, double lastSpeed, RotationData data) {
        // Calculate jerk (rate of change of acceleration)
        double jerk = Math.abs(currentSpeed - lastSpeed);

        // A perfectly smooth rotation would have very low jerk
        double smoothness = Math.exp(-jerk);
        data.addSmoothness(smoothness);

        // Check for unnaturally smooth rotations
        List<Float> buffer = rotationBuffer.get(uuid);
        if (buffer != null && buffer.size() >= 6) {
            analyzeRotationConsistency(buffer, data);
        }
    }

    private void analyzeRotationConsistency(List<Float> buffer, RotationData data) {
        double[] yawChanges = new double[buffer.size() / 2];
        double[] pitchChanges = new double[buffer.size() / 2];

        for (int i = 0; i < buffer.size(); i += 2) {
            yawChanges[i/2] = buffer.get(i);
            pitchChanges[i/2] = buffer.get(i+1);
        }

        double yawVariance = MathUtil.getVariance(yawChanges);
        double pitchVariance = MathUtil.getVariance(pitchChanges);

        // Check for suspicious consistency
        if (yawVariance < 0.01 && pitchVariance < 0.01) {
            data.addViolation("Suspicious rotation consistency - YawVar: " +
                    String.format("%.5f", yawVariance) + ", PitchVar: " +
                    String.format("%.5f", pitchVariance));
        }
    }

    private void processSensitivity(UUID uuid, double deltaYaw, double deltaPitch, RotationData data) {
        double sensitivity = deltaYaw / deltaPitch;
        List<Double> samples = sensitivitySamples.computeIfAbsent(uuid, k -> new ArrayList<>());
        samples.add(sensitivity);

        if (samples.size() > 20) {
            samples.remove(0);
        }

        if (samples.size() >= 5) {
            double[] sensArray = samples.stream().mapToDouble(Double::doubleValue).toArray();
            double variance = MathUtil.getVariance(sensArray);
            data.addSensitivityData(sensitivity, variance);
        }
    }

    private void processCinematicPattern(UUID uuid, RotationData data) {
        List<Float> buffer = rotationBuffer.get(uuid);
        if (buffer == null || buffer.size() < 6) return;

        // Analyze rotation curve smoothness
        double smoothness = calculateCurveSmoothing(buffer);
        data.addCinematicPattern(smoothness);
    }

    private double calculateCurveSmoothing(List<Float> buffer) {
        double smoothness = 0;
        for (int i = 2; i < buffer.size() - 2; i += 2) {
            double yaw1 = buffer.get(i-2);
            double yaw2 = buffer.get(i);
            double yaw3 = buffer.get(i+2);

            double pitch1 = buffer.get(i-1);
            double pitch2 = buffer.get(i+1);
            double pitch3 = buffer.get(i+3);

            // Calculate second derivatives
            double yawCurvature = Math.abs(yaw3 - 2*yaw2 + yaw1);
            double pitchCurvature = Math.abs(pitch3 - 2*pitch2 + pitch1);

            smoothness += Math.sqrt(yawCurvature * yawCurvature + pitchCurvature * pitchCurvature);
        }
        return smoothness / ((buffer.size() - 4) / 2);
    }

    private void processGCD(List<Float> buffer, RotationData data) {
        List<Double> gcdValues = new ArrayList<>();

        for (int i = 0; i < buffer.size() - 2; i += 2) {
            double deltaYaw = buffer.get(i);
            double deltaPitch = buffer.get(i+1);

            if (Math.abs(deltaPitch) > SENS_THRESHOLD) {
                long yawScaled = (long)(deltaYaw * 10000);
                long pitchScaled = (long)(deltaPitch * 10000);
                double gcd = MathUtil.getGCD(yawScaled, pitchScaled) / 10000.0;
                gcdValues.add(gcd);
            }
        }

        if (!gcdValues.isEmpty()) {
            double[] gcdArray = gcdValues.stream().mapToDouble(Double::doubleValue).toArray();
            double gcdVariance = MathUtil.getVariance(gcdArray);
            data.addGCDData(gcdArray[gcdArray.length-1], gcdVariance);
        }
    }

    private float normalizeYaw(float yaw) {
        yaw %= 360;
        if (yaw < 0) yaw += 360;
        return yaw;
    }

    private double calculateDeltaYaw(float current, float last) {
        double delta = Math.abs(current - last);
        return delta > 180 ? 360 - delta : delta;
    }

    private boolean isRotationPacket(byte packetId) {
        return packetId == PacketType.Play.Client.LOOK ||
                packetId == PacketType.Play.Client.POSITION_LOOK;
    }

    private float[] extractRotations(PacketPlayReceiveEvent event) {
        // Extract rotations from packet data
        // Implementation depends on your packet event system
        return null; // Placeholder
    }

    @Override
    public void processOutgoing(Player player, PacketPlaySendEvent event) {
        // Not needed for rotation analysis
    }

    @Override
    public void cleanup(Player player) {
        UUID uuid = player.getUniqueId();
        lastRotations.remove(uuid);
        rotationBuffer.remove(uuid);
        lastRotationTime.remove(uuid);
        sensitivitySamples.remove(uuid);
        lastRotationSpeed.remove(uuid);
    }

    @Override
    public void reset() {
        lastRotations.clear();
        rotationBuffer.clear();
        lastRotationTime.clear();
        sensitivitySamples.clear();
        lastRotationSpeed.clear();
    }
}