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
                         int channels, int range, int volume, int speakerChannel, int speakerKind) implements Clientbound {
        public static final int ID = 13;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).writeString(hash, Limits.HASH_LENGTH)
                    .writeInt(offsetMs).writeLong(serverTimeMs).writeInt(durationMs).writeInt(channels)
                    .writeInt(range).writeInt(volume).writeByte(speakerChannel).writeByte(speakerKind).toBytes();
        }

        static PlaybackStart decode(ByteBuffer in) {
            return new PlaybackStart(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in),
                    PacketIO.readString(in, Limits.HASH_LENGTH), PacketIO.readIntRange(in, 0, Integer.MAX_VALUE),
                    PacketIO.readLong(in), PacketIO.readIntRange(in, 1, Integer.MAX_VALUE),
                    PacketIO.readIntRange(in, 1, 16), PacketIO.readIntRange(in, Limits.MIN_RANGE, Limits.MAX_RANGE),
                    PacketIO.readIntRange(in, 0, 100), readSpeakerChannel(in), SpeakerKind.byOrdinal(PacketIO.readByte(in)).ordinal());
        }
    }

    /** 0 = voller Mono-Mix (Jukebox ohne Lautsprecher), 1-12 = Lautsprecher-Kanal (Ordinal + 1). */
    private static int readSpeakerChannel(ByteBuffer in) {
        int v = PacketIO.readByte(in);
        if (v > SpeakerChannel.values().length) throw new MalformedPacketException("Unbekannter Kanal");
        return v;
    }

    /** Reichweite oder Lautstaerke einer spielenden Jukebox oder eines Lautsprechers wurde geaendert (Menue). */
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

    // ---- Playlist (CD-Wechsler) ----

    /** Client -> Server: aktuellen Playlist-Zustand anfordern (das Menue fragt etwa einmal pro Sekunde). */
    record PlaylistRequest(int x, int y, int z) implements Serverbound {
        public static final int ID = 20;

        @Override
        public byte[] encode() { return new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).toBytes(); }

        static PlaylistRequest decode(ByteBuffer in) {
            return new PlaylistRequest(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in));
        }
    }

    /** Client -> Server: Aktion auf der Playlist. {@code index} ist der Eintrag (bei SKIP/STOP ignoriert). */
    record PlaylistAction(int x, int y, int z, Action action, int index) implements Serverbound {
        public static final int ID = 21;

        public enum Action {
            PLAY, SKIP, STOP, MOVE_UP, MOVE_DOWN;

            static Action byOrdinal(int o) {
                Action[] v = values();
                if (o < 0 || o >= v.length) throw new MalformedPacketException("Unbekannte Aktion");
                return v[o];
            }
        }

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).writeByte(action.ordinal())
                    .writeInt(index).toBytes();
        }

        static PlaylistAction decode(ByteBuffer in) {
            return new PlaylistAction(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in),
                    Action.byOrdinal(PacketIO.readByte(in)), PacketIO.readIntRange(in, 0, PlaylistLogic.MAX_ENTRIES));
        }
    }

    /** Ein Playlist-Eintrag. {@code available} = false, wenn der Song fehlt oder noch gesperrt ist. */
    record PlaylistEntry(String title, String artist, int durationMs, boolean available, int slot) {
        /** Ohne Slot (z. B. DJ-Pult-Listen): -1. */
        public PlaylistEntry(String title, String artist, int durationMs, boolean available) {
            this(title, artist, durationMs, available, -1);
        }
    }

    /** Server -> Client: die Playlist (Hopper + Kisten), der laufende Eintrag ({@code -1} = keiner). */
    private static int readInventorySlot(ByteBuffer in) {
        int slot = PacketIO.readByte(in);
        if (slot >= PlaylistLogic.INVENTORY_SLOTS) throw new MalformedPacketException("Ungueltiger Slot");
        return slot;
    }

    record PlaylistState(int x, int y, int z, int current, boolean playing, java.util.List<PlaylistEntry> entries)
            implements Clientbound {
        public static final int ID = 22;

        @Override
        public byte[] encode() {
            if (entries.size() > PlaylistLogic.MAX_ENTRIES) throw new IllegalArgumentException("Playlist zu lang");
            PacketIO.Writer w = new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).writeInt(current)
                    .writeBoolean(playing).writeShort(entries.size());
            for (PlaylistEntry e : entries) {
                w.writeString(e.title(), Limits.MAX_TEXT).writeString(e.artist(), Limits.MAX_TEXT)
                        .writeInt(e.durationMs()).writeBoolean(e.available()).writeByte(Math.max(0, Math.min(PlaylistLogic.INVENTORY_SLOTS - 1, e.slot())));
            }
            return w.toBytes();
        }

        static PlaylistState decode(ByteBuffer in) {
            int x = PacketIO.readInt(in), y = PacketIO.readInt(in), z = PacketIO.readInt(in);
            int current = PacketIO.readInt(in);
            boolean playing = PacketIO.readBoolean(in);
            int count = PacketIO.readShort(in);
            if (count > PlaylistLogic.MAX_ENTRIES) throw new MalformedPacketException("Playlist zu lang");
            java.util.List<PlaylistEntry> list = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(new PlaylistEntry(PacketIO.readString(in, Limits.MAX_TEXT), PacketIO.readString(in, Limits.MAX_TEXT),
                        PacketIO.readInt(in), PacketIO.readBoolean(in), readInventorySlot(in)));
            }
            if (current < -1 || current >= count) throw new MalformedPacketException("Ungueltiger Playlist-Index");
            return new PlaylistState(x, y, z, current, playing, java.util.List.copyOf(list));
        }
    }

    // ---- Lautsprecher ----

    /** Eintrag der Lautsprecher-Uebersicht im Jukebox-Menue. */
    record SpeakerEntry(int number, String name, SpeakerChannel channel, int volume) {}

    /** Server -> Client: Lautsprecher-Menue oeffnen. {@code number} 0 = nicht mit einer Jukebox verbunden. */
    record OpenSpeakerMenu(int x, int y, int z, SpeakerSettings settings, int number, String jukeboxName)
            implements Clientbound {
        public static final int ID = 23;

        @Override
        public byte[] encode() {
            PacketIO.Writer w = new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z);
            settings.write(w);
            w.writeByte(number).writeString(jukeboxName, Limits.MAX_TEXT);
            return w.toBytes();
        }

        static OpenSpeakerMenu decode(ByteBuffer in) {
            int x = PacketIO.readInt(in), y = PacketIO.readInt(in), z = PacketIO.readInt(in);
            SpeakerSettings s = SpeakerSettings.read(in);
            int number = PacketIO.readByte(in);
            if (number > SpeakerNumbering.MAX_NUMBER) throw new MalformedPacketException("Ungueltige Nummer");
            return new OpenSpeakerMenu(x, y, z, s, number, PacketIO.readString(in, Limits.MAX_TEXT));
        }
    }

    /** Client -> Server: geaenderte Lautsprecher-Einstellungen. Der Server prueft Entfernung, Block und Werte. */
    record SpeakerUpdate(int x, int y, int z, SpeakerSettings settings) implements Serverbound {
        public static final int ID = 24;

        @Override
        public byte[] encode() {
            PacketIO.Writer w = new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z);
            settings.write(w);
            return w.toBytes();
        }

        static SpeakerUpdate decode(ByteBuffer in) {
            return new SpeakerUpdate(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in), SpeakerSettings.read(in));
        }
    }

    /** Client -> Server: Lautsprecher-Uebersicht der Jukebox an dieser Position anfordern. */
    record SpeakerOverviewRequest(int x, int y, int z) implements Serverbound {
        public static final int ID = 25;

        @Override
        public byte[] encode() { return new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).toBytes(); }

        static SpeakerOverviewRequest decode(ByteBuffer in) {
            return new SpeakerOverviewRequest(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in));
        }
    }

    /** Server -> Client: alle Lautsprecher, die mit dieser Jukebox verbunden sind. */
    record SpeakerOverview(int x, int y, int z, java.util.List<SpeakerEntry> speakers) implements Clientbound {
        public static final int ID = 26;

        @Override
        public byte[] encode() {
            if (speakers.size() > SpeakerNumbering.MAX_NUMBER) throw new IllegalArgumentException("Zu viele Lautsprecher");
            PacketIO.Writer w = new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).writeByte(speakers.size());
            for (SpeakerEntry e : speakers) {
                w.writeByte(e.number()).writeString(e.name(), SpeakerSettings.MAX_NAME)
                        .writeByte(e.channel().ordinal()).writeByte(e.volume());
            }
            return w.toBytes();
        }

        static SpeakerOverview decode(ByteBuffer in) {
            int x = PacketIO.readInt(in), y = PacketIO.readInt(in), z = PacketIO.readInt(in);
            int count = PacketIO.readByte(in);
            if (count > SpeakerNumbering.MAX_NUMBER) throw new MalformedPacketException("Zu viele Lautsprecher");
            java.util.List<SpeakerEntry> list = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int number = PacketIO.readByte(in);
                String name = PacketIO.readString(in, SpeakerSettings.MAX_NAME);
                SpeakerChannel channel = SpeakerChannel.byOrdinal(PacketIO.readByte(in));
                int volume = PacketIO.readByte(in);
                if (number < 1 || number > SpeakerNumbering.MAX_NUMBER || volume > 100) {
                    throw new MalformedPacketException("Ungueltiger Lautsprecher-Eintrag");
                }
                list.add(new SpeakerEntry(number, name, channel, volume));
            }
            return new SpeakerOverview(x, y, z, java.util.List.copyOf(list));
        }
    }

    // ---- OP-Menue (Song-Verwaltung) ----

    /** Server -> Client: Der Operator-Status hat sich geaendert (der Escape-Menue-Button erscheint oder verschwindet). */
    record OperatorState(boolean operator) implements Clientbound {
        public static final int ID = 27;

        @Override
        public byte[] encode() { return new PacketIO.Writer(ID).writeBoolean(operator).toBytes(); }

        static OperatorState decode(ByteBuffer in) { return new OperatorState(PacketIO.readBoolean(in)); }
    }

    /** Client -> Server: Song-Verwaltung oeffnen / aktualisieren. Nur Operatoren bekommen eine Antwort. */
    record AdminListRequest() implements Serverbound {
        public static final int ID = 28;

        @Override
        public byte[] encode() { return new PacketIO.Writer(ID).toBytes(); }

        static AdminListRequest decode(ByteBuffer in) { return new AdminListRequest(); }
    }

    /** Ein Song in der Verwaltung. {@code pending} = gesperrte Mehrkanal-Datei, wartet auf Freigabe. */
    record AdminSong(String hash, String title, String artist, String uploader, int channels, int durationMs,
                     int sizeBytes, boolean pending, long uploadedAt) {}

    /** Ein Spieler mit Upload-Limit. {@code max} = {@link LimitLogic#USE_DEFAULT}, wenn der Standard gilt (0 = unbegrenzt). */
    record AdminPlayer(String name, long uuidHigh, long uuidLow, int songs, int max) {}

    /**
     * Server -> Client: Daten der Song-Verwaltung. {@code songs} enthaelt hoechstens {@link #MAX_ADMIN_SONGS} Eintraege
     * (die neuesten), {@code totalSongs} die Gesamtzahl. {@code maxBytes} 0 = unbegrenzt.
     */
    record AdminSongs(java.util.List<AdminSong> songs, int totalSongs, long usedBytes, long maxBytes, int defaultLimit,
                      java.util.List<AdminPlayer> players) implements Clientbound {
        public static final int ID = 29;
        public static final int MAX_ADMIN_SONGS = 400;
        public static final int MAX_ADMIN_PLAYERS = 200;

        @Override
        public byte[] encode() {
            if (songs.size() > MAX_ADMIN_SONGS || players.size() > MAX_ADMIN_PLAYERS) throw new IllegalArgumentException("Liste zu lang");
            PacketIO.Writer w = new PacketIO.Writer(ID).writeShort(songs.size()).writeInt(totalSongs).writeLong(usedBytes)
                    .writeLong(maxBytes).writeInt(defaultLimit).writeShort(players.size());
            for (AdminSong s : songs) {
                w.writeString(s.hash(), Limits.HASH_LENGTH).writeString(s.title(), Limits.MAX_TEXT).writeString(s.artist(), Limits.MAX_TEXT)
                        .writeString(s.uploader(), Limits.MAX_TEXT).writeInt(s.channels()).writeInt(s.durationMs())
                        .writeInt(s.sizeBytes()).writeBoolean(s.pending()).writeLong(s.uploadedAt());
            }
            for (AdminPlayer p : players) {
                w.writeString(p.name(), Limits.MAX_TEXT).writeLong(p.uuidHigh()).writeLong(p.uuidLow()).writeInt(p.songs()).writeInt(p.max());
            }
            return w.toBytes();
        }

        static AdminSongs decode(ByteBuffer in) {
            int songCount = PacketIO.readShort(in);
            if (songCount > MAX_ADMIN_SONGS) throw new MalformedPacketException("Zu viele Songs");
            int total = PacketIO.readInt(in);
            long used = PacketIO.readLong(in);
            long max = PacketIO.readLong(in);
            int def = PacketIO.readInt(in);
            int playerCount = PacketIO.readShort(in);
            if (playerCount > MAX_ADMIN_PLAYERS) throw new MalformedPacketException("Zu viele Spieler");
            java.util.List<AdminSong> songs = new java.util.ArrayList<>(songCount);
            for (int i = 0; i < songCount; i++) {
                songs.add(new AdminSong(PacketIO.readString(in, Limits.HASH_LENGTH), PacketIO.readString(in, Limits.MAX_TEXT),
                        PacketIO.readString(in, Limits.MAX_TEXT), PacketIO.readString(in, Limits.MAX_TEXT), PacketIO.readInt(in),
                        PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readBoolean(in), PacketIO.readLong(in)));
            }
            java.util.List<AdminPlayer> players = new java.util.ArrayList<>(playerCount);
            for (int i = 0; i < playerCount; i++) {
                players.add(new AdminPlayer(PacketIO.readString(in, Limits.MAX_TEXT), PacketIO.readLong(in), PacketIO.readLong(in),
                        PacketIO.readInt(in), PacketIO.readInt(in)));
            }
            return new AdminSongs(java.util.List.copyOf(songs), total, used, max, def, java.util.List.copyOf(players));
        }
    }

    /**
     * Client -> Server: eine Verwaltungs-Aktion. Der Server prueft bei JEDER Aktion erneut, ob der Spieler Operator ist.
     * {@code hash} gilt fuer Song-Aktionen, {@code uuid*} fuer Spieler-Limits, {@code value} fuer Zahlenwerte
     * (Songlimit, Speicher in MB; {@link LimitLogic#USE_DEFAULT} setzt ein Spielerlimit zurueck).
     */
    record AdminAction(Action action, String hash, long uuidHigh, long uuidLow, long value) implements Serverbound {
        public static final int ID = 30;

        public enum Action {
            DELETE_SONG, APPROVE_SONG, REJECT_SONG, SET_PLAYER_LIMIT, SET_DEFAULT_LIMIT, SET_STORAGE_MB;

            static Action byOrdinal(int o) {
                Action[] v = values();
                if (o < 0 || o >= v.length) throw new MalformedPacketException("Unbekannte Aktion");
                return v[o];
            }
        }

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeByte(action.ordinal()).writeString(hash, Limits.HASH_LENGTH)
                    .writeLong(uuidHigh).writeLong(uuidLow).writeLong(value).toBytes();
        }

        static AdminAction decode(ByteBuffer in) {
            Action action = Action.byOrdinal(PacketIO.readByte(in));
            String hash = PacketIO.readString(in, Limits.HASH_LENGTH);
            long high = PacketIO.readLong(in), low = PacketIO.readLong(in), value = PacketIO.readLong(in);
            if (value < LimitLogic.USE_DEFAULT || value > LimitLogic.MAX_STORAGE_MB) throw new MalformedPacketException("Wert ausserhalb des Bereichs");
            return new AdminAction(action, hash, high, low, value);
        }
    }

    // ---- Scheinwerfer ----

    /** Server -> Client: Scheinwerfer-Menue oeffnen. {@code connected} = an eine Jukebox angeschlossen. */
    record OpenLightMenu(int x, int y, int z, LightSettings settings, boolean connected) implements Clientbound {
        public static final int ID = 31;

        @Override
        public byte[] encode() {
            PacketIO.Writer w = new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z);
            settings.write(w);
            return w.writeBoolean(connected).toBytes();
        }

        static OpenLightMenu decode(ByteBuffer in) {
            return new OpenLightMenu(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in),
                    LightSettings.read(in), PacketIO.readBoolean(in));
        }
    }

    /** Client -> Server: geaenderte Scheinwerfer-Einstellungen. */
    record LightUpdate(int x, int y, int z, LightSettings settings) implements Serverbound {
        public static final int ID = 32;

        @Override
        public byte[] encode() {
            PacketIO.Writer w = new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z);
            settings.write(w);
            return w.toBytes();
        }

        static LightUpdate decode(ByteBuffer in) {
            return new LightUpdate(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in), LightSettings.read(in));
        }
    }

    /**
     * Server -> Client: Ein Scheinwerfer in der Naehe leuchtet. Der Client berechnet Farbe, Helligkeit und Schwenk aus
     * den Einstellungen und, im Takt-Modus, aus der Songposition ({@code hash}, {@code startedAtServerMs}; leer =
     * es spielt nichts). Weil alle dieselbe Serveruhr nutzen, sind die Effekte bei allen synchron.
     */
    record LightStart(int x, int y, int z, LightSettings settings, String hash, long startedAtServerMs)
            implements Clientbound {
        public static final int ID = 33;

        @Override
        public byte[] encode() {
            PacketIO.Writer w = new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z);
            settings.write(w);
            return w.writeString(hash, Limits.HASH_LENGTH).writeLong(startedAtServerMs).toBytes();
        }

        static LightStart decode(ByteBuffer in) {
            return new LightStart(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in), LightSettings.read(in),
                    PacketIO.readString(in, Limits.HASH_LENGTH), PacketIO.readLong(in));
        }
    }

    record LightStop(int x, int y, int z) implements Clientbound {
        public static final int ID = 34;

        @Override
        public byte[] encode() { return new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).toBytes(); }

        static LightStop decode(ByteBuffer in) {
            return new LightStop(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in));
        }
    }

    /** Client -> Server: Musikanalyse (Takt, Baender) eines Songs fuer die Lichteffekte. */
    record AnalysisRequest(String hash) implements Serverbound {
        public static final int ID = 35;

        @Override
        public byte[] encode() { return new PacketIO.Writer(ID).writeString(hash, Limits.HASH_LENGTH).toBytes(); }

        static AnalysisRequest decode(ByteBuffer in) { return new AnalysisRequest(PacketIO.readString(in, Limits.HASH_LENGTH)); }
    }

    record AnalysisData(String hash, byte[] data) implements Clientbound {
        public static final int ID = 36;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeString(hash, Limits.HASH_LENGTH).writeBytes(data, Limits.MAX_ANALYSIS_BYTES).toBytes();
        }

        static AnalysisData decode(ByteBuffer in) {
            return new AnalysisData(PacketIO.readString(in, Limits.HASH_LENGTH), PacketIO.readBytes(in, Limits.MAX_ANALYSIS_BYTES));
        }
    }

    // ---- DJ-Pult ----

    /** Zustand eines Decks fuer das DJ-Menue. {@code connected} = es haengt eine Jukebox daran. */
    record DjDeck(boolean connected, String jukeboxName, boolean playing, String title, int current,
                  java.util.List<PlaylistEntry> entries) {
        public static final int MAX_ENTRIES = 40;

        void write(PacketIO.Writer w) {
            if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Deck-Playlist zu lang");
            w.writeBoolean(connected).writeString(jukeboxName, Limits.MAX_TEXT).writeBoolean(playing)
                    .writeString(title, Limits.MAX_TEXT).writeInt(current).writeByte(entries.size());
            for (PlaylistEntry e : entries) {
                w.writeString(e.title(), Limits.MAX_TEXT).writeString(e.artist(), Limits.MAX_TEXT)
                        .writeInt(e.durationMs()).writeBoolean(e.available());
            }
        }

        static DjDeck read(ByteBuffer in) {
            boolean connected = PacketIO.readBoolean(in);
            String name = PacketIO.readString(in, Limits.MAX_TEXT);
            boolean playing = PacketIO.readBoolean(in);
            String title = PacketIO.readString(in, Limits.MAX_TEXT);
            int current = PacketIO.readInt(in);
            int count = PacketIO.readByte(in);
            if (count > MAX_ENTRIES || current < -1 || current >= Math.max(count, 1) && current != -1) {
                throw new MalformedPacketException("Ungueltiges Deck");
            }
            java.util.List<PlaylistEntry> list = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(new PlaylistEntry(PacketIO.readString(in, Limits.MAX_TEXT), PacketIO.readString(in, Limits.MAX_TEXT),
                        PacketIO.readInt(in), PacketIO.readBoolean(in)));
            }
            return new DjDeck(connected, name, playing, title, current, java.util.List.copyOf(list));
        }
    }

    /** Client -> Server: DJ-Zustand anfordern (das Menue fragt etwa einmal pro Sekunde). */
    record DjRequest(int x, int y, int z) implements Serverbound {
        public static final int ID = 37;

        @Override
        public byte[] encode() { return new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).toBytes(); }

        static DjRequest decode(ByteBuffer in) { return new DjRequest(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in)); }
    }

    /** Server -> Client: DJ-Zustand. {@code open} = das Menue soll sich oeffnen (Antwort auf den Tuner-Klick). */
    record DjState(int x, int y, int z, DjSettings settings, boolean open, DjDeck deckA, DjDeck deckB)
            implements Clientbound {
        public static final int ID = 38;

        @Override
        public byte[] encode() {
            PacketIO.Writer w = new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z);
            settings.write(w);
            w.writeBoolean(open);
            deckA.write(w);
            deckB.write(w);
            return w.toBytes();
        }

        static DjState decode(ByteBuffer in) {
            int x = PacketIO.readInt(in), y = PacketIO.readInt(in), z = PacketIO.readInt(in);
            DjSettings s = DjSettings.read(in);
            boolean open = PacketIO.readBoolean(in);
            return new DjState(x, y, z, s, open, DjDeck.read(in), DjDeck.read(in));
        }
    }

    /** Client -> Server: geaenderte DJ-Einstellungen. */
    record DjUpdate(int x, int y, int z, DjSettings settings) implements Serverbound {
        public static final int ID = 39;

        @Override
        public byte[] encode() {
            PacketIO.Writer w = new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z);
            settings.write(w);
            return w.toBytes();
        }

        static DjUpdate decode(ByteBuffer in) {
            return new DjUpdate(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in), DjSettings.read(in));
        }
    }

    /** Client -> Server: Song auf einem Deck waehlen oder stoppen. {@code deck} 0 = A, 1 = B. */
    record DjDeckAction(int x, int y, int z, int deck, Action action, int index) implements Serverbound {
        public static final int ID = 40;

        public enum Action {
            PLAY, SKIP, STOP;

            static Action byOrdinal(int o) {
                Action[] v = values();
                if (o < 0 || o >= v.length) throw new MalformedPacketException("Unbekannte Aktion");
                return v[o];
            }
        }

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).writeByte(deck).writeByte(action.ordinal())
                    .writeInt(index).toBytes();
        }

        static DjDeckAction decode(ByteBuffer in) {
            int x = PacketIO.readInt(in), y = PacketIO.readInt(in), z = PacketIO.readInt(in);
            int deck = PacketIO.readByte(in);
            if (deck > 1) throw new MalformedPacketException("Unbekanntes Deck");
            return new DjDeckAction(x, y, z, deck, Action.byOrdinal(PacketIO.readByte(in)),
                    PacketIO.readIntRange(in, 0, PlaylistLogic.MAX_ENTRIES));
        }
    }

    /**
     * Server -> Client: Die Lautstaerke einer Schallquelle faehrt in {@code durationMs} auf {@code toVolume}
     * (Ueberblenden des DJ-Pults). Der Client rampt lokal, deshalb braucht es nur ein Paket je Ueberblendung.
     */
    record PlaybackFade(int x, int y, int z, int toVolume, int durationMs) implements Clientbound {
        public static final int ID = 41;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).writeInt(toVolume).writeInt(durationMs).toBytes();
        }

        static PlaybackFade decode(ByteBuffer in) {
            return new PlaybackFade(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in),
                    PacketIO.readIntRange(in, 0, 100), PacketIO.readIntRange(in, 0, 60_000));
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

    // ---- Mikrofon ----

    /** Server -> Client: Mikrofon-Menue oeffnen. {@code voiceChat} = Simple Voice Chat laeuft auf dem Server. */
    record OpenMicMenu(int x, int y, int z, MicSettings settings, boolean connected, boolean voiceChat) implements Clientbound {
        public static final int ID = 42;

        @Override
        public byte[] encode() {
            PacketIO.Writer w = new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z);
            settings.write(w);
            return w.writeBoolean(connected).writeBoolean(voiceChat).toBytes();
        }

        static OpenMicMenu decode(ByteBuffer in) {
            return new OpenMicMenu(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in), MicSettings.read(in),
                    PacketIO.readBoolean(in), PacketIO.readBoolean(in));
        }
    }

    /** Client -> Server: geaenderte Mikrofon-Einstellungen. */
    record MicUpdate(int x, int y, int z, MicSettings settings) implements Serverbound {
        public static final int ID = 43;

        @Override
        public byte[] encode() {
            PacketIO.Writer w = new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z);
            settings.write(w);
            return w.toBytes();
        }

        static MicUpdate decode(ByteBuffer in) {
            return new MicUpdate(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in), MicSettings.read(in));
        }
    }

    // ---- Anzeige: Songinfo und Songtext ----

    /** Server -> Client: Titel eines Songs, der gleich in Hoerweite spielt (vor dem PlaybackStart gesendet; Anzeige am Bildschirm). */
    record SongInfo(String hash, String title, String artist, long startedAtServerMs, int durationMs) implements Clientbound {
        public static final int ID = 58;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeString(hash, Limits.HASH_LENGTH).writeString(title, Limits.MAX_TEXT)
                    .writeString(artist, Limits.MAX_TEXT).writeLong(startedAtServerMs).writeInt(durationMs).toBytes();
        }

        static SongInfo decode(ByteBuffer in) {
            return new SongInfo(PacketIO.readString(in, Limits.HASH_LENGTH), PacketIO.readString(in, Limits.MAX_TEXT),
                    PacketIO.readString(in, Limits.MAX_TEXT), PacketIO.readLong(in), PacketIO.readIntRange(in, 0, Integer.MAX_VALUE));
        }
    }

    /** Client -> Server: Songtext eines Songs anfordern. */
    record LyricsRequest(String hash) implements Serverbound {
        public static final int ID = 59;

        @Override
        public byte[] encode() { return new PacketIO.Writer(ID).writeString(hash, Limits.HASH_LENGTH).toBytes(); }

        static LyricsRequest decode(ByteBuffer in) { return new LyricsRequest(PacketIO.readString(in, Limits.HASH_LENGTH)); }
    }

    /** Server -> Client: Songtext ("" = der Song hat keinen). */
    record LyricsData(String hash, String text) implements Clientbound {
        public static final int ID = 60;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeString(hash, Limits.HASH_LENGTH).writeString(text, Limits.MAX_LYRICS_CHARS).toBytes();
        }

        static LyricsData decode(ByteBuffer in) {
            return new LyricsData(PacketIO.readString(in, Limits.HASH_LENGTH), PacketIO.readString(in, Limits.MAX_LYRICS_CHARS));
        }
    }

    // ---- Radio ----

    /**
     * Server -> Client: Radio-Menue oeffnen. {@code transmitter} unterscheidet Sender und Empfaenger,
     * {@code station} ist der Titel des Senders auf der Frequenz (Empfaenger) bzw. was der Sender gerade sendet ("" = nichts).
     */
    record OpenRadioMenu(int x, int y, int z, RadioSettings settings, boolean transmitter, String station) implements Clientbound {
        public static final int ID = 54;

        @Override
        public byte[] encode() {
            PacketIO.Writer w = new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z);
            settings.write(w);
            return w.writeBoolean(transmitter).writeString(station, Limits.MAX_TEXT).toBytes();
        }

        static OpenRadioMenu decode(ByteBuffer in) {
            return new OpenRadioMenu(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in), RadioSettings.read(in),
                    PacketIO.readBoolean(in), PacketIO.readString(in, Limits.MAX_TEXT));
        }
    }

    /** Client -> Server: geaenderte Radio-Einstellungen. */
    record RadioUpdate(int x, int y, int z, RadioSettings settings) implements Serverbound {
        public static final int ID = 55;

        @Override
        public byte[] encode() {
            PacketIO.Writer w = new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z);
            settings.write(w);
            return w.toBytes();
        }

        static RadioUpdate decode(ByteBuffer in) {
            return new RadioUpdate(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in), RadioSettings.read(in));
        }
    }

    /** Server -> Client: der Sender auf der Frequenz hat gewechselt (Anzeige im offenen Radio-Menue). */
    record RadioStatus(int x, int y, int z, String station) implements Clientbound {
        public static final int ID = 56;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).writeString(station, Limits.MAX_TEXT).toBytes();
        }

        static RadioStatus decode(ByteBuffer in) {
            return new RadioStatus(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readString(in, Limits.MAX_TEXT));
        }
    }

    /** Server -> Client: Rauschen an einem Empfaenger. {@code level} 1-100 = Staerke, 0 = Rauschen beenden. */
    record RadioStatic(int x, int y, int z, int range, int volume, int level) implements Clientbound {
        public static final int ID = 57;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).writeByte(range).writeByte(volume).writeByte(level).toBytes();
        }

        static RadioStatic decode(ByteBuffer in) {
            int x = PacketIO.readInt(in), y = PacketIO.readInt(in), z = PacketIO.readInt(in);
            int range = PacketIO.readByte(in), volume = PacketIO.readByte(in), level = PacketIO.readByte(in);
            if (range < 1 || range > RadioSettings.MAX_RANGE || volume > 100 || level > 100) throw new MalformedPacketException("Wert ausserhalb des Bereichs");
            return new RadioStatic(x, y, z, range, volume, level);
        }
    }

    // ---- Diagnose ----

    /** Client -> Server: Diagnosetext anfordern (wird auch ohne abgeschlossenen Handshake beantwortet). */
    record DebugRequest() implements Serverbound {
        public static final int ID = 44;

        @Override
        public byte[] encode() { return new PacketIO.Writer(ID).toBytes(); }

        static DebugRequest decode(ByteBuffer in) { return new DebugRequest(); }
    }

    /** Server -> Client: Diagnosetext (UTF-8). */
    record DebugInfo(String text) implements Clientbound {
        public static final int ID = 45;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeBytes(text.getBytes(java.nio.charset.StandardCharsets.UTF_8), Limits.MAX_DEBUG_BYTES).toBytes();
        }

        static DebugInfo decode(ByteBuffer in) {
            return new DebugInfo(new String(PacketIO.readBytes(in, Limits.MAX_DEBUG_BYTES), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    // ---- Song-Bibliothek ----

    /** Ein Song in der Bibliothek (Anzeige in der Liste). */
    record LibrarySong(String hash, String title, String artist, String uploader, int durationMs,
                       boolean favorite, boolean mine, boolean hasCover, int plays) {}

    /** Server -> Client: Bibliotheks-Menue oeffnen. Die Liste folgt auf eine {@link LibraryQuery}. */
    record OpenLibraryMenu(int x, int y, int z) implements Clientbound {
        public static final int ID = 46;

        @Override
        public byte[] encode() { return new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).toBytes(); }

        static OpenLibraryMenu decode(ByteBuffer in) { return new OpenLibraryMenu(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in)); }
    }

    /** Client -> Server: Suche und Seite anfordern (leere Suche = alle Songs). */
    record LibraryQuery(int x, int y, int z, String query, int page, LibraryLogic.Sort sort, LibraryLogic.Filter filter)
            implements Serverbound {
        public static final int ID = 47;

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).writeString(query, Limits.LIBRARY_MAX_QUERY)
                    .writeInt(page).writeByte(sort.ordinal()).writeByte(filter.ordinal()).toBytes();
        }

        static LibraryQuery decode(ByteBuffer in) {
            return new LibraryQuery(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in),
                    PacketIO.readString(in, Limits.LIBRARY_MAX_QUERY), PacketIO.readIntRange(in, 0, 100_000),
                    LibraryLogic.Sort.byOrdinal(PacketIO.readByte(in)), LibraryLogic.Filter.byOrdinal(PacketIO.readByte(in)));
        }
    }

    /** Client -> Server: eigenen Song umbenennen/loeschen oder Favorit umschalten (Operatoren duerfen auch fremde verwalten). */
    record LibraryManage(int x, int y, int z, Kind kind, String hash, String text) implements Serverbound {
        public static final int ID = 53;

        public enum Kind {
            RENAME, DELETE, FAVORITE;

            static Kind byOrdinal(int o) {
                Kind[] v = values();
                if (o < 0 || o >= v.length) throw new MalformedPacketException("Unbekannte Aktion");
                return v[o];
            }
        }

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).writeByte(kind.ordinal())
                    .writeString(hash, Limits.HASH_LENGTH).writeString(text, Limits.MAX_TEXT).toBytes();
        }

        static LibraryManage decode(ByteBuffer in) {
            return new LibraryManage(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in), Kind.byOrdinal(PacketIO.readByte(in)),
                    PacketIO.readString(in, Limits.HASH_LENGTH), PacketIO.readString(in, Limits.MAX_TEXT));
        }
    }

    /** Server -> Client: eine Seite der Trefferliste. {@code blank} = wie viele leere Platten der Spieler dabei hat. */
    record LibraryResults(int x, int y, int z, int page, int pages, int total, int blank, java.util.List<LibrarySong> songs)
            implements Clientbound {
        public static final int ID = 48;

        @Override
        public byte[] encode() {
            if (songs.size() > Limits.LIBRARY_PAGE_SIZE) throw new IllegalArgumentException("Seite zu lang");
            PacketIO.Writer w = new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).writeInt(page).writeInt(pages)
                    .writeInt(total).writeInt(blank).writeByte(songs.size());
            for (LibrarySong s : songs) {
                w.writeString(s.hash(), Limits.HASH_LENGTH).writeString(s.title(), Limits.MAX_TEXT).writeString(s.artist(), Limits.MAX_TEXT)
                        .writeString(s.uploader(), Limits.MAX_TEXT).writeInt(s.durationMs()).writeBoolean(s.favorite())
                        .writeBoolean(s.mine()).writeBoolean(s.hasCover()).writeInt(s.plays());
            }
            return w.toBytes();
        }

        static LibraryResults decode(ByteBuffer in) {
            int x = PacketIO.readInt(in), y = PacketIO.readInt(in), z = PacketIO.readInt(in);
            int page = PacketIO.readIntRange(in, 0, 100_000), pages = PacketIO.readIntRange(in, 1, 100_001);
            int total = PacketIO.readIntRange(in, 0, Integer.MAX_VALUE), blank = PacketIO.readIntRange(in, 0, 100_000);
            int count = PacketIO.readByte(in);
            if (count > Limits.LIBRARY_PAGE_SIZE) throw new MalformedPacketException("Seite zu lang");
            java.util.List<LibrarySong> songs = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                songs.add(new LibrarySong(PacketIO.readString(in, Limits.HASH_LENGTH), PacketIO.readString(in, Limits.MAX_TEXT),
                        PacketIO.readString(in, Limits.MAX_TEXT), PacketIO.readString(in, Limits.MAX_TEXT), PacketIO.readInt(in),
                        PacketIO.readBoolean(in), PacketIO.readBoolean(in), PacketIO.readBoolean(in), PacketIO.readIntRange(in, 0, Integer.MAX_VALUE)));
            }
            return new LibraryResults(x, y, z, page, pages, total, blank, java.util.List.copyOf(songs));
        }
    }

    /** Client -> Server: diesen Song auf eine leere Platte schreiben. */
    record LibraryWrite(int x, int y, int z, String hash) implements Serverbound {
        public static final int ID = 49;

        @Override
        public byte[] encode() { return new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).writeString(hash, Limits.HASH_LENGTH).toBytes(); }

        static LibraryWrite decode(ByteBuffer in) {
            return new LibraryWrite(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readString(in, Limits.HASH_LENGTH));
        }
    }

    // ---- Musik-Fernbedienung ----

    /** Server -> Client: Fernbedienungs-Menue oeffnen. {@code connected} = an ein Jukebox-Netz angeschlossen. */
    record OpenRemoteMenu(int x, int y, int z, RemoteSettings settings, boolean connected) implements Clientbound {
        public static final int ID = 50;

        @Override
        public byte[] encode() {
            PacketIO.Writer w = new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z);
            settings.write(w);
            return w.writeBoolean(connected).toBytes();
        }

        static OpenRemoteMenu decode(ByteBuffer in) {
            return new OpenRemoteMenu(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in), RemoteSettings.read(in),
                    PacketIO.readBoolean(in));
        }
    }

    /** Client -> Server: geaenderte Einstellungen der Fernbedienung. */
    record RemoteUpdate(int x, int y, int z, RemoteSettings settings) implements Serverbound {
        public static final int ID = 51;

        @Override
        public byte[] encode() {
            PacketIO.Writer w = new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z);
            settings.write(w);
            return w.toBytes();
        }

        static RemoteUpdate decode(ByteBuffer in) {
            return new RemoteUpdate(PacketIO.readInt(in), PacketIO.readInt(in), PacketIO.readInt(in), RemoteSettings.read(in));
        }
    }

    // ---- Jukebox-Inventar ----

    /**
     * Client -> Server: eine beschriebene Platte aus dem eigenen Inventar ins Jukebox-Inventar legen ({@code PUT}, Slot =
     * Inventar-Slot 0-35) oder aus dem Jukebox-Inventar nehmen ({@code TAKE}, Slot = Jukebox-Slot 0-14).
     */
    record JukeboxInvAction(int x, int y, int z, Kind kind, int slot) implements Serverbound {
        public static final int ID = 52;

        public enum Kind {
            PUT, TAKE;

            static Kind byOrdinal(int o) {
                Kind[] v = values();
                if (o < 0 || o >= v.length) throw new MalformedPacketException("Unbekannte Aktion");
                return v[o];
            }
        }

        @Override
        public byte[] encode() {
            return new PacketIO.Writer(ID).writeInt(x).writeInt(y).writeInt(z).writeByte(kind.ordinal()).writeByte(slot).toBytes();
        }

        static JukeboxInvAction decode(ByteBuffer in) {
            int x = PacketIO.readInt(in), y = PacketIO.readInt(in), z = PacketIO.readInt(in);
            Kind kind = Kind.byOrdinal(PacketIO.readByte(in));
            int slot = PacketIO.readByte(in);
            if (slot >= (kind == Kind.PUT ? 36 : PlaylistLogic.INVENTORY_SLOTS)) throw new MalformedPacketException("Ungueltiger Slot");
            return new JukeboxInvAction(x, y, z, kind, slot);
        }
    }
}