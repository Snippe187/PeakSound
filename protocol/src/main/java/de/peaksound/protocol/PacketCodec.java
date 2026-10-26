package de.peaksound.protocol;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * Decodiert eingehende Pakete streng nach Richtung. Der Server akzeptiert nur {@link Packet.Serverbound},
 * der Client nur {@link Packet.Clientbound}. Alles andere wirft {@link MalformedPacketException}.
 */
public final class PacketCodec {
    /** Client -> Server: Plugin-Messaging erlaubt 32 KiB je Payload. */
    public static final int MAX_SERVERBOUND_SIZE = 32 * 1024 - 1;
    /** Server -> Client: Plugin-Messaging erlaubt knapp 1 MiB je Payload. */
    public static final int MAX_CLIENTBOUND_SIZE = 1024 * 1024 - 1;

    private PacketCodec() {}

    public static Packet.Serverbound decodeServerbound(byte[] data) {
        return decode(data, MAX_SERVERBOUND_SIZE, Packet.Serverbound.class);
    }

    public static Packet.Clientbound decodeClientbound(byte[] data) {
        return decode(data, MAX_CLIENTBOUND_SIZE, Packet.Clientbound.class);
    }

    private static <T extends Packet> T decode(byte[] data, int maxSize, Class<T> direction) {
        if (data.length == 0 || data.length > maxSize) throw new MalformedPacketException("Ungueltige Paketgroesse");
        ByteBuffer in = ByteBuffer.wrap(data);
        int id = in.get() & 0xFF;
        try {
            Packet packet = switch (id) {
                case Packet.Hello.ID -> Packet.Hello.decode(in);
                case Packet.HelloAck.ID -> Packet.HelloAck.decode(in);
                case Packet.OpenUpload.ID -> Packet.OpenUpload.decode(in);
                case Packet.UploadStart.ID -> Packet.UploadStart.decode(in);
                case Packet.UploadAccept.ID -> Packet.UploadAccept.decode(in);
                case Packet.UploadReject.ID -> Packet.UploadReject.decode(in);
                case Packet.UploadChunk.ID -> Packet.UploadChunk.decode(in);
                case Packet.UploadAck.ID -> Packet.UploadAck.decode(in);
                case Packet.UploadResult.ID -> Packet.UploadResult.decode(in);
                case Packet.SongRequest.ID -> Packet.SongRequest.decode(in);
                case Packet.SongChunk.ID -> Packet.SongChunk.decode(in);
                case Packet.SongUnavailable.ID -> Packet.SongUnavailable.decode(in);
                case Packet.PlaybackStart.ID -> Packet.PlaybackStart.decode(in);
                case Packet.PlaybackStop.ID -> Packet.PlaybackStop.decode(in);
                case Packet.PlaybackUpdate.ID -> Packet.PlaybackUpdate.decode(in);
                case Packet.OpenJukeboxMenu.ID -> Packet.OpenJukeboxMenu.decode(in);
                case Packet.JukeboxUpdate.ID -> Packet.JukeboxUpdate.decode(in);
                case Packet.CoverRequest.ID -> Packet.CoverRequest.decode(in);
                case Packet.PlaylistRequest.ID -> Packet.PlaylistRequest.decode(in);
                case Packet.PlaylistAction.ID -> Packet.PlaylistAction.decode(in);
                case Packet.PlaylistState.ID -> Packet.PlaylistState.decode(in);
                case Packet.OpenSpeakerMenu.ID -> Packet.OpenSpeakerMenu.decode(in);
                case Packet.SpeakerUpdate.ID -> Packet.SpeakerUpdate.decode(in);
                case Packet.SpeakerOverviewRequest.ID -> Packet.SpeakerOverviewRequest.decode(in);
                case Packet.SpeakerOverview.ID -> Packet.SpeakerOverview.decode(in);
                case Packet.OperatorState.ID -> Packet.OperatorState.decode(in);
                case Packet.OpenLightMenu.ID -> Packet.OpenLightMenu.decode(in);
                case Packet.LightUpdate.ID -> Packet.LightUpdate.decode(in);
                case Packet.LightStart.ID -> Packet.LightStart.decode(in);
                case Packet.LightStop.ID -> Packet.LightStop.decode(in);
                case Packet.AnalysisRequest.ID -> Packet.AnalysisRequest.decode(in);
                case Packet.AnalysisData.ID -> Packet.AnalysisData.decode(in);
                case Packet.DjRequest.ID -> Packet.DjRequest.decode(in);
                case Packet.DjState.ID -> Packet.DjState.decode(in);
                case Packet.DjUpdate.ID -> Packet.DjUpdate.decode(in);
                case Packet.DjDeckAction.ID -> Packet.DjDeckAction.decode(in);
                case Packet.PlaybackFade.ID -> Packet.PlaybackFade.decode(in);
                case Packet.AdminListRequest.ID -> Packet.AdminListRequest.decode(in);
                case Packet.AdminSongs.ID -> Packet.AdminSongs.decode(in);
                case Packet.AdminAction.ID -> Packet.AdminAction.decode(in);
                case Packet.CoverData.ID -> Packet.CoverData.decode(in);
                default -> throw new MalformedPacketException("Unbekannte Paket-ID " + id);
            };
            if (in.hasRemaining()) throw new MalformedPacketException("Ueberzaehlige Bytes");
            if (!direction.isInstance(packet)) throw new MalformedPacketException("Falsche Richtung fuer Paket " + id);
            return direction.cast(packet);
        } catch (BufferUnderflowException e) {
            throw new MalformedPacketException("Paket zu kurz");
        }
    }
}
