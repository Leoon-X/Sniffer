package me.leon.sniffer.utils;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public class MathUtil {
    private static final double EPSILON = 1E-10;

    public static double getGCD(double a, double b) {
        if (Math.abs(b) < EPSILON) return Math.abs(a);
        return getGCD(b, a - Math.floor(a / b) * b);
    }

    public static double[] getRotations(Location one, Location two) {
        double diffX = two.getX() - one.getX();
        double diffY = two.getY() - one.getY();
        double diffZ = two.getZ() - one.getZ();

        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) (Math.atan2(diffZ, diffX) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) -(Math.atan2(diffY, dist) * 180.0D / Math.PI);

        return new double[] { yaw, pitch };
    }

    public static double getAngleDifference(double alpha, double beta) {
        double phi = Math.abs(beta - alpha) % 360;
        return phi > 180 ? 360 - phi : phi;
    }

    public static double calculateSensitivity(double deltaYaw, double deltaPitch) {
        if (Math.abs(deltaPitch) < EPSILON) return 0;
        return deltaYaw / deltaPitch;
    }

    public static double getDistance3D(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static double getHorizontalDistance(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double[] getOffsets(Vector from, Vector to) {
        return new double[] {
                Math.abs(to.getX() - from.getX()),
                Math.abs(to.getY() - from.getY()),
                Math.abs(to.getZ() - from.getZ())
        };
    }

    public static double getVariance(double[] samples) {
        double mean = 0.0;
        for (double sample : samples) mean += sample;
        mean /= samples.length;

        double variance = 0.0;
        for (double sample : samples) {
            variance += (sample - mean) * (sample - mean);
        }
        return variance / samples.length;
    }

    public static double getStandardDeviation(double[] samples) {
        return Math.sqrt(getVariance(samples));
    }

    public static double[] calculateMovementPattern(Location[] locations) {
        if (locations.length < 2) return new double[0];

        double[] patterns = new double[locations.length - 1];
        for (int i = 1; i < locations.length; i++) {
            patterns[i-1] = getDistance3D(locations[i-1], locations[i]);
        }
        return patterns;
    }

    public static boolean isInvalidRotation(float pitch) {
        return pitch > 90.0F || pitch < -90.0F;
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float getAngle(Vector vector1, Vector vector2) {
        double dot = vector1.dot(vector2);
        double magnitude = vector1.length() * vector2.length();
        return (float) Math.toDegrees(Math.acos(dot / magnitude));
    }
}