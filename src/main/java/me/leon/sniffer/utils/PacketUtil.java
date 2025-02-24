package me.leon.sniffer.utils;

import io.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.packettype.PacketType;
import io.github.retrooper.packetevents.packetwrappers.play.in.flying.WrappedPacketInFlying;
import io.github.retrooper.packetevents.packetwrappers.play.in.useentity.WrappedPacketInUseEntity;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PacketUtil {
    private static final Map<UUID, Location> lastLocation = new HashMap<>();
    private static final Map<UUID, Long> lastFlying = new HashMap<>();
    private static final Map<UUID, Integer> flyingCount = new HashMap<>();

    public static boolean isMovementPacket(byte packetId) {
        return packetId == PacketType.Play.Client.POSITION
                || packetId == PacketType.Play.Client.POSITION_LOOK
                || packetId == PacketType.Play.Client.LOOK
                || packetId == PacketType.Play.Client.FLYING;
    }

    public static boolean isPositionPacket(byte packetId) {
        return packetId == PacketType.Play.Client.POSITION
                || packetId == PacketType.Play.Client.POSITION_LOOK;
    }

    public static boolean isRotationPacket(byte packetId) {
        return packetId == PacketType.Play.Client.LOOK
                || packetId == PacketType.Play.Client.POSITION_LOOK;
    }

    public static Location getLocation(Player player, WrappedPacketInFlying packet) {
        Location last = lastLocation.getOrDefault(player.getUniqueId(), player.getLocation());

        if (packet.isPosition()) {
            last.setX(packet.getX());
            last.setY(packet.getY());
            last.setZ(packet.getZ());
        }

        if (packet.isLook()) {
            last.setYaw(packet.getYaw());
            last.setPitch(packet.getPitch());
        }

        lastLocation.put(player.getUniqueId(), last.clone());
        return last;
    }

    public static void updateFlyingInfo(UUID uuid) {
        long now = System.currentTimeMillis();
        Long last = lastFlying.get(uuid);

        if (last != null) {
            long timeDiff = now - last;
            if (timeDiff < 150) { // Typical maximum time between packets
                flyingCount.merge(uuid, 1, Integer::sum);
            } else {
                flyingCount.put(uuid, 1);
            }
        }

        lastFlying.put(uuid, now);
    }

    public static int getFlyingCount(UUID uuid) {
        return flyingCount.getOrDefault(uuid, 0);
    }

    public static void clearData(UUID uuid) {
        lastLocation.remove(uuid);
        lastFlying.remove(uuid);
        flyingCount.remove(uuid);
    }

    public static Entity getAttackedEntity(Player player, WrappedPacketInUseEntity packet) {
        if (packet.getAction() != WrappedPacketInUseEntity.EntityUseAction.ATTACK) {
            return null;
        }

        return packet.getEntity();
    }

    public static double[] getMovementOffsets(Location from, Location to) {
        return new double[] {
                Math.abs(to.getX() - from.getX()),
                Math.abs(to.getY() - from.getY()),
                Math.abs(to.getZ() - from.getZ())
        };
    }

    public static void cleanup() {
        long now = System.currentTimeMillis();
        lastFlying.entrySet().removeIf(entry -> now - entry.getValue() > 30000);
        flyingCount.entrySet().removeIf(entry -> !lastFlying.containsKey(entry.getKey()));
    }
}
