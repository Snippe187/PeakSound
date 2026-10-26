package de.peaksound.mod.client;

import de.peaksound.mod.PeakSoundMod;
import de.peaksound.mod.RawPayload;
import de.peaksound.protocol.Hello;
import de.peaksound.protocol.HelloAck;
import de.peaksound.protocol.MalformedPacketException;
import de.peaksound.protocol.PacketCodec;
import de.peaksound.protocol.PeakIds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client-Einstieg: Handshake mit dem Plugin und Registrierung der Client-Bausteine. */
public final class PeakSoundClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PeakCreativeTab.register();

        ClientPlayNetworking.registerGlobalReceiver(RawPayload.TYPE, (payload, context) -> {
            Object packet;
            try {
                packet = PacketCodec.decode(payload.data());
            } catch (MalformedPacketException e) {
                return; // unbekannte oder defekte Pakete ignorieren
            }
            if (packet instanceof HelloAck ack) {
                ServerState.onAck(ack.compatible(), ack.operator());
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerState.reset();
            // Nur senden, wenn der Server den Kanal kennt (sonst bleibt das Paket ungenutzt, aber harmlos)
            if (ClientPlayNetworking.canSend(RawPayload.TYPE)) {
                ClientPlayNetworking.send(new RawPayload(new Hello(PeakIds.PROTOCOL_VERSION, PeakSoundMod.VERSION).encode()));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ServerState.reset());
    }
}
