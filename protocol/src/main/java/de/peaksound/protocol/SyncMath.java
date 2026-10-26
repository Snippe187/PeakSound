package de.peaksound.protocol;

import java.util.Collection;
import java.util.Optional;

/**
 * Rechnungen fuer die synchrone Wiedergabe. Alle Spieler und alle Jukeboxen eines Netzes leiten ihre
 * Songposition aus derselben Serveruhr ab, deshalb hoeren alle dasselbe, auch wer spaeter dazukommt.
 */
public final class SyncMath {
    private SyncMath() {}

    /**
     * Wie weit liegt die Uhr des Servers vor der Uhr des Clients? Der Server hat seine Zeit etwa in der
     * Mitte der Rundreise gestempelt (Hello gesendet -> Antwort empfangen).
     */
    public static long clockOffsetMs(long helloSentClientMs, long ackReceivedClientMs, long serverTimeMs) {
        long midpoint = helloSentClientMs > 0 ? (helloSentClientMs + ackReceivedClientMs) / 2 : ackReceivedClientMs;
        return serverTimeMs - midpoint;
    }

    /** Songposition in Millisekunden zur (geschaetzten) Serverzeit; nie negativ. */
    public static long positionMs(long serverNowMs, long startedAtServerMs) {
        return Math.max(0, serverNowMs - startedAtServerMs);
    }

    /** Beginn des Songs auf der Serveruhr aus den Angaben eines {@code PlaybackStart}-Pakets. */
    public static long startedAt(long packetServerTimeMs, int offsetMs) {
        return packetServerTimeMs - offsetMs;
    }

    /** Frame-Index im Song fuer eine Position (Samples pro Sekunde = {@code sampleRate}). */
    public static long frameAt(long positionMs, int sampleRate) {
        return positionMs * sampleRate / 1000;
    }

    /** Ein Song im Netz, der gerade spielt: Wer ihn gestartet hat (Schluessel) und seit wann. */
    public record Candidate<K>(K key, long startedAtMs) {}

    /**
     * Wer bestimmt, was ein Netz spielt? Der Song, der am laengsten laeuft. Er bleibt "Anfuehrer", bis er endet;
     * erst dann uebernimmt der naechste. So wird ein laufender Song nie von einem neu eingelegten verdraengt.
     * Bei gleichem Start gewinnt der kleinere Schluessel in der Reihenfolge der Sammlung (stabil).
     */
    public static <K> Optional<Candidate<K>> pickLeader(Collection<Candidate<K>> playing) {
        Candidate<K> best = null;
        for (Candidate<K> c : playing) {
            if (best == null || c.startedAtMs() < best.startedAtMs()) best = c;
        }
        return Optional.ofNullable(best);
    }
}
