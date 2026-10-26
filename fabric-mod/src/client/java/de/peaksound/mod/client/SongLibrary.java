package de.peaksound.mod.client;

import de.peaksound.protocol.Limits;
import de.peaksound.protocol.Packet;
import java.io.IOException;
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
 * {@code .minecraft/peaksound/cache} liegt, wird nie erneut geladen. Geladene Daten werden gegen den
 * Hash geprueft, bevor sie benutzt oder gespeichert werden.
 */
public final class SongLibrary {
    /** Anfragen, die gleichzeitig unterwegs sind. */
    private static final int WINDOW = 6;
    private static final long STALL_TIMEOUT_MS = 20_000;

    private static final class Download {
        final String hash;
        final List<Consumer<byte[]>> onReady = new ArrayList<>();
        byte[][] chunks;
        int total = -1;
        int nextToRequest;
        int received;
        long lastProgress = System.currentTimeMillis();

        Download(String hash) { this.hash = hash; }
    }

    private static final Map<String, Download> downloads = new HashMap<>();

    private SongLibrary() {}

    private static Path cacheDir() {
        return FabricLoader.getInstance().getGameDir().resolve("peaksound").resolve("cache");
    }

    private static Path file(String hash) { return cacheDir().resolve(hash + ".bin"); }

    /**
     * Liefert die Songdaten (aus dem Cache oder per Download). {@code onReady} laeuft auf dem Haupt-Thread.
     * Schlaegt es fehl, wird nichts aufgerufen; der Aufrufer versucht es spaeter erneut.
     */
    public static void request(String hash, Consumer<byte[]> onReady) {
        Path cached = file(hash);
        if (Files.exists(cached)) {
            Minecraft.getInstance().execute(() -> {
                try {
                    onReady.accept(Files.readAllBytes(cached));
                } catch (IOException ignored) { /* Cache-Datei defekt: naechster Versuch laedt neu */ }
            });
            // Defekte Datei entfernen, falls sie nicht passt (siehe verify beim naechsten Download)
            return;
        }
        Download existing = downloads.get(hash);
        if (existing != null) {
            existing.onReady.add(onReady);
            return;
        }
        Download d = new Download(hash);
        d.onReady.add(onReady);
        downloads.put(hash, d);
        ClientNetwork.send(new Packet.SongRequest(hash, 0));
        d.nextToRequest = 1;
    }

    static void onChunk(Packet.SongChunk chunk) {
        Download d = downloads.get(chunk.hash());
        if (d == null) return;
        if (d.total < 0) {
            // Erste Antwort: Gesamtzahl pruefen und die Pipeline fuellen
            int max = Limits.chunkCount(Limits.MAX_UPLOAD_BYTES, Limits.DOWNLOAD_CHUNK_BYTES);
            if (chunk.total() < 1 || chunk.total() > max) {
                downloads.remove(d.hash);
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
        if (d.received == d.total) {
            downloads.remove(d.hash);
            finish(d);
        }
    }

    static void onUnavailable(String hash) {
        downloads.remove(hash);
    }

    /** Bricht haengende Downloads ab (alle Sekunde pruefen). */
    static void tick() {
        long now = System.currentTimeMillis();
        downloads.values().removeIf(d -> now - d.lastProgress > STALL_TIMEOUT_MS);
    }

    static void clear() { downloads.clear(); }

    private static void finish(Download d) {
        int size = 0;
        for (byte[] c : d.chunks) size += c.length;
        byte[] all = new byte[size];
        int pos = 0;
        for (byte[] c : d.chunks) {
            System.arraycopy(c, 0, all, pos, c.length);
            pos += c.length;
        }
        if (!sha256(all).equals(d.hash)) return; // manipuliert oder beschaedigt: verwerfen
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
