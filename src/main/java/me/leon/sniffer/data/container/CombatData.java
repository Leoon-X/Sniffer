package me.leon.sniffer.data.container;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class CombatData {
    private final UUID playerUUID;
    private final long startTime;

    // Hit Data
    private final Map<Long, Double> reachDistances = new ConcurrentHashMap<>();
    private final Map<Long, float[]> hitAngles = new ConcurrentHashMap<>();
    private final Map<UUID, List<Double>> targetSpecificReach = new ConcurrentHashMap<>();
    private final List<Vector> hitVectors = new ArrayList<>();

    // Aim Analysis
    private final List<Double> aimConsistency = new ArrayList<>();
    private final List<Double> predictionAccuracy = new ArrayList<>();
    private final List<Double> rotationSpeed = new ArrayList<>();
    private final List<Double> angleChanges = new ArrayList<>();

    // Click Analysis
    private final Map<Long, Integer> clickCounts = new ConcurrentHashMap<>();
    private final List<Double> clickIntervals = new ArrayList<>();
    private final List<Double> doubleClickTimes = new ArrayList<>();
    private final double[] cpsBuffer = new double[20];
    private int cpsIndex = 0;

    // Metrics
    private double averageReach;
    private double reachVariance;
    private double lastAimConsistency;
    private double lastPredictionScore;
    private int suspiciousHits;

    // Violations
    private final List<String> violations = new ArrayList<>();
    private int totalViolations;

    public CombatData(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.startTime = System.currentTimeMillis();
    }

    // Methods needed by CombatProcessor
    public void addAttack(double reach, float yaw, float pitch, boolean onGround, UUID targetId) {
        long time = System.currentTimeMillis();
        reachDistances.put(time, reach);
        hitAngles.put(time, new float[]{yaw, pitch});

        // Update target-specific reach
        targetSpecificReach.computeIfAbsent(targetId, k -> new ArrayList<>()).add(reach);

        // Update statistics
        updateReachStats();
    }

    public void addTargetPosition(UUID targetId, double x, double y, double z) {
        hitVectors.add(new Vector(x, y, z));
        if (hitVectors.size() > 20) hitVectors.remove(0);
    }

    public void addClickSpeedViolation(int clicks) {
        addViolation("Click speed violation: " + clicks + " CPS");
    }

    public void addReachViolation(double reach) {
        addViolation("Reach violation: " + String.format("%.2f", reach));
        suspiciousHits++;
    }

    public void addRotationSmoothness(double smoothness) {
        rotationSpeed.add(smoothness);
        if (rotationSpeed.size() > 20) rotationSpeed.remove(0);
    }

    public void addAimAccuracy(float yawDiff, float pitchDiff) {
        double accuracy = 1.0 - (yawDiff + pitchDiff) / 180.0;
        aimConsistency.add(accuracy);
        if (aimConsistency.size() > 20) aimConsistency.remove(0);
        lastAimConsistency = accuracy;
    }

    public void addHitDelay(double delay) {
        clickIntervals.add(delay);
        if (clickIntervals.size() > 20) clickIntervals.remove(0);
    }

    public void updateAimConsistency(double consistency) {
        lastAimConsistency = consistency;
        aimConsistency.add(consistency);
        if (aimConsistency.size() > 20) aimConsistency.remove(0);
    }

    public void updateAttackPrediction(double prediction) {
        lastPredictionScore = prediction;
        predictionAccuracy.add(prediction);
        if (predictionAccuracy.size() > 20) predictionAccuracy.remove(0);
    }

    public void updateReachStats() {
        double[] reaches = reachDistances.values().stream()
                .mapToDouble(Double::doubleValue)
                .toArray();
        averageReach = Arrays.stream(reaches).average().orElse(0.0);
        reachVariance = calculateVariance(reaches);
    }

    public double getAverageReach() {
        return averageReach;
    }

    public double getMaxReach() {
        return reachDistances.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
    }

    public int getSuspiciousHits() {
        return suspiciousHits;
    }


    public void addReachConsistency(double variance) {
        if (variance < 0.01) {
            addViolation("Suspicious reach consistency: " + String.format("%.6f", variance));
        }
    }

    private double calculateVariance(double[] values) {
        double mean = Arrays.stream(values).average().orElse(0.0);
        return Arrays.stream(values)
                .map(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
    }

    public void addViolation(String description) {
        violations.add(System.currentTimeMillis() + ": " + description);
        totalViolations++;
    }

    /**
     * Get the total number of data points collected
     * @return Total number of collected data points
     */
    public int getTotalDataPoints() {
        return reachDistances.size() +
                hitAngles.size() +
                hitVectors.size() +
                aimConsistency.size() +
                rotationSpeed.size() +
                clickCounts.size() +
                clickIntervals.size();
    }

    public Map<String, Object> getAnalysisResults() {
        Map<String, Object> results = new HashMap<>();
        results.put("average_reach", averageReach);
        results.put("reach_variance", reachVariance);
        results.put("aim_consistency", lastAimConsistency);
        results.put("prediction_accuracy", lastPredictionScore);
        results.put("suspicious_hits", suspiciousHits);
        results.put("total_violations", totalViolations);
        return results;
    }

    public List<String> getRecentViolations(int count) {
        int size = Math.min(count, violations.size());
        return violations.subList(violations.size() - size, violations.size());
    }

    public void cleanup() {
        long threshold = System.currentTimeMillis() - 60000; // Keep last minute
        reachDistances.entrySet().removeIf(entry -> entry.getKey() < threshold);
        hitAngles.entrySet().removeIf(entry -> entry.getKey() < threshold);
        clickCounts.entrySet().removeIf(entry -> entry.getKey() < threshold);
    }
}