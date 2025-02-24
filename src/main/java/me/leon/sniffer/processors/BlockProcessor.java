package me.leon.sniffer.processors;

import io.github.retrooper.packetevents.event.impl.PacketPlayReceiveEvent;
import io.github.retrooper.packetevents.event.impl.PacketPlaySendEvent;
import io.github.retrooper.packetevents.packettype.PacketType;
import lombok.RequiredArgsConstructor;
import me.leon.sniffer.Sniffer;
import me.leon.sniffer.data.container.BlockData;
import me.leon.sniffer.utils.MathUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class BlockProcessor implements PacketProcessor {
    private final Sniffer plugin;

    // Player States
    private final Map<UUID, Location> lastBlockInteraction = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastInteractionTime = new ConcurrentHashMap<>();

    // Analysis Buffers
    private final Map<UUID, List<Vector>> breakVectors = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> placementTimings = new ConcurrentHashMap<>();
    private final Map<UUID, List<Double>> reachDistances = new ConcurrentHashMap<>();
    private final Map<UUID, List<Vector>> placeVectors = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> interactionTimings = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Location, Long>> blockBreakProgress = new ConcurrentHashMap<>();
    private final Map<UUID, List<Double>> breakPatternBuffer = new ConcurrentHashMap<>();

    // Constants
    private static final int BUFFER_SIZE = 20;
    private static final double MAX_REACH = 6.0;
    private static final double NUKER_ANGLE_THRESHOLD = 45.0;
    private static final double MIN_BREAK_TIME = 0.05; // 50ms
    private static final int PATTERN_BUFFER_SIZE = 50;

    @Override
    public void processIncoming(Player player, PacketPlayReceiveEvent event) {
        byte packetId = event.getPacketId();
        if (!isBlockPacket(packetId)) return;

        UUID uuid = player.getUniqueId();
        BlockData data = plugin.getBlockCollector().getBlockData().get(uuid);
        if (data == null) return;

        long currentTime = System.currentTimeMillis();
        Location blockLocation = extractBlockLocation(event);
        if (blockLocation == null) return;

        if (packetId == PacketType.Play.Client.BLOCK_DIG) {
            processBlockBreak(player, blockLocation, currentTime, data);
        } else if (packetId == PacketType.Play.Client.BLOCK_PLACE) {
            processBlockPlace(player, blockLocation, currentTime, data);
        }
    }

    private void processBlockBreak(Player player, Location blockLocation, long currentTime, BlockData data) {
        UUID uuid = player.getUniqueId();

        // Calculate and analyze reach
        double reach = calculateReach(player.getLocation(), blockLocation);
        processReachAnalysis(uuid, reach, currentTime, data);

        // Process break timing and patterns
        processBreakTiming(uuid, blockLocation, currentTime, data);

        // Analyze break patterns
        analyzeBreakPattern(uuid, blockLocation, data);

        // Update state
        lastBlockInteraction.put(uuid, blockLocation);
        lastInteractionTime.put(uuid, currentTime);
    }

    private void processBlockPlace(Player player, Location blockLocation, long currentTime, BlockData data) {
        UUID uuid = player.getUniqueId();

        // Calculate and analyze reach
        double reach = calculateReach(player.getLocation(), blockLocation);
        processReachAnalysis(uuid, reach, currentTime, data);

        // Process place timing and patterns
        processPlaceTiming(uuid, blockLocation, currentTime, data);

        // Analyze build patterns
        analyzeBuildPattern(uuid, blockLocation, data);

        // Update state
        lastBlockInteraction.put(uuid, blockLocation);
        lastInteractionTime.put(uuid, currentTime);
    }

    private void processReachAnalysis(UUID uuid, double reach, long currentTime, BlockData data) {
        List<Double> distances = reachDistances.computeIfAbsent(uuid, k -> new ArrayList<>());
        distances.add(reach);

        if (distances.size() > BUFFER_SIZE) {
            distances.remove(0);
        }

        // Basic reach check
        if (reach > MAX_REACH) {
            data.addViolation("Reach exceeded - " + String.format("%.2f", reach));
        }

        // Analyze reach consistency
        if (distances.size() >= 5) {
            double[] reachArray = distances.stream().mapToDouble(Double::doubleValue).toArray();
            double variance = MathUtil.getVariance(reachArray);
            double mean = Arrays.stream(reachArray).average().orElse(0.0);

            // Check for suspicious reach patterns
            if (variance < 0.01 && mean > MAX_REACH - 0.5) {
                data.addViolation("Suspicious reach consistency - Mean: " +
                        String.format("%.2f", mean) + ", Variance: " + String.format("%.5f", variance));
            }
        }
    }

    private void processBreakTiming(UUID uuid, Location blockLocation, long currentTime, BlockData data) {
        Map<Location, Long> progress = blockBreakProgress.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        Long startTime = progress.get(blockLocation);

        if (startTime == null) {
            // Start of break
            progress.put(blockLocation, currentTime);
        } else {
            // End of break
            double breakTime = (currentTime - startTime) / 1000.0;
            progress.remove(blockLocation);

            if (breakTime < MIN_BREAK_TIME) {
                data.addViolation("Fast break - " + String.format("%.3f", breakTime) + "s");
            }

            // Add to break pattern buffer
            List<Double> patterns = breakPatternBuffer.computeIfAbsent(uuid, k -> new ArrayList<>());
            patterns.add(breakTime);

            if (patterns.size() > PATTERN_BUFFER_SIZE) {
                patterns.remove(0);
            }

            // Analyze break patterns
            analyzeBreakPatterns(patterns, data);
        }
    }

    private void analyzeBuildPattern(UUID uuid, Location blockLocation, BlockData data) {
        List<Vector> vectors = placeVectors.computeIfAbsent(uuid, k -> new ArrayList<>());
        vectors.add(blockLocation.toVector());

        if (vectors.size() > BUFFER_SIZE) {
            vectors.remove(0);
        }

        if (vectors.size() >= 3) {
            // Analyze build patterns
            analyzeVectorPattern(vectors, data);
        }
    }

    private void analyzeBreakPatterns(List<Double> patterns, BlockData data) {
        if (patterns.size() < 5) return;

        double[] timings = patterns.stream().mapToDouble(Double::doubleValue).toArray();
        double variance = MathUtil.getVariance(timings);
        double mean = Arrays.stream(timings).average().orElse(0.0);

        // Check for suspicious break patterns
        if (variance < 0.001 && mean < 0.2) {
            data.addViolation("Suspicious break pattern - Mean: " +
                    String.format("%.3f", mean) + "s, Variance: " + String.format("%.6f", variance));
        }

        // Analyze break rhythm
        analyzeBreakRhythm(patterns, data);
    }

    private void analyzeBreakRhythm(List<Double> patterns, BlockData data) {
        // Calculate intervals between breaks
        double[] intervals = new double[patterns.size() - 1];
        for (int i = 0; i < patterns.size() - 1; i++) {
            intervals[i] = patterns.get(i + 1) - patterns.get(i);
        }

        // Analyze rhythm consistency
        double rhythmVariance = MathUtil.getVariance(intervals);
        if (rhythmVariance < 0.0001) {
            data.addViolation("Suspicious break rhythm - Variance: " + String.format("%.6f", rhythmVariance));
        }
    }

    private void analyzeVectorPattern(List<Vector> vectors, BlockData data) {
        // Calculate angles between consecutive placements
        double[] angles = new double[vectors.size() - 2];
        for (int i = 0; i < vectors.size() - 2; i++) {
            Vector v1 = vectors.get(i + 1).subtract(vectors.get(i));
            Vector v2 = vectors.get(i + 2).subtract(vectors.get(i + 1));
            angles[i] = v1.angle(v2);
        }

        // Analyze angle pattern
        double angleVariance = MathUtil.getVariance(angles);
        if (angleVariance < 0.01) {
            data.addViolation("Suspicious build pattern - Angle variance: " +
                    String.format("%.6f", angleVariance));
        }

        // Check for nuker patterns
        if (vectors.size() > 5) {
            analyzeNukerPattern(vectors, data);
        }
    }

    private void analyzeBreakPattern(UUID uuid, Location blockLocation, BlockData data) {
        // Get break vectors
        List<Vector> vectors = new ArrayList<>(breakVectors.getOrDefault(uuid, new ArrayList<>()));
        vectors.add(blockLocation.toVector());

        // Keep only recent vectors
        while (vectors.size() > 20) {
            vectors.remove(0);
        }

        if (vectors.size() >= 3) {
            // Calculate center point
            Vector center = calculateCenter(vectors);

            // Calculate distances from center
            double[] distances = vectors.stream()
                    .mapToDouble(v -> v.distance(center))
                    .toArray();

            // Calculate variance
            double variance = calculateVariance(distances);

            // Check for nuker pattern (very consistent distances suggest automated breaking)
            if (variance < 0.1) {
                data.addViolation("Possible nuker detected - Spherical pattern variance: " +
                        String.format("%.6f", variance));
            }
        }

        // Update vectors
        breakVectors.put(uuid, vectors);
    }

    private void processPlaceTiming(UUID uuid, Location blockLocation, long currentTime, BlockData data) {
        // Get previous timings
        List<Long> timings = placementTimings.getOrDefault(uuid, new ArrayList<>());
        Location lastLoc = lastBlockInteraction.get(uuid);
        Long lastTime = lastInteractionTime.get(uuid);

        if (lastLoc != null && lastTime != null) {
            // Calculate time difference
            double timeDelta = (currentTime - lastTime) / 1000.0;

            // Calculate distance between placements
            double distance = lastLoc.distance(blockLocation);

            // Check for suspicious timing
            if (timeDelta < 0.05) { // 50ms between placements
                data.addViolation("Fast place timing: " + String.format("%.3fs", timeDelta));
            }

            // Add timing
            timings.add(currentTime);

            // Keep only recent timings
            while (timings.size() > 20) {
                timings.remove(0);
            }

            // Analyze placement pattern if we have enough samples
            if (timings.size() >= 3) {
                analyzePlacementPattern(timings, distance, data);
            }
        }

        // Update timings
        placementTimings.put(uuid, timings);
    }

    private void analyzePlacementPattern(List<Long> timings, double distance, BlockData data) {
        // Calculate intervals between placements
        double[] intervals = new double[timings.size() - 1];
        for (int i = 0; i < timings.size() - 1; i++) {
            intervals[i] = (timings.get(i + 1) - timings.get(i)) / 1000.0;
        }

        // Calculate variance in timing
        double variance = calculateVariance(intervals);

        // Check for suspicious patterns
        if (variance < 0.001) { // Very consistent timing
            data.addViolation("Suspicious placement pattern - Timing variance: " +
                    String.format("%.6f", variance));
        }
    }

    private double calculateVariance(double[] values) {
        double mean = Arrays.stream(values).average().orElse(0.0);
        return Arrays.stream(values)
                .map(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
    }

    private Vector calculateCenter(List<Vector> vectors) {
        Vector sum = new Vector(0, 0, 0);
        for (Vector v : vectors) {
            sum.add(v);
        }
        return sum.multiply(1.0 / vectors.size());
    }

    private void analyzeNukerPattern(List<Vector> vectors, BlockData data) {
        // Check for spherical breaking pattern
        Vector center = calculateCenter(vectors);
        double[] distances = vectors.stream()
                .mapToDouble(v -> v.distance(center))
                .toArray();

        double distanceVariance = MathUtil.getVariance(distances);
        if (distanceVariance < 0.1) {
            data.addViolation("Possible nuker detected - Spherical pattern variance: " +
                    String.format("%.6f", distanceVariance));
        }
    }

    private double calculateReach(Location playerLoc, Location blockLoc) {
        // Account for eye height and hitbox
        Vector eyePos = playerLoc.clone().add(0, 1.62, 0).toVector();
        Vector blockCenter = blockLoc.clone().add(0.5, 0.5, 0.5).toVector();
        return eyePos.distance(blockCenter);
    }

    private boolean isBlockPacket(byte packetId) {
        return packetId == PacketType.Play.Client.BLOCK_DIG ||
                packetId == PacketType.Play.Client.BLOCK_PLACE;
    }

    private Location extractBlockLocation(PacketPlayReceiveEvent event) {
        // Implementation depends on your packet event system
        return null; // Placeholder
    }

    @Override
    public void processOutgoing(Player player, PacketPlaySendEvent event) {
        // Not needed for block analysis
    }

    @Override
    public void cleanup(Player player) {
        UUID uuid = player.getUniqueId();
        lastBlockInteraction.remove(uuid);
        lastInteractionTime.remove(uuid);
        reachDistances.remove(uuid);
        placeVectors.remove(uuid);
        interactionTimings.remove(uuid);
        blockBreakProgress.remove(uuid);
        breakPatternBuffer.remove(uuid);
    }

    @Override
    public void reset() {
        lastBlockInteraction.clear();
        lastInteractionTime.clear();
        reachDistances.clear();
        placeVectors.clear();
        interactionTimings.clear();
        blockBreakProgress.clear();
        breakPatternBuffer.clear();
    }
}