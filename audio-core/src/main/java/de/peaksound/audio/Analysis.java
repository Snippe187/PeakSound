package de.peaksound.audio;

import java.nio.ByteBuffer;

/**
 * Vorberechnete Musikanalyse eines Songs. Wird beim Upload einmal serverseitig erzeugt, damit alle Spieler
 * und alle Redstone-Signale exakt dasselbe sehen.
 *
 * @param hopMs  Zeitraster der Pegel-Arrays in Millisekunden (50 = 20 Werte pro Sekunde)
 * @param bass   Pegel 0-15 je Zeitschritt, tiefe Frequenzen (bis ca. 200 Hz)
 * @param mid    Pegel 0-15, mittlere Frequenzen (ca. 200 Hz bis 2,5 kHz)
 * @param high   Pegel 0-15, hohe Frequenzen (ab ca. 2,5 kHz)
 * @param beatsMs Zeitpunkte der erkannten Taktschlaege in Millisekunden, aufsteigend
 */
public record Analysis(int hopMs, byte[] bass, byte[] mid, byte[] high, int[] beatsMs) {
    private static final int MAGIC = 0x50534131; // "PSA1"

    public enum Band { BASS, MID, HIGH }

    /** Pegel 0-15 eines Bands zum Zeitpunkt {@code positionMs}; 0 ausserhalb des Songs. */
    public int level(Band band, long positionMs) {
        byte[] values = switch (band) {
            case BASS -> bass;
            case MID -> mid;
            case HIGH -> high;
        };
        if (positionMs < 0) return 0;
        long i = positionMs / hopMs;
        return i >= values.length ? 0 : values[(int) i];
    }

    /** Erster Taktschlag mit Zeit &gt; {@code afterMs}, oder -1. Binaere Suche, deshalb auch im Tick-Loop guenstig. */
    public int nextBeatAfter(long afterMs) {
        int lo = 0, hi = beatsMs.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (beatsMs[mid] <= afterMs) lo = mid + 1;
            else hi = mid;
        }
        return lo < beatsMs.length ? beatsMs[lo] : -1;
    }

    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(4 + 4 + 4 + bass.length * 3 + 4 + beatsMs.length * 4);
        buf.putInt(MAGIC).putInt(hopMs).putInt(bass.length);
        buf.put(bass).put(mid).put(high);
        buf.putInt(beatsMs.length);
        for (int b : beatsMs) buf.putInt(b);
        return buf.array();
    }

    public static Analysis fromBytes(byte[] data) throws AudioException {
        try {
            ByteBuffer buf = ByteBuffer.wrap(data);
            if (buf.getInt() != MAGIC) throw new AudioException("Unbekanntes Analyse-Format");
            int hop = buf.getInt();
            int n = buf.getInt();
            if (hop < 1 || n < 0 || n > 1_000_000) throw new AudioException("Analyse ungueltig");
            byte[] bass = new byte[n], mid = new byte[n], high = new byte[n];
            buf.get(bass).get(mid).get(high);
            int beats = buf.getInt();
            if (beats < 0 || beats > 1_000_000) throw new AudioException("Analyse ungueltig");
            int[] b = new int[beats];
            for (int i = 0; i < beats; i++) b[i] = buf.getInt();
            return new Analysis(hop, bass, mid, high, b);
        } catch (java.nio.BufferUnderflowException e) {
            throw new AudioException("Analyse abgeschnitten");
        }
    }
}
