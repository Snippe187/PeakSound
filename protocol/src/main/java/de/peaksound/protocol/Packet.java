package de.peaksound.protocol;

import java.nio.ByteBuffer;

/**
 * Alle PeakSound-Pakete. {@link Serverbound} wird vom Client gesendet und vom Server validiert,
 * {@link Clientbound} sendet der Server. Jedes Paket kodiert sich selbst; decodiert wird zentral
 * im {@link PacketCodec}.
 */
public sealed interface Packet {
    byte[] encode();

    sealed interface Serverbound extends Packet {}

    sealed interface Clientbound extends Packet {}

    // ---- Handshake ----

    record Hello(int protocolVersion, String modVersion) implements Serverbound {
        public static final int ID = 1;
        public static final int MAX_VERSION_LENGTH = 32;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeInt(protocolVersion).writeString(modVersion, MAX_VERSION_LENGTH).toBytes();
        }

        static Hello decode(ByteBuffer in) {
            return new Hello(PacketIO.readInt(in), PacketIO.readString(in, MAX_VERSION_LENGTH));
        }
    }

    /** Antwort auf {@link Hello}; {@code serverTimeMs} dient der Uhrensynchronisation der Wiedergabe. */
    record HelloAck(int protocolVersion, boolean compatible, boolean operator, long serverTimeMs, int maxSongSeconds)
            implements Clientbound {
        public static final int ID = 2;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeInt(protocolVersion).writeBoolean(compatible)
                    .writeBoolean(operator).writeLong(serverTimeMs).writeInt(maxSongSeconds).toBytes();
        }

        static HelloAck decode(ByteBuffer in) {
            return new HelloAck(PacketIO.readInt(in), PacketIO.readBoolean(in), PacketIO.readBoolean(in),
                    PacketIO.readLong(in), PacketIO.readIntRange(in, 0, 3600));
        }
    }

    // ---- Upload ----

    /** Server -> Client: Upload-Fenster oeffnen. Das Ticket verknuepft den Upload mit Jukebox und Platte. */
    record OpenUpload(int ticket) implements Clientbound {
        public static final int ID = 3;

        @Override
        public byte[] encode() { return new PacketIO.Writer(ID).writeInt(ticket).toBytes(); }

        static OpenUpload decode(ByteBuffer in) { return new OpenUpload(PacketIO.readInt(in)); }
    }

    /** Client -> Server: Upload ankuendigen. Titel/Interpret/Cover liest der Server selbst aus der Datei. */
    record UploadStart(int ticket, String fallbackTitle, int fileSize) implements Serverbound {
        public static final int ID = 4;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeInt(ticket).writeString(fallbackTitle, Limits.MAX_TEXT)
                    .writeInt(fileSize).toBytes();
        }

        static UploadStart decode(ByteBuffer in) {
            return new UploadStart(PacketIO.readInt(in), PacketIO.readString(in, Limits.MAX_TEXT),
                    PacketIO.readIntRange(in, 1, Limits.MAX_UPLOAD_BYTES));
        }
    }

    record UploadAccept(int uploadId, int chunkSize, int window) implements Clientbound {
        public static final int ID = 5;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeInt(uploadId).writeInt(chunkSize).writeInt(window).toBytes();
        }

        static UploadAccept decode(ByteBuffer in) {
            return new UploadAccept(PacketIO.readInt(in), PacketIO.readIntRange(in, 1, Limits.UPLOAD_CHUNK_BYTES),
                    PacketIO.readIntRange(in, 1, 1024));
        }
    }

    /** Ablehnung oder Fehlschlag. {@code arg} ist je nach Grund z. B. Wartezeit in Sekunden oder Limit. */
    record UploadReject(int uploadId, Reason reason, int arg) implements Clientbound {
        public static final int ID = 6;

        public enum Reason {
            TOO_LONG, TOO_LARGE, UNSUPPORTED_FORMAT, RATE_LIMIT, QUOTA_REACHED, STORAGE_FULL,
            NO_TICKET, BUSY, CORRUPT, INTERNAL;

            static Reason byOrdinal(int o) {
                Reason[] v = values();
                if (o < 0 || o >= v.length) throw new MalformedPacketException("Unbekannter Grund");
                return v[o];
            }
        }

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeInt(uploadId).writeByte(reason.ordinal()).writeInt(arg).toBytes();
        }

        static UploadReject decode(ByteBuffer in) {
            return new UploadReject(PacketIO.readInt(in), Reason.byOrdinal(PacketIO.readByte(in)), PacketIO.readInt(in));
        }
    }

    record UploadChunk(int uploadId, int index, byte[] data) implements Serverbound {
        public static final int ID = 7;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeInt(uploadId).writeInt(index)
                    .writeBytes(data, Limits.UPLOAD_CHUNK_BYTES).toBytes();
        }

        static UploadChunk decode(ByteBuffer in) {
            return new UploadChunk(PacketIO.readInt(in), PacketIO.readIntRange(in, 0, Integer.MAX_VALUE),
                    PacketIO.readBytes(in, Limits.UPLOAD_CHUNK_BYTES));
        }
    }

    /** Kumulative Bestaetigung: alle Chunks vor {@code nextIndex} sind angekommen. */
    record UploadAck(int uploadId, int nextIndex) implements Clientbound {
        public static final int ID = 8;

        @Override
        public byte[] encode() { return new PacketIO.Writer(ID).writeInt(uploadId).writeInt(nextIndex).toBytes(); }

        static UploadAck decode(ByteBuffer in) { return new UploadAck(PacketIO.readInt(in), PacketIO.readInt(in)); }
    }

    record UploadResult(int uploadId, boolean pendingApproval, String hash, String title, String artist,
                        int durationMs, int channels) implements Clientbound {
        public static final int ID = 9;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeInt(uploadId).writeBoolean(pendingApproval)
                    .writeString(hash, Limits.HASH_LENGTH).writeString(title, Limits.MAX_TEXT)
                    .writeString(artist, Limits.MAX_TEXT).writeInt(durationMs).writeInt(channels).toBytes();
        }

        static UploadResult decode(ByteBuffer in) {
            return new UploadResult(PacketIO.readInt(in), PacketIO.readBoolean(in),
                    PacketIO.readString(in, Limits.HASH_LENGTH), PacketIO.readString(in, Limits.MAX_TEXT),
                    PacketIO.readString(in, Limits.MAX_TEXT), PacketIO.readInt(in), PacketIO.readInt(in));
        }
    }

    // ---- Song-Download ----

    record SongRequest(String hash, int index) implements Serverbound {
        public static final int ID = 10;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeString(hash, Limits.HASH_LENGTH).writeInt(index).toBytes();
        }

        static SongRequest decode(ByteBuffer in) {
            return new SongRequest(PacketIO.readString(in, Limits.HASH_LENGTH),
                    PacketIO.readIntRange(in, 0, Integer.MAX_VALUE));
        }
    }

    record SongChunk(String hash, int index, int total, byte[] data) implements Clientbound {
        public static final int ID = 11;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeString(hash, Limits.HASH_LENGTH).writeInt(index).writeInt(total)
                    .writeBytes(data, Limits.DOWNLOAD_CHUNK_BYTES).toBytes();
        }

        static SongChunk decode(ByteBuffer in) {
            return new SongChunk(PacketIO.readString(in, Limits.HASH_LENGTH), PacketIO.readInt(in),
                    PacketIO.readInt(in), PacketIO.readBytes(in, Limits.DOWNLOAD_CHUNK_BYTES));
        }
    }

    record SongUnavailable(String hash) implements Clientbound {
        public static final int ID = 12;

        @Override
        public byte[] encode() { return new PacketIO.Writer(ID).writeString(hash, Limits.HASH_LENGTH).toBytes(); }

        static SongUnavailable decode(ByteBuffer in) {
            return new SongUnavailable(PacketIO.readString(in, Limits.HASH_LENGTH));
        }
    }

    // ---- Wiedergabe ----

    /**
     * Server -> Client: eine Jukebox spielt. Die Position im Song ergibt sich aus
     * {@code offsetMs + (jetzt - serverTimeMs)}, damit spaeter Hinzukommende richtig einsteigen.
     */
    record PlaybackStart(int x, int y, int z, String hash, int offsetMs, long serverTimeMs, int durationMs,
                         int channels, int range, int volume) implements Clientbound {
        public static final int ID = 13;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).writeString(hash, Limits.HASH_LENGTH)
                    .writeInt(offsetMs).writeLong(serverTimeMs).writeInt(durationMs).writeInt(channels)
                    .writeInt(range).writeInt(volume).toBytes();
        }

        static PlaybackStart decode(ByteBuffer in) {
            return new PlaybackStart(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in),
                    PacketIO.readString(in, Limits.HASH_LENGTH), PacketIO.readIntRange(in, 0, Integer.MAX_VALUE),
                    PacketIO.readLong(in), PacketIO.readIntRange(in, 1, Integer.MAX_VALUE),
                    PacketIO.readIntRange(in, 1, 16), PacketIO.readIntRange(in, Limits.MIN_RANGE, Limits.MAX_RANGE),
                    PacketIO.readIntRange(in, 0, 100));
        }
    }

    /** Reichweite oder Lautstaerke einer spielenden Jukebox wurde geaendert (Menue). */
    record PlaybackUpdate(int x, int y, int z, int range, int volume) implements Clientbound {
        public static final int ID = 15;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).writeInt(range).writeInt(volume).toBytes();
        }

        static PlaybackUpdate decode(ByteBuffer in) {
            return new PlaybackUpdate(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in),
                    PacketIO.readIntRange(in, Limits.MIN_RANGE, Limits.MAX_RANGE), PacketIO.readIntRange(in, 0, 100));
        }
    }

    // ---- Jukebox-Menue ----

    /** Server -> Client: Menue oeffnen, mit dem vollstaendigen aktuellen Zustand. */
    record OpenJukeboxMenu(int x, int y, int z, JukeboxSettings settings, boolean playing, String title, String artist)
            implements Clientbound {
        public static final int ID = 16;

        @Override
        public byte[] encode() {
            PacketIO.Writer w = new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z);
            settings.write(w);
            w.writeBoolean(playing).writeString(title, Limits.MAX_TEXT).writeString(artist, Limits.MAX_TEXT);
            return w.toBytes();
        }

        static OpenJukeboxMenu decode(ByteBuffer in) {
            return new OpenJukeboxMenu(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in),
                    JukeboxSettings.read(in), PacketIO.readBoolean(in),
                    PacketIO.readString(in, Limits.MAX_TEXT), PacketIO.readString(in, Limits.MAX_TEXT));
        }
    }

    /** Client -> Server: geaenderte Einstellungen. Der Server prueft Entfernung, Block und Werte. */
    record JukeboxUpdate(int x, int y, int z, JukeboxSettings settings) implements Serverbound {
        public static final int ID = 17;

        @Override
        public byte[] encode() {
            PacketIO.Writer w = new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z);
            settings.write(w);
            return w.toBytes();
        }

        static JukeboxUpdate decode(ByteBuffer in) {
            return new JukeboxUpdate(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in),
                    JukeboxSettings.read(in));
        }
    }

    // ---- Cover ----

    record CoverRequest(String hash) implements Serverbound {
        public static final int ID = 18;

        @Override
        public byte[] encode() { return new PacketIO.Writer(ID).writeString(hash, Limits.HASH_LENGTH).toBytes(); }

        static CoverRequest decode(ByteBuffer in) { return new CoverRequest(PacketIO.readString(in, Limits.HASH_LENGTH)); }
    }

    /** Cover-Bild (JPEG/PNG, hoechstens {@link Limits#MAX_COVER_BYTES}); leer, wenn der Song kein Cover hat. */
    record CoverData(String hash, byte[] image) implements Clientbound {
        public static final int ID = 19;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeString(hash, Limits.HASH_LENGTH)
                    .writeBytes(image, Limits.MAX_COVER_BYTES).toBytes();
        }

        static CoverData decode(ByteBuffer in) {
            return new CoverData(PacketIO.readString(in, Limits.HASH_LENGTH), PacketIO.readBytes(in, Limits.MAX_COVER_BYTES));
        }
    }

    record PlaybackStop(int x, int y, int z) implements Clientbound {
        public static final int ID = 14;

        @Override
        public byte[] encode() { return new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).toBytes(); }

        static PlaybackStop decode(ByteBuffer in) {
            return new PlaybackStop(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in));
        }
    }
}
