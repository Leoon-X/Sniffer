package me.leon.sniffer.data.container;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class BlockData {
    private final UUID playerUUID;
    private final long startTime;

    // Break Data
    private final Map<Long, Location> breakLocations = new ConcurrentHashMap<>();
    private final Map<Long, Double> breakTimes = new ConcurrentHashMap<>();
    private final List<Double> breakSpeeds = new ArrayList<>();
    private final List<Vector> breakVectors = new ArrayList<>();
    private double lastBreakSpeed;

    // Place Data
    private final Map<Long, Location> placeLocations = new ConcurrentHashMap<>();
    private final Map<Long, Double> placeTimes = new ConcurrentHashMap<>();
    private final List<Vector> placeVectors = new ArrayList<>();
    private final List<Double> placeAngles = new ArrayList<>();

    // Pattern Analysis
    private final List<Double> patternScores = new ArrayList<>();
    private final List<Double> buildAngles = new ArrayList<>();
    private final List<Double> buildDistances = new ArrayList<>();
    private double lastPatternScore;

    // Reach Analysis
    private final List<Double> reachDistances = new ArrayList<>();
    private final Map<Long, Double> reachViolations = new ConcurrentHashMap<>();
    private double averageReach;
    private double reachVariance;

    // Timing Analysis
    private final Map<Location, Long> blockProgressTimes = new ConcurrentHashMap<>();
    private final List<Double> breakIntervals = new ArrayList<>();
    private final List<Double> placeIntervals = new ArrayList<>();

    // Nuker Detection
    private final List<Vector> nukerVectors = new ArrayList<>();
    private final List<Double> sphericalVariance = new ArrayList<>();
    private double lastNukerScore;

    // Violation Tracking
    private final List<String> violations = new ArrayList<>();
    private int totalViolations;

    public BlockData(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.startTime = System.currentTimeMillis();
    }

    public void addBlockBreak(Location location, Material material, double breakTime, double reach) {
        long time = System.currentTimeMillis();
        breakLocations.put(time, location);
        breakTimes.put(time, breakTime);
        breakSpeeds.add(breakTime);
        breakVectors.add(location.toVector());

        addReachDistance(reach);

        if (breakSpeeds.size() > 20) {
            breakSpeeds.remove(0);
        }
        if (breakVectors.size() > 20) {
            breakVectors.remove(0);
        }

        analyzeBreakPattern();
    }

    public void addBlockPlace(Location location, Material material, double interval, double reach) {
        long time = System.currentTimeMillis();
        placeLocations.put(time, location);
        placeVectors.add(location.toVector());
        placeIntervals.add(interval);

        addReachDistance(reach);

        if (placeVectors.size() > 2) {
            calculateBuildAngle(placeVectors);
        }

        if (placeVectors.size() > 20) {
            placeVectors.remove(0);
            placeIntervals.remove(0);
        }

        analyzeBuildPattern();
    }

    private void addReachDistance(double reach) {
        reachDistances.add(reach);
        if (reachDistances.size() > 20) {
            reachDistances.remove(0);
        }

        double[] reaches = reachDistances.stream().mapToDouble(Double::doubleValue).toArray();
        averageReach = Arrays.stream(reaches).average().orElse(0.0);
        reachVariance = calculateVariance(reaches);
    }

    private void analyzeBreakPattern() {
        if (breakVectors.size() < 3) return;

        // Calculate spherical variance for nuker detection
        Vector center = calculateCenter(breakVectors);
        double[] distances = breakVectors.stream()
                .mapToDouble(v -> v.distance(center))
                .toArray();

        double variance = calculateVariance(distances);
        sphericalVariance.add(variance);

        if (sphericalVariance.size() > 20) {
            sphericalVariance.remove(0);
        }

        // Update nuker score
        lastNukerScore = calculateNukerProbability();
    }

    private void analyzeBuildPattern() {
        if (placeVectors.size() < 3) return;

        double patternScore = calculatePatternScore();
        patternScores.add(patternScore);
        lastPatternScore = patternScore;

        if (patternScores.size() > 20) {
            patternScores.remove(0);
        }
    }

    private void calculateBuildAngle(List<Vector> vectors) {
        Vector v1 = vectors.get(vectors.size() - 2).subtract(vectors.get(vectors.size() - 3));
        Vector v2 = vectors.get(vectors.size() - 1).subtract(vectors.get(vectors.size() - 2));

        double angle = v1.angle(v2);
        buildAngles.add(angle);

        if (buildAngles.size() > 20) {
            buildAngles.remove(0);
        }
    }

    private Vector calculateCenter(List<Vector> vectors) {
        Vector sum = new Vector(0, 0, 0);
        for (Vector v : vectors) {
            sum.add(v);
        }
        return sum.multiply(1.0 / vectors.size());
    }

    private double calculatePatternScore() {
        if (buildAngles.isEmpty()) return 1.0;
        double variance = calculateVariance(buildAngles.stream().mapToDouble(Double::doubleValue).toArray());
        return Math.exp(-variance); // 1 = very consistent, 0 = random
    }

    private double calculateNukerProbability() {
        if (sphericalVariance.isEmpty()) return 0.0;
        double averageVariance = sphericalVariance.stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
        return Math.exp(-averageVariance * 10); // 1 = likely nuker, 0 = likely legitimate
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

    public Map<String, Object> getAnalysisResults() {
        Map<String, Object> results = new HashMap<>();
        results.put("average_reach", averageReach);
        results.put("reach_variance", reachVariance);
        results.put("pattern_score", lastPatternScore);
        results.put("nuker_score", lastNukerScore);
        results.put("total_violations", totalViolations);
        return results;
    }

    public List<String> getRecentViolations(int count) {
        int size = Math.min(count, violations.size());
        return violations.subList(violations.size() - size, violations.size());
    }
    /**
     * Get the total number of data points collected
     * @return The number of data points
     */
    public int getTotalDataPoints() {
        return breakLocations.size() +
                placeLocations.size() +
                breakVectors.size() +
                placeVectors.size() +
                reachDistances.size();
    }

    public void cleanup() {
        long threshold = System.currentTimeMillis() - 60000; // Keep last minute
        breakLocations.entrySet().removeIf(entry -> entry.getKey() < threshold);
        placeLocations.entrySet().removeIf(entry -> entry.getKey() < threshold);
        reachViolations.entrySet().removeIf(entry -> entry.getKey() < threshold);
    }
}