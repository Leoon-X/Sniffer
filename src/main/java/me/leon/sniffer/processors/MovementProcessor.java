package me.leon.sniffer.processors;

import io.github.retrooper.packetevents.event.impl.PacketPlayReceiveEvent;
import io.github.retrooper.packetevents.event.impl.PacketPlaySendEvent;
import io.github.retrooper.packetevents.packettype.PacketType;
import io.github.retrooper.packetevents.packetwrappers.play.in.flying.WrappedPacketInFlying;
import io.github.retrooper.packetevents.packetwrappers.play.out.position.WrappedPacketOutPosition;
import lombok.RequiredArgsConstructor;
import me.leon.sniffer.Sniffer;
import me.leon.sniffer.data.container.MovementData;
import me.leon.sniffer.utils.LocationUtil;
import me.leon.sniffer.utils.MathUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class MovementProcessor implements PacketProcessor {
    private final Sniffer plugin;
    private final Map<UUID, Location> lastLocation = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMoveTime = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lastDeltaY = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> lastGround = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> airTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lastSpeed = new ConcurrentHashMap<>();

    @Override
    public void processIncoming(Player player, PacketPlayReceiveEvent event) {
        if (!isMovementPacket(event.getPacketId())) return;

        UUID uuid = player.getUniqueId();
        MovementData data = plugin.getMovementCollector().getMovementData().get(uuid);
        if (data == null) return;

        WrappedPacketInFlying packet = new WrappedPacketInFlying(event.getNMSPacket());
        processMovement(player, packet, data);
    }

    @Override
    public void processOutgoing(Player player, PacketPlaySendEvent event) {
        if (event.getPacketId() != PacketType.Play.Server.POSITION) return;

        UUID uuid = player.getUniqueId();
        MovementData data = plugin.getMovementCollector().getMovementData().get(uuid);
        if (data == null) return;

        WrappedPacketOutPosition packet = new WrappedPacketOutPosition(event.getNMSPacket());
        processServerPosition(player, packet, data);
    }

    private void processMovement(Player player, WrappedPacketInFlying packet, MovementData data) {
        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Location currentLoc = player.getLocation();

        if (packet.isPosition()) {
            currentLoc = new Location(player.getWorld(), packet.getX(), packet.getY(), packet.getZ(),
                    currentLoc.getYaw(), currentLoc.getPitch());
        }
        if (packet.isLook()) {
            currentLoc.setYaw(packet.getYaw());
            currentLoc.setPitch(packet.getPitch());
        }

        // Process ground state
        boolean onGround = packet.isOnGround();
        processGroundState(uuid, currentLoc, onGround, data);

        // Process position if changed
        if (packet.isPosition()) {
            processPositionChange(uuid, currentLoc, currentTime, data);
        }

        // Process rotation if changed
        if (packet.isLook()) {
            processRotationChange(uuid, currentLoc, data);
        }

        // Update last known states
        lastLocation.put(uuid, currentLoc.clone());
        lastMoveTime.put(uuid, currentTime);
        lastGround.put(uuid, onGround);
    }

    private void processGroundState(UUID uuid, Location location, boolean claimed, MovementData data) {
        boolean actual = LocationUtil.isOnGround(location);
        boolean lastGroundState = lastGround.getOrDefault(uuid, true);

        // Update air ticks
        if (!actual) {
            airTicks.merge(uuid, 1, Integer::sum);
        } else {
            airTicks.put(uuid, 0);
        }

        // Check for ground spoofing
        if (claimed != actual) {
            data.addGroundSpoof(claimed, actual);
        }

        // Check for impossible state changes
        if (lastGroundState && !actual && !claimed && !LocationUtil.hasBlocksAround(location)) {
            data.addImpossibleStateChange("Ground to air without jump");
        }
    }

    private void processPositionChange(UUID uuid, Location currentLoc, long currentTime, MovementData data) {
        Location lastLoc = lastLocation.get(uuid);
        if (lastLoc == null) return;

        Long lastTime = lastMoveTime.get(uuid);
        if (lastTime == null) return;

        double deltaTime = (currentTime - lastTime) / 1000.0;
        if (deltaTime <= 0) return;

        // Calculate movement deltas
        double deltaX = currentLoc.getX() - lastLoc.getX();
        double deltaY = currentLoc.getY() - lastLoc.getY();
        double deltaZ = currentLoc.getZ() - lastLoc.getZ();

        // Calculate speeds
        double horizontalSpeed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) / deltaTime;
        double verticalSpeed = Math.abs(deltaY) / deltaTime;

        // Process speeds
        processSpeed(uuid, horizontalSpeed, verticalSpeed, deltaY, data);

        // Update last states
        lastDeltaY.put(uuid, deltaY);
        lastSpeed.put(uuid, horizontalSpeed);
    }

    private void processSpeed(UUID uuid, double horizontalSpeed, double verticalSpeed, double deltaY, MovementData data) {
        // Check for speed anomalies
        Double lastSpeedValue = lastSpeed.get(uuid);
        if (lastSpeedValue != null) {
            double acceleration = (horizontalSpeed - lastSpeedValue) / deltaY;
            if (Math.abs(acceleration) > 10.0) { // Arbitrary threshold
                data.addSpeedAnomaly(acceleration);
            }
        }

        // Check vertical movement
        if (Math.abs(verticalSpeed) > 4.0) { // Max vanilla vertical speed
            data.addVerticalAnomaly(verticalSpeed);
        }

        // Check for step/jump anomalies
        Double lastDeltaYValue = lastDeltaY.get(uuid);
        if (lastDeltaYValue != null) {
            if (deltaY > 0 && lastDeltaYValue > 0) { // Double jump check
                data.addDoubleJump(deltaY + lastDeltaYValue);
            }
        }

        data.addMovementData(horizontalSpeed, verticalSpeed, deltaY);
    }

    private void processRotationChange(UUID uuid, Location currentLoc, MovementData data) {
        Location lastLoc = lastLocation.get(uuid);
        if (lastLoc == null) return;

        float deltaYaw = Math.abs(currentLoc.getYaw() - lastLoc.getYaw());
        float deltaPitch = Math.abs(currentLoc.getPitch() - lastLoc.getPitch());

        // Normalize yaw difference
        if (deltaYaw > 180) {
            deltaYaw = 360 - deltaYaw;
        }

        data.addRotationData(deltaYaw, deltaPitch);
    }

    private void processServerPosition(Player player, WrappedPacketOutPosition packet, MovementData data) {
        // For 1.8 PacketEvents
        Location serverLoc = new Location(player.getWorld(),
                packet.getPosition().x,
                packet.getPosition().y,
                packet.getPosition().z,
                packet.getYaw(),
                packet.getPitch());

        data.addServerPosition(serverLoc);
    }

    private boolean isMovementPacket(byte packetId) {
        return packetId == PacketType.Play.Client.FLYING ||
                packetId == PacketType.Play.Client.POSITION ||
                packetId == PacketType.Play.Client.POSITION_LOOK ||
                packetId == PacketType.Play.Client.LOOK;
    }

    @Override
    public void cleanup(Player player) {
        UUID uuid = player.getUniqueId();
        lastLocation.remove(uuid);
        lastMoveTime.remove(uuid);
        lastDeltaY.remove(uuid);
        lastGround.remove(uuid);
        airTicks.remove(uuid);
        lastSpeed.remove(uuid);
    }

    @Override
    public void reset() {
        lastLocation.clear();
        lastMoveTime.clear();
        lastDeltaY.clear();
        lastGround.clear();
        airTicks.clear();
        lastSpeed.clear();
    }
}