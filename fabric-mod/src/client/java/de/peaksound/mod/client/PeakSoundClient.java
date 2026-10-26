package de.peaksound.mod.client;

import de.peaksound.mod.PeakSoundMod;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PeakIds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;

/** Client-Einstieg: Handshake mit dem Plugin und Registrierung der Client-Bausteine. */
public final class PeakSoundClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PeakCreativeTab.register();
        ClientNetwork.register();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerState.reset();
            AudioEngine.clear();
            ServerState.onHelloSent();
            ClientNetwork.send(new Packet.Hello(PeakIds.PROTOCOL_VERSION, PeakSoundMod.VERSION));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ServerState.reset();
            AudioEngine.clear();
            CoverCache.clear();
        });
        ClientTooltipComponentCallback.EVENT.register(CoverTooltip::create);
        ClientTickEvents.END_CLIENT_TICK.register(client -> AudioEngine.tick());
    }
}
