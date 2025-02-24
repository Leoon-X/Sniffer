package me.leon.sniffer.utils;

import java.util.concurrent.TimeUnit;

public class TimeUtil {
    private static long lastTimeUpdate = System.currentTimeMillis();
    private static long timeLastMillis = System.nanoTime() / 1000000L;

    public static long getNow() {
        return System.currentTimeMillis();
    }

    public static long getCurrentTime() {
        long now = System.nanoTime() / 1000000L;
        long timeMillis = timeLastMillis + (now - timeLastMillis);
        timeLastMillis = timeMillis;
        return timeMillis;
    }

    public static boolean hasExpired(long timestamp, long timeout) {
        return System.currentTimeMillis() - timestamp > timeout;
    }

    public static long getServerTick() {
        return getCurrentTime() / 50; // Convert to ticks (50ms per tick)
    }

    public static double getDelta(long currentTime, long lastTime) {
        return (currentTime - lastTime) / 1000.0;
    }

    public static boolean elapsed(long time, long elapsed) {
        return System.currentTimeMillis() - time > elapsed;
    }

    public static long getElapsedTime(long time) {
        return System.currentTimeMillis() - time;
    }

    public static long getTimeAgo(long duration, TimeUnit timeUnit) {
        return System.currentTimeMillis() - timeUnit.toMillis(duration);
    }

    public static boolean isPassed(long timestamp) {
        return System.currentTimeMillis() > timestamp;
    }

    public static double getTimeDelta() {
        long currentTime = System.currentTimeMillis();
        double delta = (currentTime - lastTimeUpdate) / 1000.0;
        lastTimeUpdate = currentTime;
        return delta;
    }
}