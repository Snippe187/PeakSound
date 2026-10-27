package de.peaksound.mod.client;

import de.peaksound.mod.RawPayload;
import de.peaksound.protocol.MalformedPacketException;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PacketCodec;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Senden und Verteilen der PeakSound-Pakete. Alle Handler laufen auf dem Client-Haupt-Thread. */
public final class ClientNetwork {
    private ClientNetwork() {}

    /** Diagnose: Zaehler je Pakettyp. */
    private static final java.util.Map<String, java.util.concurrent.atomic.AtomicInteger> RECEIVED = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, java.util.concurrent.atomic.AtomicInteger> SENT = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile int malformed;
    private static volatile int dropped;
    private static volatile String lastError = "-";
    /** Die ersten ungueltigen Pakete mit ID, Groesse und Anfang der Daten (zur Fehlersuche). */
    private static final java.util.List<String> INVALID = new java.util.concurrent.CopyOnWriteArrayList<>();

    static String debug() {
        return "  Gesendet: " + new java.util.TreeMap<>(SENT) + "\n  Empfangen: " + new java.util.TreeMap<>(RECEIVED) + "\n  Ungueltig empfangen: " + malformed
                + ", nicht gesendet (Server kennt Kanal nicht): " + dropped + ", letzter Fehler: " + lastError + "\n  Ungueltige Pakete (erste 12): " + INVALID + "\n";
    }

    public static void send(Packet.Serverbound packet) {
        if (ClientPlayNetworking.canSend(RawPayload.TYPE)) {
            SENT.computeIfAbsent(packet.getClass().getSimpleName(), k -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
            ClientPlayNetworking.send(new RawPayload(packet.encode()));
        } else {
            dropped++;
        }
    }

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(RawPayload.TYPE, (payload, context) -> {
            Packet.Clientbound packet;
            try {
                packet = PacketCodec.decodeClientbound(payload.data());
            } catch (MalformedPacketException e) {
                malformed++;
                lastError = e.getMessage();
                if (INVALID.size() < 12) {
                    byte[] d = payload.data();
                    StringBuilder hex = new StringBuilder();
                    for (int i = 0; i < Math.min(d.length, 24); i++) hex.append(String.format("%02x", d[i]));
                    INVALID.add("id=" + (d.length > 0 ? d[0] & 0xFF : -1) + " len=" + d.length + " (" + e.getMessage() + ") " + hex);
                }
                return; // unbekannte oder defekte Pakete ignorieren
            }
            RECEIVED.computeIfAbsent(packet.getClass().getSimpleName(), k -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
            Minecraft client = context.client();
            client.execute(() -> dispatch(packet));
        });
    }

    private static void dispatch(Packet.Clientbound packet) {
        switch (packet) {
            case Packet.HelloAck ack -> ServerState.onAck(ack.compatible(), ack.operator(), ack.serverTimeMs(), ack.maxSongSeconds());
            case Packet.OpenUpload open -> UploadClient.open(open.ticket());
            case Packet.UploadAccept accept -> UploadClient.onAccept(accept);
            case Packet.UploadAck ack -> UploadClient.onAck(ack);
            case Packet.UploadReject reject -> UploadClient.onReject(reject);
            case Packet.UploadResult result -> UploadClient.onResult(result);
            case Packet.SongChunk chunk -> SongLibrary.onChunk(chunk);
            case Packet.SongUnavailable unavailable -> SongLibrary.onUnavailable(unavailable.hash());
            case Packet.PlaybackStart start -> AudioEngine.start(start);
            case Packet.PlaybackStop stop -> AudioEngine.stop(stop.x(), stop.y(), stop.z());
            case Packet.PlaybackUpdate update -> AudioEngine.update(update);
            case Packet.OpenJukeboxMenu menu -> JukeboxMenuScreen.open(menu);
            case Packet.CoverData cover -> CoverCache.onData(cover);
            case Packet.PlaylistState state -> JukeboxMenuScreen.onPlaylist(state);
            case Packet.OpenSpeakerMenu menu -> SpeakerMenuScreen.open(menu);
            case Packet.SpeakerOverview overview -> JukeboxMenuScreen.onSpeakers(overview);
            case Packet.OperatorState state -> ServerState.setOperator(state.operator());
            case Packet.AdminSongs songs -> AdminScreen.onData(songs);
            case Packet.PlaybackFade fade -> AudioEngine.fade(fade);
            case Packet.OpenLightMenu menu -> LightMenuScreen.open(menu);
            case Packet.LightStart start -> LightShow.start(start);
            case Packet.LightStop stop -> LightShow.stop(stop.x(), stop.y(), stop.z());
            case Packet.AnalysisData data -> LightShow.onAnalysis(data);
            case Packet.OpenRemoteMenu menu -> RemoteMenuScreen.open(menu);
            case Packet.OpenLibraryMenu menu -> LibraryScreen.open(menu);
            case Packet.LibraryResults results -> LibraryScreen.onResults(results);
            case Packet.DebugInfo info -> DebugReport.onServerText(info.text());
            case Packet.OpenMicMenu menu -> MicMenuScreen.open(menu);
            case Packet.SongInfo info -> NowPlayingHud.onInfo(info);
            case Packet.LyricsData lyrics -> NowPlayingHud.onLyrics(lyrics);
            case Packet.OpenRadioMenu menu -> RadioMenuScreen.open(menu);
            case Packet.RadioStatus status -> RadioMenuScreen.onStatus(status);
            case Packet.RadioStatic noise -> StaticNoise.onPacket(noise);
            case Packet.DjState state -> DjMenuScreen.onState(state);
        }
    }
}
