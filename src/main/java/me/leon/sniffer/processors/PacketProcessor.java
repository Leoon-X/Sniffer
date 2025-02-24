package me.leon.sniffer.processors;

import io.github.retrooper.packetevents.event.impl.PacketPlayReceiveEvent;
import io.github.retrooper.packetevents.event.impl.PacketPlaySendEvent;
import org.bukkit.entity.Player;

public interface PacketProcessor {
    void processIncoming(Player player, PacketPlayReceiveEvent event);
    void processOutgoing(Player player, PacketPlaySendEvent event);
    void cleanup(Player player);
    void reset();
}