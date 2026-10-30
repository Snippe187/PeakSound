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
        PeakModels.register();
        PeakCreativeTab.register();
        DebugReport.register();
        // Diagnose: /peaksoundstatus zeigt, was die Mod ueber den Server weiss
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("peaksoundstatus").executes(context -> {
                    boolean channel = net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(de.peaksound.mod.RawPayload.TYPE);
                    context.getSource().sendFeedback(Component.literal("PeakSound " + PeakSoundMod.VERSION + " | Protokoll " + PeakIds.PROTOCOL_VERSION
                            + " | Server kennt den Kanal: " + channel + " | Plugin antwortet: " + ServerState.active()
                            + " | Versionskonflikt: " + ServerState.versionMismatch() + " | Operator: " + ServerState.operator()));
                    if (channel && !ServerState.active()) ClientNetwork.send(new Packet.Hello(PeakIds.PROTOCOL_VERSION, PeakSoundMod.VERSION));
                    return 1;
                })));
        // Alternative zum Button im Escape-Menue (z. B. wenn ein Launcher das Pausenmenue ersetzt): /peaksoundadmin
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("peaksoundadmin").executes(context -> {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (!ServerState.operator()) {
                        context.getSource().sendFeedback(Component.translatable("peaksound.admin.denied"));
                    } else {
                        mc.execute(() -> AdminScreen.open(null));
                    }
                    return 1;
                })));
        ClientNetwork.register();
        NowPlayingHud.register();
        // /peaksoundhud und /peaksoundlyrics: Songanzeige und Songtext-Zeile ein- bzw. ausschalten
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("peaksoundhud").executes(context -> {
                ClientSettings.setHud(!ClientSettings.hud());
                context.getSource().sendFeedback(Component.translatable(ClientSettings.hud() ? "peaksound.hud.on" : "peaksound.hud.off"));
                return 1;
            }));
            dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("peaksoundlyrics").executes(context -> {
                ClientSettings.setLyrics(!ClientSettings.lyrics());
                context.getSource().sendFeedback(Component.translatable(ClientSettings.lyrics() ? "peaksound.lyrics.on" : "peaksound.lyrics.off"));
                return 1;
            }));
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerState.reset();
            AudioEngine.clear();
            LightShow.clear();
            // Das Hello geht erst raus, wenn der Weg zum Plugin feststeht (PeakCore oder alter Kanal): siehe PeakLink.tick
            PeakLink.onJoin();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ServerState.reset();
            AudioEngine.clear();
            CoverCache.clear();
            LightShow.clear();
            PreviewPlayer.stop();
            PeakLink.onDisconnect();
        });
        ClientTooltipComponentCallback.EVENT.register(CoverTooltip::create);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AudioEngine.tick();
            LightShow.tick();
            DebugReport.tick();
            if (client.player != null) PeakLink.tick();
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
