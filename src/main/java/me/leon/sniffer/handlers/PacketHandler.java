package me.leon.sniffer.handlers;

import io.github.retrooper.packetevents.event.PacketListenerDynamic;
import io.github.retrooper.packetevents.event.impl.PacketPlayReceiveEvent;
import io.github.retrooper.packetevents.event.impl.PacketPlaySendEvent;
import io.github.retrooper.packetevents.packettype.PacketType;
import lombok.RequiredArgsConstructor;
import me.leon.sniffer.Sniffer;
import me.leon.sniffer.data.enums.SniffType;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class PacketHandler extends PacketListenerDynamic {
    private final Sniffer plugin;
    public final Map<UUID, Integer> packetCounter = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPacketTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFlyingPacket = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> flyingPacketCount = new ConcurrentHashMap<>();

    private static final int MAX_PACKETS_PER_SECOND = 1000;
    private static final long MIN_FLYING_DELAY = 10L;

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();
        byte packetId = event.getPacketId();
        long currentTime = System.currentTimeMillis();

        // Update packet counter
        updatePacketCounter(uuid, currentTime);

        // Handle specific packet types
        if (isMovementPacket(packetId)) {
            handleMovementPacket(player, event);
        } else if (packetId == PacketType.Play.Client.USE_ENTITY) {
            handleCombatPacket(player, event);
        } else if (packetId == PacketType.Play.Client.BLOCK_DIG) {
            handleBlockDigPacket(player, event);
        } else if (packetId == PacketType.Play.Client.BLOCK_PLACE) {
            handleBlockPlacePacket(player, event);
        }

        // Check for packet spam
        if (checkPacketSpam(uuid)) {
            plugin.getLogger().warning("Excessive packets from player: " + player.getName());
        }
    }

    private void handleMovementPacket(Player player, PacketPlayReceiveEvent event) {
        UUID uuid = player.getUniqueId();
        if (!plugin.getDataManager().isSniffing(uuid, SniffType.MOVEMENT)) return;

        plugin.getMovementProcessor().processIncoming(player, event);
    }

    private void handleCombatPacket(Player player, PacketPlayReceiveEvent event) {
        UUID uuid = player.getUniqueId();
        if (!plugin.getDataManager().isSniffing(uuid, SniffType.COMBAT)) return;

        plugin.getCombatProcessor().processIncoming(player, event);
    }

    private void handleBlockDigPacket(Player player, PacketPlayReceiveEvent event) {
        UUID uuid = player.getUniqueId();
        if (!plugin.getDataManager().isSniffing(uuid, SniffType.BLOCK)) return;

        plugin.getBlockProcessor().processIncoming(player, event);
    }

    private void handleBlockPlacePacket(Player player, PacketPlayReceiveEvent event) {
        UUID uuid = player.getUniqueId();
        if (!plugin.getDataManager().isSniffing(uuid, SniffType.BLOCK)) return;

        plugin.getBlockProcessor().processIncoming(player, event);
    }

    @Override
    public void onPacketPlaySend(PacketPlaySendEvent event) {
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();
        byte packetId = event.getPacketId();

        if (packetId == PacketType.Play.Server.POSITION &&
                plugin.getDataManager().isSniffing(uuid, SniffType.MOVEMENT)) {
            plugin.getMovementProcessor().processOutgoing(player, event);
        }
    }

    private void updatePacketCounter(UUID uuid, long currentTime) {
        Long lastTime = lastPacketTime.get(uuid);
        if (lastTime == null || currentTime - lastTime >= 1000) {
            packetCounter.put(uuid, 1);
            lastPacketTime.put(uuid, currentTime);
        } else {
            packetCounter.merge(uuid, 1, Integer::sum);
        }
    }

    private boolean isMovementPacket(byte packetId) {
        return packetId == PacketType.Play.Client.FLYING ||
                packetId == PacketType.Play.Client.POSITION ||
                packetId == PacketType.Play.Client.POSITION_LOOK ||
                packetId == PacketType.Play.Client.LOOK;
    }

    private boolean checkPacketSpam(UUID uuid) {
        return packetCounter.getOrDefault(uuid, 0) > MAX_PACKETS_PER_SECOND;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        lastPacketTime.entrySet().removeIf(entry -> now - entry.getValue() > 60000);
        packetCounter.entrySet().removeIf(entry -> !lastPacketTime.containsKey(entry.getKey()));
        lastFlyingPacket.entrySet().removeIf(entry -> now - entry.getValue() > 60000);
        flyingPacketCount.entrySet().removeIf(entry -> !lastFlyingPacket.containsKey(entry.getKey()));
    }

    public void clearData(UUID uuid) {
        packetCounter.remove(uuid);
        lastPacketTime.remove(uuid);
        lastFlyingPacket.remove(uuid);
        flyingPacketCount.remove(uuid);
    }
}