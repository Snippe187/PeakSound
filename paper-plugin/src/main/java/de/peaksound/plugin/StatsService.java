package de.peaksound.plugin;

import de.peaksound.protocol.Limits;
import de.peaksound.protocol.PeakBlockType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Statistik fuer Operatoren: Songs, Speicher, beliebteste Songs, aktivste Hochlader und Bloecke. Nur Lesen, nichts wird veraendert. */
final class StatsService {
    private final SongStore songs;
    private final AdminLimits limits;
    private final BlockRegistry blocks;
    private final PlaybackManager playback;
    private final NetworkHandler network;

    StatsService(SongStore songs, AdminLimits limits, BlockRegistry blocks, PlaybackManager playback, NetworkHandler network) {
        this.songs = songs;
        this.limits = limits;
        this.blocks = blocks;
        this.playback = playback;
        this.network = network;
    }

    private static String mb(long bytes) { return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1048576.0); }

    private static String cut(String s, int max) { return s.length() > max ? s.substring(0, max - 1) + "." : s; }

    /** Mehrzeiliger Text, begrenzt auf {@link Limits#MAX_STATS_CHARS} (geht auch an den Admin-Tab der Mod). */
    String describe() {
        List<Song> all = songs.all();
        long max = limits.maxStorageBytes();
        long plays = 0;
        for (Song s : all) plays += s.plays();
        StringBuilder sb = new StringBuilder();
        sb.append("Songs: ").append(all.size()).append("  |  Speicher: ").append(mb(songs.totalBytes()))
                .append(max == 0 ? " (unbegrenzt)" : " von " + mb(max)).append('\n');
        sb.append("Abspielungen gesamt: ").append(plays).append('\n');

        sb.append("Beliebteste Songs:\n");
        List<Song> top = new ArrayList<>(all);
        top.sort(Comparator.comparingInt(Song::plays).reversed().thenComparing(Song::title));
        int shown = 0;
        for (Song s : top) {
            if (s.plays() == 0 || shown >= 5) break;
            sb.append("  ").append(++shown).append(". ").append(cut(s.title(), 28)).append(" - ").append(s.plays()).append("x\n");
        }
        if (shown == 0) sb.append("  (noch nichts gespielt)\n");

        sb.append("Hochlader:\n");
        Map<String, long[]> byUploader = new HashMap<>(); // Name -> {Songs, Bytes}
        for (Song s : all) {
            long[] v = byUploader.computeIfAbsent(s.uploaderName(), k -> new long[2]);
            v[0]++;
            v[1] += s.sizeBytes();
        }
        List<Map.Entry<String, long[]>> uploaders = new ArrayList<>(byUploader.entrySet());
        uploaders.sort((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]));
        for (int i = 0; i < Math.min(5, uploaders.size()); i++) {
            var e = uploaders.get(i);
            sb.append("  ").append(cut(e.getKey(), 20)).append(": ").append(e.getValue()[0]).append(" Songs, ").append(mb(e.getValue()[1])).append('\n');
        }
        if (uploaders.isEmpty()) sb.append("  (keine Songs)\n");

        int jukeboxes = blocks.keysOf(PeakBlockType.JUKEBOX).size();
        long playing = blocks.keysOf(PeakBlockType.JUKEBOX).stream().filter(k -> playback.isPlayingKey(k)).count();
        sb.append("Bloecke: ").append(jukeboxes).append(" Jukeboxen (").append(playing).append(" spielen), ")
                .append(blocks.keysOf(PeakBlockType.SPEAKER).size()).append(" Lautsprecher, ")
                .append(blocks.keysOf(PeakBlockType.RADIO_TRANSMITTER).size()).append(" Sender, ")
                .append(blocks.keysOf(PeakBlockType.RADIO_RECEIVER).size()).append(" Radios, ")
                .append(blocks.keysOf(PeakBlockType.SONG_LIBRARY).size()).append(" Bibliotheken\n");
        int withMod = 0;
        for (Player p : Bukkit.getOnlinePlayers()) if (network.hasMod(p)) withMod++;
        sb.append("Spieler online: ").append(Bukkit.getOnlinePlayers().size()).append(" (").append(withMod).append(" mit Mod)\n");
        String text = sb.toString();
        return text.length() > Limits.MAX_STATS_CHARS ? text.substring(0, Limits.MAX_STATS_CHARS) : text;
    }
}