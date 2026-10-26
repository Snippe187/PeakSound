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

    public static void send(Packet.Serverbound packet) {
        if (ClientPlayNetworking.canSend(RawPayload.TYPE)) {
            ClientPlayNetworking.send(new RawPayload(packet.encode()));
        }
    }

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(RawPayload.TYPE, (payload, context) -> {
            Packet.Clientbound packet;
            try {
                packet = PacketCodec.decodeClientbound(payload.data());
            } catch (MalformedPacketException e) {
                return; // unbekannte oder defekte Pakete ignorieren
            }
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
        }
    }
}
