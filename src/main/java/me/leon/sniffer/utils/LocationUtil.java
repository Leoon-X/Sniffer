package me.leon.sniffer.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class LocationUtil {
    private static final double PLAYER_WIDTH = 0.6;
    private static final double PLAYER_HEIGHT = 1.8;

    public static boolean isOnGround(Location location) {
        double expand = 0.3;
        location = location.clone().subtract(0, 0.001, 0);

        for (double x = -expand; x <= expand; x += expand) {
            for (double z = -expand; z <= expand; z += expand) {
                if (location.clone().add(x, 0, z).getBlock().getType().isSolid()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isInLiquid(Location location) {
        Block block = location.getBlock();
        Material material = block.getType();
        return material == Material.WATER || material == Material.STATIONARY_WATER
                || material == Material.LAVA || material == Material.STATIONARY_LAVA;
    }

    public static boolean isOnClimbable(Location location) {
        Material material = location.getBlock().getType();
        return material == Material.LADDER || material == Material.VINE;
    }

    public static List<Block> getBlocksAround(Location location) {
        List<Block> blocks = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    blocks.add(location.clone().add(x, y, z).getBlock());
                }
            }
        }
        return blocks;
    }

    public static Vector getDirection(Location location) {
        Vector vector = new Vector();

        double rotX = location.getYaw();
        double rotY = location.getPitch();

        vector.setY(-Math.sin(Math.toRadians(rotY)));

        double xz = Math.cos(Math.toRadians(rotY));

        vector.setX(-xz * Math.sin(Math.toRadians(rotX)));
        vector.setZ(xz * Math.cos(Math.toRadians(rotX)));

        return vector;
    }

    public static boolean hasBlocksAround(Location location) {
        location = location.clone().subtract(0, 0.5, 0);
        for (double x = -0.3; x <= 0.3; x += 0.3) {
            for (double z = -0.3; z <= 0.3; z += 0.3) {
                if (location.clone().add(x, 0, z).getBlock().getType().isSolid()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<Block> getBlocksBetween(Location loc1, Location loc2) {
        List<Block> blocks = new ArrayList<>();

        int minX = Math.min(loc1.getBlockX(), loc2.getBlockX());
        int minY = Math.min(loc1.getBlockY(), loc2.getBlockY());
        int minZ = Math.min(loc1.getBlockZ(), loc2.getBlockZ());

        int maxX = Math.max(loc1.getBlockX(), loc2.getBlockX());
        int maxY = Math.max(loc1.getBlockY(), loc2.getBlockY());
        int maxZ = Math.max(loc1.getBlockZ(), loc2.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    blocks.add(loc1.getWorld().getBlockAt(x, y, z));
                }
            }
        }

        return blocks;
    }

    public static boolean isInBox(Location point, Location min, Location max) {
        return point.getX() >= min.getX() && point.getX() <= max.getX()
                && point.getY() >= min.getY() && point.getY() <= max.getY()
                && point.getZ() >= min.getZ() && point.getZ() <= max.getZ();
    }

    public static List<Location> getLine(Location start, Location end) {
        List<Location> locations = new ArrayList<>();

        double distance = start.distance(end);
        Vector direction = end.toVector().subtract(start.toVector()).normalize();

        for (double d = 0; d <= distance; d += 0.1) {
            locations.add(start.clone().add(direction.clone().multiply(d)));
        }

        return locations;
    }

    public static boolean isColliding(Location playerLoc, Location blockLoc) {
        double minX = blockLoc.getX();
        double minY = blockLoc.getY();
        double minZ = blockLoc.getZ();
        double maxX = blockLoc.getX() + 1;
        double maxY = blockLoc.getY() + 1;
        double maxZ = blockLoc.getZ() + 1;

        double playerMinX = playerLoc.getX() - PLAYER_WIDTH / 2;
        double playerMinY = playerLoc.getY();
        double playerMinZ = playerLoc.getZ() - PLAYER_WIDTH / 2;
        double playerMaxX = playerLoc.getX() + PLAYER_WIDTH / 2;
        double playerMaxY = playerLoc.getY() + PLAYER_HEIGHT;
        double playerMaxZ = playerLoc.getZ() + PLAYER_WIDTH / 2;

        return (minX <= playerMaxX && maxX >= playerMinX) &&
                (minY <= playerMaxY && maxY >= playerMinY) &&
                (minZ <= playerMaxZ && maxZ >= playerMinZ);
    }

    public static boolean hasLineOfSight(Location start, Location end) {
        List<Location> line = getLine(start, end);

        for (Location loc : line) {
            if (loc.getBlock().getType().isOccluding()) {
                return false;
            }
        }

        return true;
    }

    public static Location getHighestLocation(Location location) {
        location = location.clone();
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();

        for (int y = world.getMaxHeight(); y >= 0; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid()) {
                location.setY(y + 1);
                return location;
            }
        }

        return location;
    }

    public static boolean isOnStairs(Location location) {
        Block block = location.getBlock();
        Material type = block.getType();
        return type.name().contains("STAIRS");
    }

    public static List<Entity> getNearbyEntities(Location location, double radius) {
        List<Entity> entities = new ArrayList<>();

        for (Entity entity : location.getWorld().getEntities()) {
            if (entity.getLocation().distance(location) <= radius) {
                entities.add(entity);
            }
        }

        return entities;
    }

    public static double getDistanceToGround(Location location) {
        location = location.clone();
        double distance = 0;

        while (location.getBlockY() > 0) {
            location.subtract(0, 1, 0);
            distance++;

            if (location.getBlock().getType().isSolid()) {
                break;
            }
        }

        return distance;
    }

    /**
     * Check if a location is near a climbable block (ladder or vine)
     * @param location Location to check
     * @return true if near climbable
     */
    public static boolean isNearClimbable(Location location) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block block = location.clone().add(x, y, z).getBlock();
                    Material type = block.getType();
                    if (type == Material.LADDER || type == Material.VINE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isNearSlimeBlock(Location location, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (location.clone().add(x, y, z).getBlock().getType() == Material.SLIME_BLOCK) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}