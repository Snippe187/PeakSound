package de.peaksound.mod.client;

import de.peaksound.protocol.Limits;
import de.peaksound.protocol.Packet;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Song-Cache und Download. Jeder Song hat einen Hash als Kennung; was schon in
 * {@code .minecraft/peaksound/cache} liegt, wird nie erneut geladen. Beim Download laeuft die Wiedergabe schon
 * mit, sobald die ersten Chunks da sind ({@link #openStream}). Die fertigen Daten werden gegen den Hash geprueft,
 * bevor sie im Cache landen.
 */
public final class SongLibrary {
    /** Anfragen, die gleichzeitig unterwegs sind. */
    private static final int WINDOW = 6;
    private static final long STALL_TIMEOUT_MS = 20_000;

    private static final class Download {
        final String hash;
        /** Wer die kompletten Bytes am Ende haben will (z. B. Vorhoeren). */
        final List<Consumer<byte[]>> onReady = new ArrayList<>();
        /** Wer den Song waehrend des Ladens als Strom liest (Wiedergabe). */
        final List<SongStream> streams = new ArrayList<>();
        byte[][] chunks;
        int total = -1;
        int nextToRequest;
        int received;
        /** Naechster Chunk, der an die Strome ausgeliefert wird (in Reihenfolge, ohne Luecken). */
        int delivered;
        long lastProgress = System.currentTimeMillis();

        Download(String hash) { this.hash = hash; }
    }

    private static final Map<String, Download> downloads = new HashMap<>();

    private SongLibrary() {}

    private static Path cacheDir() {
        return FabricLoader.getInstance().getGameDir().resolve("peaksound").resolve("cache");
    }

    private static Path file(String hash) { return cacheDir().resolve(hash + ".bin"); }

    /** Cache-Datei lesen und pruefen; eine kaputte Datei wird geloescht. */
    private static byte[] readCached(String hash) {
        Path cached = file(hash);
        if (!Files.exists(cached)) return null;
        try {
            byte[] data = Files.readAllBytes(cached);
            if (sha256(data).equals(hash)) return data;
            Files.deleteIfExists(cached); // beschaedigt: neu laden
        } catch (IOException ignored) { /* naechster Versuch laedt neu */ }
        return null;
    }

    /**
     * Liefert die kompletten Songdaten (aus dem Cache oder per Download). {@code onReady} laeuft auf dem Haupt-Thread.
     * Schlaegt es fehl, wird nichts aufgerufen.
     */
    public static void request(String hash, Consumer<byte[]> onReady) {
        Thread.ofVirtual().start(() -> {
            byte[] data = readCached(hash);
            Minecraft.getInstance().execute(() -> {
                if (data != null) {
                    onReady.accept(data);
                    return;
                }
                download(hash).onReady.add(onReady);
            });
        });
    }

    /**
     * Liefert einen Strom ueber den Song. Liegt er im Cache, ist er sofort komplett; sonst fuellt er sich waehrend des
     * Downloads, und der Leser blockiert nur dann, wenn er schneller ist als das Netz. {@code onReady} laeuft in einem
     * Hintergrund-Thread.
     */
    static void openStream(String hash, Consumer<InputStream> onReady) {
        Thread.ofVirtual().start(() -> {
            byte[] data = readCached(hash);
            if (data != null) {
                onReady.accept(new ByteArrayInputStream(data));
                return;
            }
            Minecraft.getInstance().execute(() -> {
                Download d = download(hash);
                SongStream stream = new SongStream();
                // Spaet hinzukommende Leser bekommen zuerst, was schon angekommen ist
                for (int i = 0; i < d.delivered; i++) stream.append(d.chunks[i]);
                if (d.delivered > 0 && d.delivered == d.total) stream.finish();
                d.streams.add(stream);
                Thread.ofVirtual().start(() -> onReady.accept(stream));
            });
        });
    }

    /** Download starten, falls noch keiner laeuft. Nur auf dem Haupt-Thread. */
    private static Download download(String hash) {
        Download existing = downloads.get(hash);
        if (existing != null) return existing;
        Download d = new Download(hash);
        downloads.put(hash, d);
        ClientNetwork.send(new Packet.SongRequest(hash, 0));
        d.nextToRequest = 1;
        return d;
    }

    static void onChunk(Packet.SongChunk chunk) {
        Download d = downloads.get(chunk.hash());
        if (d == null) return;
        if (d.total < 0) {
            // Erste Antwort: Gesamtzahl pruefen und die Pipeline fuellen
            int max = Limits.chunkCount(Limits.MAX_UPLOAD_BYTES, Limits.DOWNLOAD_CHUNK_BYTES);
            if (chunk.total() < 1 || chunk.total() > max) {
                fail(d);
                return;
            }
            d.total = chunk.total();
            d.chunks = new byte[d.total][];
        }
        if (chunk.total() != d.total || chunk.index() >= d.total || d.chunks[chunk.index()] != null) return;
        d.chunks[chunk.index()] = chunk.data();
        d.received++;
        d.lastProgress = System.currentTimeMillis();
        while (d.nextToRequest < d.total && d.nextToRequest - d.received < WINDOW) {
            ClientNetwork.send(new Packet.SongRequest(d.hash, d.nextToRequest++));
        }
        // Alles Lueckenlose sofort an die Wiedergabe weiterreichen
        while (d.delivered < d.total && d.chunks[d.delivered] != null) {
            byte[] part = d.chunks[d.delivered++];
            for (SongStream s : d.streams) s.append(part);
        }
        if (d.received == d.total) {
            downloads.remove(d.hash);
            finish(d);
        }
    }

    static void onUnavailable(String hash) {
        Download d = downloads.get(hash);
        if (d != null) fail(d);
    }

    /** Bricht haengende Downloads ab (alle Sekunde pruefen). */
    static void tick() {
        long now = System.currentTimeMillis();
        List<Download> stalled = new ArrayList<>();
        for (Download d : downloads.values()) if (now - d.lastProgress > STALL_TIMEOUT_MS) stalled.add(d);
        stalled.forEach(SongLibrary::fail);
    }

    /** Diagnose: Cache-Ordner, Anzahl und Groesse der Dateien, laufende Downloads. */
    static String debug() {
        StringBuilder sb = new StringBuilder();
        Path dir = cacheDir();
        long bytes = 0;
        int files = 0;
        try (var list = java.nio.file.Files.list(dir)) {
            for (Path p : (Iterable<Path>) list::iterator) {
                files++;
                bytes += java.nio.file.Files.size(p);
            }
        } catch (java.io.IOException | RuntimeException e) {
            sb.append("  Cache-Ordner nicht lesbar: ").append(e).append('\n');
        }
        sb.append("  Cache-Ordner: ").append(dir).append(" (").append(files).append(" Dateien, ").append(bytes / 1024).append(" KiB)\n");
        sb.append("  Laufende Downloads: ").append(downloads.size());
        for (String h : downloads.keySet()) sb.append(' ').append(h, 0, Math.min(8, h.length()));
        return sb.append('\n').toString();
    }

    static void clear() {
        new ArrayList<>(downloads.values()).forEach(SongLibrary::fail);
    }

    private static void fail(Download d) {
        downloads.remove(d.hash);
        d.streams.forEach(SongStream::fail);
    }

    private static void finish(Download d) {
        int size = 0;
        for (byte[] c : d.chunks) size += c.length;
        byte[] all = new byte[size];
        int pos = 0;
        for (byte[] c : d.chunks) {
            System.arraycopy(c, 0, all, pos, c.length);
            pos += c.length;
        }
        d.streams.forEach(SongStream::finish);
        if (!sha256(all).equals(d.hash)) return; // beschaedigt: nicht zwischenspeichern, Vorhoeren nicht bedienen
        Thread.ofVirtual().start(() -> {
            try {
                Files.createDirectories(cacheDir());
                Path tmp = cacheDir().resolve(d.hash + ".tmp");
                Files.write(tmp, all);
                Files.move(tmp, file(d.hash), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) { /* Cache ist nur eine Optimierung */ }
        });
        for (Consumer<byte[]> callback : d.onReady) callback.accept(all);
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
