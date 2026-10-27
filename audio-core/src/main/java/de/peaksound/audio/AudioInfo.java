package de.peaksound.audio;

import de.peaksound.protocol.AudioFormat;

/**
 * Eigenschaften einer Audiodatei. {@code cover} ist leer (nicht null), wenn kein Bild eingebettet ist;
 * {@code title} und {@code artist} sind leer, wenn die Tags fehlen.
 */
public record AudioInfo(AudioFormat format, int durationMs, int channels, int sampleRate, int bitsPerSample,
                        String title, String artist, byte[] cover, String lyrics) {
    /** Ohne Songtext. */
    public AudioInfo(AudioFormat format, int durationMs, int channels, int sampleRate, int bitsPerSample,
                     String title, String artist, byte[] cover) {
        this(format, durationMs, channels, sampleRate, bitsPerSample, title, artist, cover, "");
    }

    /** Mehrkanal (mehr als Stereo) muss vom Server-Owner freigegeben werden. */
    public boolean multichannel() { return channels > 2; }
}
