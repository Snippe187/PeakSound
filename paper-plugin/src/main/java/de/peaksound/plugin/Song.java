package de.peaksound.plugin;

import java.util.UUID;

/**
 * Ein gespeicherter Song. {@code hash} ist der SHA-256 der gespeicherten Datei und zugleich die
 * Kennung (Dateiname, Client-Cache, Netzwerk). {@code PENDING} = Mehrkanal, wartet auf Freigabe.
 * {@code plays} zaehlt, wie oft der Song gestartet oder auf eine Platte geschrieben wurde (Beliebtheit).
 */
public record Song(String hash, String title, String artist, UUID uploader, String uploaderName, int channels,
                   int durationMs, int sizeBytes, String format, Status status, long uploadedAt, boolean hasCover, int plays) {
    public enum Status { OK, PENDING }

    /** Neuer Song ohne bisherige Wiedergaben. */
    public Song(String hash, String title, String artist, UUID uploader, String uploaderName, int channels,
                int durationMs, int sizeBytes, String format, Status status, long uploadedAt, boolean hasCover) {
        this(hash, title, artist, uploader, uploaderName, channels, durationMs, sizeBytes, format, status, uploadedAt, hasCover, 0);
    }

    public Song withStatus(Status s) {
        return new Song(hash, title, artist, uploader, uploaderName, channels, durationMs, sizeBytes, format, s, uploadedAt, hasCover, plays);
    }

    public Song withTitle(String t) {
        return new Song(hash, t, artist, uploader, uploaderName, channels, durationMs, sizeBytes, format, status, uploadedAt, hasCover, plays);
    }

    public Song withPlays(int p) {
        return new Song(hash, title, artist, uploader, uploaderName, channels, durationMs, sizeBytes, format, status, uploadedAt, hasCover, p);
    }
}
