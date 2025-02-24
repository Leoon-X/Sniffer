package me.leon.sniffer.data.container;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class MovementData {
    private final UUID playerUUID;
    private final long startTime;

    // Position Data
    private final Map<Long, Vector> positions = new ConcurrentHashMap<>();
    private final Map<Long, float[]> rotations = new ConcurrentHashMap<>();
    private final List<Double> deltaY = new ArrayList<>();
    private final List<Vector> velocities = new ArrayList<>();

    // Ground State
    private final Map<Long, Boolean> groundStates = new ConcurrentHashMap<>();
    private final List<Boolean> groundStateChanges = new ArrayList<>();
    private boolean lastGroundState = true;

    // Movement Analysis
    private final List<Double> speeds = new ArrayList<>();
    private final List<Double> accelerations = new ArrayList<>();
    private final List<Double> verticalSpeeds = new ArrayList<>();
    private final List<Double> jumpHeights = new ArrayList<>();

    // Pattern Analysis
    private final List<Double> movementAngles = new ArrayList<>();
    private final List<Double> strafePatterns = new ArrayList<>();
    private final List<Double> timerPatterns = new ArrayList<>();

    // Statistics
    private double currentSpeed;
    private double lastSpeed;
    private double maxSpeed;
    private double averageSpeed;
    private int suspiciousMovements;
    private Vector lastPosition;
    private long lastMoveTime;

    public MovementData(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.startTime = System.currentTimeMillis();
    }

    public void addPosition(double x, double y, double z) {
        long time = System.currentTimeMillis();
        Vector pos = new Vector(x, y, z);
        positions.put(time, pos);

        if (lastPosition != null) {
            processMovement(pos, time);
        }

        lastPosition = pos;
        lastMoveTime = time;
    }

    public void addGroundState(boolean onGround) {
        long time = System.currentTimeMillis();
        groundStates.put(time, onGround);
        groundStateChanges.add(onGround);

        if (groundStateChanges.size() > 20) {
            groundStateChanges.remove(0);
        }

        lastGroundState = onGround;
    }

    public void addRotation(float yaw, float pitch) {
        rotations.put(System.currentTimeMillis(), new float[]{yaw, pitch});
    }

    public void addVelocity(double x, double y, double z) {
        velocities.add(new Vector(x, y, z));
        if (velocities.size() > 20) velocities.remove(0);
    }

    public void addMovementData(double horizontalSpeed, double verticalSpeed, double deltaY) {
        speeds.add(horizontalSpeed);
        verticalSpeeds.add(verticalSpeed);
        this.deltaY.add(deltaY);

        if (speeds.size() > 20) speeds.remove(0);
        if (verticalSpeeds.size() > 20) verticalSpeeds.remove(0);
        if (this.deltaY.size() > 20) this.deltaY.remove(0);

        currentSpeed = horizontalSpeed;
        maxSpeed = Math.max(maxSpeed, horizontalSpeed);
        updateAverageSpeed();
    }

    public void flagSuspiciousVertical(double deltaY) {
        addViolation("Suspicious vertical movement: " + String.format("%.2f", deltaY));
        suspiciousMovements++;
    }

    public void flagGroundInconsistency() {
        addViolation("Ground state inconsistency detected");
        suspiciousMovements++;
    }

    public void addSpeedAnomaly(double acceleration) {
        addViolation("Speed anomaly detected: " + String.format("%.2f", acceleration));
        accelerations.add(acceleration);
        if (accelerations.size() > 20) accelerations.remove(0);
    }

    public void addVerticalAnomaly(double speed) {
        addViolation("Vertical speed anomaly: " + String.format("%.2f", speed));
    }

    public void addDoubleJump(double height) {
        addViolation("Double jump detected: " + String.format("%.2f", height));
        jumpHeights.add(height);
        if (jumpHeights.size() > 20) jumpHeights.remove(0);
    }

    public void addTimerViolation(double multiplier) {
        addViolation("Timer violation detected: " + String.format("%.2f", multiplier));
        timerPatterns.add(multiplier);
        if (timerPatterns.size() > 20) timerPatterns.remove(0);
    }

    public void addStrafePattern(double angle) {
        strafePatterns.add(angle);
        if (strafePatterns.size() > 20) strafePatterns.remove(0);

        // Check for suspicious strafe patterns
        if (strafePatterns.size() >= 5) {
            analyzeStrafing();
        }
    }

    private void processMovement(Vector newPos, long time) {
        // Calculate basic movement data
        Vector delta = newPos.clone().subtract(lastPosition);
        double horizontalSpeed = Math.sqrt(delta.getX() * delta.getX() + delta.getZ() * delta.getZ());
        double verticalSpeed = Math.abs(delta.getY());
        double timeDelta = (time - lastMoveTime) / 1000.0;

        // Process speeds
        if (timeDelta > 0) {
            double speed = horizontalSpeed / timeDelta;
            addMovementData(speed, verticalSpeed / timeDelta, delta.getY());
        }

        // Calculate movement angle
        if (!movementAngles.isEmpty()) {
            Vector lastDelta = velocities.isEmpty() ? delta : velocities.get(velocities.size() - 1);
            double angle = delta.angle(lastDelta);
            movementAngles.add(angle);
            if (movementAngles.size() > 20) movementAngles.remove(0);
        }

        // Update velocities
        addVelocity(delta.getX() / timeDelta, delta.getY() / timeDelta, delta.getZ() / timeDelta);
    }

    private void analyzeStrafing() {
        double variance = calculateVariance(strafePatterns);
        if (variance < 0.001) {
            addViolation("Suspicious strafe pattern detected: " + String.format("%.6f", variance));
        }
    }

    private void updateAverageSpeed() {
        averageSpeed = speeds.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private double calculateVariance(List<Double> values) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
    }

    private final List<String> violations = new ArrayList<>();
    private int totalViolations;

    public void addViolation(String description) {
        violations.add(System.currentTimeMillis() + ": " + description);
        totalViolations++;
    }

    public void cleanup() {
        long threshold = System.currentTimeMillis() - 60000; // Keep last minute
        positions.entrySet().removeIf(entry -> entry.getKey() < threshold);
        rotations.entrySet().removeIf(entry -> entry.getKey() < threshold);
        groundStates.entrySet().removeIf(entry -> entry.getKey() < threshold);
    }

    public boolean getLastGroundState() {
        return lastGroundState;
    }

    public int getSuspiciousMovements() {
        return suspiciousMovements;
    }

    public void addGroundSpoof(boolean claimed, boolean actual) {
        addViolation("Ground state spoof detected - Claimed: " + claimed + ", Actual: " + actual);
        suspiciousMovements++;
    }

    public void addImpossibleStateChange(String reason) {
        addViolation("Impossible state change: " + reason);
        suspiciousMovements++;
    }

    public void addRotationData(float deltaYaw, float deltaPitch) {
        if (rotations.size() > 20) {
            rotations.remove(rotations.keySet().iterator().next());
        }
        rotations.put(System.currentTimeMillis(), new float[]{deltaYaw, deltaPitch});
    }

    public void addServerPosition(Location serverLoc) {
        positions.put(System.currentTimeMillis(), new Vector(
                serverLoc.getX(),
                serverLoc.getY(),
                serverLoc.getZ()
        ));

        rotations.put(System.currentTimeMillis(), new float[]{
                serverLoc.getYaw(),
                serverLoc.getPitch()
        });
    }
    public Map<String, Object> getAnalysisResults() {
        Map<String, Object> results = new HashMap<>();
        results.put("current_speed", currentSpeed);
        results.put("max_speed", maxSpeed);
        results.put("average_speed", averageSpeed);
        results.put("suspicious_movements", suspiciousMovements);
        results.put("total_violations", totalViolations);
        return results;
    }

    /**
     * Add teleport data
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param yaw Yaw rotation
     * @param pitch Pitch rotation
     */
    public void addTeleport(double x, double y, double z, float yaw, float pitch) {
        long time = System.currentTimeMillis();

        // Store the teleport position
        positions.put(time, new Vector(x, y, z));

        // Store the teleport rotation
        rotations.put(time, new float[]{yaw, pitch});

        // Reset movement tracking variables since this is a teleport
        lastPosition = new Vector(x, y, z);
        lastMoveTime = time;

        // Add this as a special type of movement
        velocities.clear(); // Clear velocity history as it's no longer relevant
        speeds.clear(); // Clear speed history
    }

    /**
     * Add teleport data from a Location
     * @param location The location being teleported to
     */
    public void addTeleport(Location location) {
        addTeleport(
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    public List<String> getRecentViolations(int count) {
        int size = Math.min(count, violations.size());
        return violations.subList(violations.size() - size, violations.size());
    }

    public boolean isCollecting() {
        return System.currentTimeMillis() - startTime < 60000;
    }

    public int getTotalDataPoints() {
        return positions.size() + rotations.size() + groundStates.size();
    }
}