package me.leon.sniffer.data.container;

import lombok.Getter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class RotationData {
    private final UUID playerUUID;
    private final long startTime;

    // Rotation Tracking
    private final Map<Long, float[]> rotations = new ConcurrentHashMap<>();
    private final List<Double> yawChanges = new ArrayList<>();
    private final List<Double> pitchChanges = new ArrayList<>();
    private final List<Double> rotationSpeed = new ArrayList<>();
    private final List<Double> angleChanges = new ArrayList<>();
    // GCD Analysis
    private final List<Double> gcdValues = new ArrayList<>();
    private double lastGCDValue;
    private double gcdVariance;

    // Sensitivity Analysis
    private final List<Double> sensitivityValues = new ArrayList<>();
    private double lastSensitivity;
    private double sensitivityVariance;

    // Pattern Analysis
    private final List<Double> smoothnessValues = new ArrayList<>();
    private final List<Double> accelerationValues = new ArrayList<>();
    private final List<Double> jerkValues = new ArrayList<>();
    private double lastSmoothnessValue;

    // Cinematic Analysis
    private final List<Double> cinematicPatterns = new ArrayList<>();
    private double lastCinematicValue;

    // Violation Tracking
    private final List<String> violations = new ArrayList<>();
    private int totalViolations;

    public RotationData(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.startTime = System.currentTimeMillis();
    }

    public void addRotation(float yaw, float pitch) {
        rotations.put(System.currentTimeMillis(), new float[]{yaw, pitch});
    }

    public void addAcceleration(double acceleration) {
        accelerationValues.add(acceleration);
        if (accelerationValues.size() > 20) {
            accelerationValues.remove(0);
        }
    }

    public void addSmoothness(double smoothness) {
        smoothnessValues.add(smoothness);
        lastSmoothnessValue = smoothness;
        if (smoothnessValues.size() > 20) {
            smoothnessValues.remove(0);
        }
    }

    public void addGCDData(double gcd, double variance) {
        gcdValues.add(gcd);
        lastGCDValue = gcd;
        gcdVariance = variance;
        if (gcdValues.size() > 20) {
            gcdValues.remove(0);
        }
    }

    public void addSensitivityData(double sensitivity, double variance) {
        sensitivityValues.add(sensitivity);
        lastSensitivity = sensitivity;
        sensitivityVariance = variance;
        if (sensitivityValues.size() > 20) {
            sensitivityValues.remove(0);
        }
    }

    public void addCinematicPattern(double value) {
        cinematicPatterns.add(value);
        lastCinematicValue = value;
        if (cinematicPatterns.size() > 20) {
            cinematicPatterns.remove(0);
        }
    }

    public void addViolation(String description) {
        violations.add(System.currentTimeMillis() + ": " + description);
        totalViolations++;
    }

    public double getAverageSmoothnessLast(int samples) {
        if (smoothnessValues.isEmpty()) return 0;
        int size = Math.min(samples, smoothnessValues.size());
        return smoothnessValues.subList(smoothnessValues.size() - size, smoothnessValues.size())
                .stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }

    public double getAverageAcceleration(int samples) {
        if (accelerationValues.isEmpty()) return 0;
        int size = Math.min(samples, accelerationValues.size());
        return accelerationValues.subList(accelerationValues.size() - size, accelerationValues.size())
                .stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }

    public List<String> getRecentViolations(int count) {
        int size = Math.min(count, violations.size());
        return violations.subList(violations.size() - size, violations.size());
    }

    public double getRotationConsistency() {
        if (smoothnessValues.size() < 5) return 1.0;
        double variance = calculateVariance(smoothnessValues);
        return Math.exp(-variance); // 1 = very consistent, 0 = inconsistent
    }

    public void addRotationSpeed(double speed) {
        rotationSpeed.add(speed);
        if (rotationSpeed.size() > 20) {
            rotationSpeed.remove(0);
        }
    }

    public void addGCDValue(double gcd) {
        gcdValues.add(gcd);
        if (gcdValues.size() > 20) {
            gcdValues.remove(0);
        }
        lastGCDValue = gcd;
    }

    public void addSensitivityEstimate(double sensitivity) {
        sensitivityValues.add(sensitivity);
        if (sensitivityValues.size() > 20) {
            sensitivityValues.remove(0);
        }
        lastSensitivity = sensitivity;
    }

    public void addSmoothnessValue(double smoothness) {
        smoothnessValues.add(smoothness);
        if (smoothnessValues.size() > 20) {
            smoothnessValues.remove(0);
        }
        lastSmoothnessValue = smoothness;
    }

    public void addSnapRotation(double deltaYaw, double deltaPitch) {
        double angle = Math.max(deltaYaw, deltaPitch);
        angleChanges.add(angle);
        if (angleChanges.size() > 20) {
            angleChanges.remove(0);
        }

        // Check if this is a suspicious snap
        if (angle > 30.0) { // Configurable threshold
            addViolation("Suspicious snap rotation - Angle: " + String.format("%.2f", angle));
        }
    }

    public double[] getRecentRotationSpeeds(int count) {
        int size = Math.min(count, rotationSpeed.size());
        return rotationSpeed.subList(rotationSpeed.size() - size, rotationSpeed.size())
                .stream()
                .mapToDouble(Double::doubleValue)
                .toArray();
    }

    public int getTotalDataPoints() {
        return rotations.size() +
                gcdValues.size() +
                sensitivityValues.size() +
                smoothnessValues.size() +
                rotationSpeed.size() +
                angleChanges.size();
    }

    private double calculateVariance(List<Double> values) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0);
    }

    public void cleanup() {
        long threshold = System.currentTimeMillis() - 60000; // Keep last minute
        rotations.entrySet().removeIf(entry -> entry.getKey() < threshold);
    }

    public Map<String, Object> getAnalysisResults() {
        Map<String, Object> results = new HashMap<>();
        results.put("gcd_value", lastGCDValue);
        results.put("gcd_variance", gcdVariance);
        results.put("sensitivity", lastSensitivity);
        results.put("sensitivity_variance", sensitivityVariance);
        results.put("smoothness", lastSmoothnessValue);
        results.put("cinematic_value", lastCinematicValue);
        results.put("total_violations", totalViolations);
        results.put("rotation_consistency", getRotationConsistency());
        return results;
    }
}