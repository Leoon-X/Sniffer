package me.leon.sniffer.collector.impl;

import io.github.retrooper.packetevents.event.impl.PacketPlayReceiveEvent;
import io.github.retrooper.packetevents.event.impl.PacketPlaySendEvent;
import io.github.retrooper.packetevents.packettype.PacketType;
import io.github.retrooper.packetevents.packetwrappers.play.in.flying.WrappedPacketInFlying;
import io.github.retrooper.packetevents.packetwrappers.play.out.position.WrappedPacketOutPosition;
import lombok.Getter;
import me.leon.sniffer.Sniffer;
import me.leon.sniffer.collector.interfaces.ICollector;
import me.leon.sniffer.data.container.MovementData;
import me.leon.sniffer.data.enums.SniffType;
import me.leon.sniffer.utils.FileUtil;
import me.leon.sniffer.utils.LocationUtil;
import me.leon.sniffer.utils.MathUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MovementCollector extends ICollector {
    private final Sniffer plugin;
    @Getter private final Map<UUID, MovementData> movementData;
    private final Map<UUID, Location> lastLocation;
    private final Map<UUID, Long> lastMovement;
    private final Map<UUID, Double> lastDeltaY;

    public MovementCollector(Sniffer plugin) {
        super(SniffType.MOVEMENT, true);
        this.plugin = plugin;
        this.movementData = new ConcurrentHashMap<>();
        this.lastLocation = new ConcurrentHashMap<>();
        this.lastMovement = new ConcurrentHashMap<>();
        this.lastDeltaY = new ConcurrentHashMap<>();
    }

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        if (event.getPacketId() == PacketType.Play.Client.FLYING ||
                event.getPacketId() == PacketType.Play.Client.POSITION ||
                event.getPacketId() == PacketType.Play.Client.POSITION_LOOK ||
                event.getPacketId() == PacketType.Play.Client.LOOK) {

            Player player = (Player) event.getPlayer();
            UUID uuid = player.getUniqueId();

            if (!isCollecting(uuid)) return;

            WrappedPacketInFlying packet = new WrappedPacketInFlying(event.getNMSPacket());
            handleMovement(player, packet);
        }
    }

    @Override
    public void onPacketPlaySend(PacketPlaySendEvent event) {
        if (event.getPacketId() == PacketType.Play.Server.POSITION) {
            Player player = (Player) event.getPlayer();
            UUID uuid = player.getUniqueId();

            if (!isCollecting(uuid)) return;

            WrappedPacketOutPosition packet = new WrappedPacketOutPosition(event.getNMSPacket());
            handleTeleport(player, packet);
        }
    }

    private void handleMovement(Player player, WrappedPacketInFlying packet) {
        UUID uuid = player.getUniqueId();
        MovementData data = movementData.get(uuid);
        Location current = player.getLocation();

        if (packet.isPosition()) {
            double x = packet.getX();
            double y = packet.getY();
            double z = packet.getZ();
            data.addPosition(x, y, z);

            // Calculate movement statistics
            Location last = lastLocation.get(uuid);
            if (last != null) {
                double deltaY = y - last.getY();
                double horizontalSpeed = MathUtil.getHorizontalDistance(last, current);
                double verticalSpeed = Math.abs(deltaY);

                data.addMovementData(horizontalSpeed, verticalSpeed, deltaY);

                // Check for suspicious movements
                checkSuspiciousMovement(player, last, current, deltaY);
            }

            lastLocation.put(uuid, current.clone());
        }

        if (packet.isLook()) {
            float yaw = packet.getYaw();
            float pitch = packet.getPitch();
            data.addRotation(yaw, pitch);

            // Check for invalid rotations
            if (MathUtil.isInvalidRotation(pitch)) {
                logSuspiciousRotation(player, yaw, pitch);
            }
        }

        data.addGroundState(packet.isOnGround());
        lastMovement.put(uuid, System.currentTimeMillis());
    }

    private void handleTeleport(Player player, WrappedPacketOutPosition packet) {
        UUID uuid = player.getUniqueId();
        MovementData data = movementData.get(uuid);

        // For 1.8 PacketEvents, we use the Position object
        double x = packet.getPosition().x;
        double y = packet.getPosition().y;
        double z = packet.getPosition().z;
        float yaw = packet.getYaw();
        float pitch = packet.getPitch();

        data.addTeleport(x, y, z, yaw, pitch);
        lastLocation.put(uuid, new Location(player.getWorld(), x, y, z, yaw, pitch));
    }

    private void checkSuspiciousMovement(Player player, Location from, Location to, double deltaY) {
        UUID uuid = player.getUniqueId();
        MovementData data = movementData.get(uuid);

        // Check for suspicious vertical movement
        if (Math.abs(deltaY) > 0.5 && !LocationUtil.isNearClimbable(from)) {
            Double lastDelta = lastDeltaY.get(uuid);
            if (lastDelta != null && Math.signum(deltaY) == Math.signum(lastDelta)) {
                data.flagSuspiciousVertical(deltaY);
                if (debug) {
                    FileUtil.logData(plugin, "movement", uuid,
                            "Suspicious vertical movement: " + deltaY + " (previous: " + lastDelta + ")");
                }
            }
        }
        lastDeltaY.put(uuid, deltaY);

        // Check ground state consistency
        boolean shouldBeOnGround = LocationUtil.isOnGround(to);
        boolean claimedOnGround = data.getLastGroundState();
        if (shouldBeOnGround != claimedOnGround) {
            data.flagGroundInconsistency();
            if (debug) {
                FileUtil.logData(plugin, "movement", uuid,
                        "Ground state inconsistency: claimed=" + claimedOnGround + ", actual=" + shouldBeOnGround);
            }
        }
    }

    private void logSuspiciousRotation(Player player, float yaw, float pitch) {
        if (debug) {
            FileUtil.logData(plugin, "movement", player.getUniqueId(),
                    "Suspicious rotation: yaw=" + yaw + ", pitch=" + pitch);
        }
    }

    @Override
    public boolean startCollecting(Player player) {
        UUID uuid = player.getUniqueId();
        if (isCollecting(uuid)) return false;

        movementData.put(uuid, new MovementData(uuid));
        lastLocation.put(uuid, player.getLocation().clone());
        lastMovement.put(uuid, System.currentTimeMillis());

        return true;
    }

    @Override
    public void stopCollecting(Player player) {
        UUID uuid = player.getUniqueId();
        movementData.remove(uuid);
        lastLocation.remove(uuid);
        lastMovement.remove(uuid);
        lastDeltaY.remove(uuid);
    }

    @Override
    public boolean isCollecting(UUID uuid) {
        return movementData.containsKey(uuid);
    }

    @Override
    public void saveData(UUID uuid) {
        MovementData data = movementData.get(uuid);
        if (data != null) {
            FileUtil.saveData(plugin, "movement", uuid, data);
        }
    }

    @Override
    public void clearData(UUID uuid) {
        movementData.remove(uuid);
        lastLocation.remove(uuid);
        lastMovement.remove(uuid);
        lastDeltaY.remove(uuid);
    }

    public Map<UUID, MovementData> getCollectedData() {
        return movementData;
    }

    public void clearData() {
        // Clear all maps and collections
        for (UUID uuid : getCollectedData().keySet()) {
            clearData(uuid);
        }
    }
    @Override
    public int getDataCount(UUID uuid) {
        MovementData data = movementData.get(uuid);
        return data != null ? data.getTotalDataPoints() : 0;
    }
}