package de.peaksound.plugin;

import java.util.UUID;

/**
 * Ein gespeicherter Song. {@code hash} ist der SHA-256 der gespeicherten Datei und zugleich die
 * Kennung (Dateiname, Client-Cache, Netzwerk). {@code PENDING} = Mehrkanal, wartet auf Freigabe.
 */
public record Song(String hash, String title, String artist, UUID uploader, String uploaderName, int channels,
                   int durationMs, int sizeBytes, String format, Status status, long uploadedAt, boolean hasCover) {
    public enum Status { OK, PENDING }
}
