package de.peaksound.mod.client;

import de.peaksound.mod.PeakSoundMod;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PeakIds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

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
            PreviewPlayer.stop();
        });
        ClientTooltipComponentCallback.EVENT.register(CoverTooltip::create);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AudioEngine.tick();
            PreviewPlayer.tick();
        });
        // Button "Song-Verwaltung" im Escape-Menue, nur fuer Operatoren (der Server meldet den Status).
        // Erst nach dem Spielstart registrieren: Wer die Screen-Events zu frueh anfasst, laedt Minecraft-Klassen
        // zu einem Zeitpunkt, an dem ihre Mixins noch nicht bereit sind (VerifyError beim Start).
        ClientLifecycleEvents.CLIENT_STARTED.register(started -> ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof PauseScreen && ServerState.operator()) {
                Screens.getWidgets(screen).add(Button.builder(Component.translatable("peaksound.admin.button"),
                        b -> AdminScreen.open(screen)).bounds(scaledWidth - 124, scaledHeight - 28, 118, 20).build());
            }
        }));    }
}
