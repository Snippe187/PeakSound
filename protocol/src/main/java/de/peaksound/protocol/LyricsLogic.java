package de.peaksound.protocol;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Songtexte fuer die Anzeige. Enthalten die Tags Zeitstempel im LRC-Format ({@code [mm:ss.xx]}), laeuft der Text
 * zeitgenau mit. Sonst (die meisten MP3-Tags) gibt es keine Zeitangaben: Dann verteilen wir die Zeilen gleichmaessig
 * ueber den Song, das ist nur eine Naeherung und wird in der Anzeige auch so gekennzeichnet.
 */
public final class LyricsLogic {
    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    private LyricsLogic() {}

    /** Eine Zeile: Beginn in Millisekunden ab Songstart. */
    public record Line(int startMs, String text) {}

    /** Die geparsten Zeilen; {@code synced} = mit echten Zeitstempeln. */
    public record Lyrics(boolean synced, List<Line> lines) {
        public boolean isEmpty() { return lines.isEmpty(); }
    }

    public static Lyrics parse(String text, int durationMs) {
        if (text == null || text.isBlank()) return new Lyrics(false, List.of());
        List<Line> timed = new ArrayList<>();
        List<String> plain = new ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = raw.strip();
            Matcher m = STAMP.matcher(line);
            List<Integer> stamps = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) { // nur Zeitstempel am Zeilenanfang
                stamps.add(toMs(m.group(1), m.group(2), m.group(3)));
                end = m.end();
            }
            String body = line.substring(end).strip();
            if (!stamps.isEmpty()) {
                for (int s : stamps) timed.add(new Line(s, body));
            } else if (!line.isEmpty() && !line.startsWith("[")) { // [ar:..]-Kopfzeilen ueberspringen
                plain.add(line);
            }
        }
        if (timed.size() >= 2) {
            timed.sort(Comparator.comparingInt(Line::startMs));
            return new Lyrics(true, List.copyOf(timed));
        }
        List<String> lines = plain.isEmpty() ? timed.stream().map(Line::text).toList() : plain;
        if (lines.isEmpty()) return new Lyrics(false, List.of());
        // gleichmaessig zwischen 5 % und 95 % des Songs verteilen
        int from = durationMs / 20, span = Math.max(1, durationMs * 9 / 10);
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) out.add(new Line(from + (int) ((long) span * i / lines.size()), lines.get(i)));
        return new Lyrics(false, List.copyOf(out));
    }

    private static int toMs(String min, String sec, String frac) {
        int ms = 0;
        if (frac != null) {
            ms = Integer.parseInt(frac);
            if (frac.length() == 1) ms *= 100;
            else if (frac.length() == 2) ms *= 10;
        }
        return (Integer.parseInt(min) * 60 + Integer.parseInt(sec)) * 1000 + ms;
    }

    /** Index der Zeile, die bei {@code positionMs} gerade dran ist; -1 vor der ersten Zeile. */
    public static int lineAt(List<Line> lines, long positionMs) {
        int index = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startMs() <= positionMs) index = i;
            else break;
        }
        return index;
    }
}
