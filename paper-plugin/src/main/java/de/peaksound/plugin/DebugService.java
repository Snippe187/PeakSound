package de.peaksound.plugin;

import de.peaksound.protocol.Limits;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PeakBlockType;
import de.peaksound.protocol.PeakIds;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Diagnosetext fuer {@code /peaksound debug}: Der Client fordert ihn an, der Server beschreibt sich selbst, den
 * anfragenden Spieler und den Zustand des Plugins. Alles ist nur lesend. Serverlog-Auszug und Dateiliste gibt es nur
 * fuer Operatoren; jeder andere bekommt nur Angaben zu sich selbst.
 */
final class DebugService {
    private static final long MIN_INTERVAL_MS = 2000;
    private static final Pattern LOG_NOISE = Pattern.compile("(?i)oshi|Perflib|CrashReport preload|Loaded library|Loading \\d+ libraries|No key layers");
    private static final Pattern LOG_FILTER = Pattern.compile("(?i)peaksound|voicechat|exception|\\bERROR\\b|\\bSEVERE\\b");

    private final PeakSoundPlugin plugin;
    private final BlockRegistry blocks;
    private final SongStore songs;
    private final PlaybackManager playback;
    private final NetworkHandler network;
    private final EqualizerService equalizer;
    private final MicService mic;
    private final Map<UUID, Long> last = new ConcurrentHashMap<>();

    DebugService(PeakSoundPlugin plugin, BlockRegistry blocks, SongStore songs, PlaybackManager playback,
                 NetworkHandler network, EqualizerService equalizer, MicService mic) {
        this.plugin = plugin;
        this.blocks = blocks;
        this.songs = songs;
        this.playback = playback;
        this.network = network;
        this.equalizer = equalizer;
        this.mic = mic;
    }

    void onQuit(Player player) {
        last.remove(player.getUniqueId());
    }

    void onRequest(Player player) {
        long now = System.currentTimeMillis();
        Long before = last.put(player.getUniqueId(), now);
        if (before != null && now - before < MIN_INTERVAL_MS) return;
        String text;
        try {
            text = build(player);
        } catch (RuntimeException e) {
            text = "Fehler beim Erstellen des Server-Berichts: " + e;
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > Limits.MAX_DEBUG_BYTES) {
            // am Zeichenrand kuerzen: zuerst grob, dann bis es passt
            String cut = text.substring(0, Math.min(text.length(), Limits.MAX_DEBUG_BYTES / 2));
            while ((cut + "\n... (gekuerzt)").getBytes(StandardCharsets.UTF_8).length > Limits.MAX_DEBUG_BYTES) {
                cut = cut.substring(0, cut.length() - 500);
            }
            text = cut + "\n... (gekuerzt)";
        }
        Net.send(plugin, player, new Packet.DebugInfo(text));
    }

    /** Fuer die Konsole (/peaksound debugdump): derselbe Bericht ohne Spieler-Abschnitt, als Operator-Sicht. */
    String dump() {
        return build(null);
    }

    private String build(Player player) {
        boolean admin = player == null || Permissions.admin(player);
        StringBuilder sb = new StringBuilder(8192);
        line(sb, "Zeit (Server)", Instant.now().toString());
        sb.append("\n[Plugin]\n");
        line(sb, "PeakSound-Plugin", plugin.getPluginMeta().getVersion() + ", Protokoll " + PeakIds.PROTOCOL_VERSION);
        line(sb, "Aktiviert", String.valueOf(plugin.isEnabled()));
        line(sb, "Server", Bukkit.getName() + " " + Bukkit.getVersion());
        line(sb, "Minecraft", Bukkit.getMinecraftVersion() + " (Bukkit " + Bukkit.getBukkitVersion() + ")");
        line(sb, "Java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        line(sb, "System", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
        line(sb, "online-mode", String.valueOf(Bukkit.getOnlineMode()));
        line(sb, "Spieler online", Bukkit.getOnlinePlayers().size() + " / " + Bukkit.getMaxPlayers());
        line(sb, "Comparator-Equalizer", equalizer.available() ? "bereit" : "ausgeschaltet");
        line(sb, "Simple Voice Chat", Bukkit.getPluginManager().getPlugin("voicechat") != null
                ? "Plugin vorhanden, Anbindung aktiv: " + mic.voiceChatAvailable() : "nicht installiert");

        if (player != null) {
        sb.append("\n[Du (der anfragende Spieler)]\n");
        line(sb, "Name / UUID", player.getName() + " / " + player.getUniqueId());
        line(sb, "Operator (Bukkit isOp)", String.valueOf(player.isOp()));
        line(sb, "Recht peaksound.admin", String.valueOf(player.hasPermission("peaksound.admin")));
        line(sb, "Admin laut Plugin", String.valueOf(admin));
        line(sb, "Handshake erfolgreich (hasMod)", String.valueOf(network.hasMod(player)));
        sb.append(network.describe(player));
        line(sb, "Client-Brand", String.valueOf(player.getClientBrandName()));
        line(sb, "Sprache", String.valueOf(player.locale()));
        line(sb, "Ping", player.getPing() + " ms");
        line(sb, "Welt / Position", player.getWorld().getName() + " " + player.getLocation().getBlockX() + " "
                + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ());
        line(sb, "Gamemode", String.valueOf(player.getGameMode()));
        line(sb, "Plugin-Kanaele des Clients", String.valueOf(new java.util.TreeSet<>(player.getListeningPluginChannels())));
        line(sb, "Kanal " + PeakIds.CHANNEL + " registriert", String.valueOf(player.getListeningPluginChannels().contains(PeakIds.CHANNEL)));
        }

        sb.append("\n[Zustand]\n");
        for (PeakBlockType type : PeakBlockType.values()) line(sb, "Bloecke " + type.id(), String.valueOf(blocks.keysOf(type).size()));
        var effective = playback.effective();
        line(sb, "Spielende Jukeboxen / Schallquellen", effective.jukeboxes().size() + " / " + effective.emitters().size());
        line(sb, "Songs", songs.count() + " (" + songs.totalBytes() / 1024 + " KiB)");
        List<String> modded = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (network.hasMod(p)) modded.add(p.getName());
        line(sb, "Spieler mit PeakSound-Mod", modded.size() + " " + modded);

        sb.append("\n[Config]\n").append(plugin.getConfig().saveToString()).append('\n');

        if (admin) {
            sb.append("[Installierte Plugins]\n");
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                sb.append("  ").append(p.getPluginMeta().getName()).append(' ').append(p.getPluginMeta().getVersion())
                        .append(p.isEnabled() ? "" : " (AUS)").append('\n');
            }
            sb.append("\n[Plugin-Datenordner]\n");
            try (var files = Files.list(plugin.getDataFolder().toPath())) {
                files.sorted().forEach(f -> sb.append("  ").append(f.getFileName()).append("  ")
                        .append(Files.isDirectory(f) ? "(Ordner)" : size(f)).append('\n'));
            } catch (IOException e) {
                sb.append("  nicht lesbar: ").append(e).append('\n');
            }
            sb.append("\n[Serverlog-Auszug (PeakSound, Voice Chat, Fehler; neueste zuletzt)]\n");
            for (String l : logTail()) sb.append("  ").append(l).append('\n');
        } else {
            sb.append("(Plugin-Liste, Dateien und Serverlog nur fuer Operatoren)\n");
        }
        return sb.toString();
    }

    private static void line(StringBuilder sb, String key, String value) {
        sb.append("  ").append(key).append(": ").append(value).append('\n');
    }

    private static String size(Path f) {
        try {
            return Files.size(f) + " B";
        } catch (IOException e) {
            return "?";
        }
    }

    /** Die letzten passenden Zeilen aus logs/latest.log (liest nur das Ende der Datei). */
    private List<String> logTail() {
        Path log = Path.of("logs", "latest.log");
        List<String> out = new ArrayList<>();
        if (!Files.exists(log)) {
            out.add("logs/latest.log nicht gefunden");
            return out;
        }
        try (RandomAccessFile f = new RandomAccessFile(log.toFile(), "r")) {
            long start = Math.max(0, f.length() - 400_000);
            f.seek(start);
            byte[] data = new byte[(int) (f.length() - start)];
            f.readFully(data);
            for (String l : new String(data, StandardCharsets.UTF_8).split("\\R")) {
                if (LOG_FILTER.matcher(l).find() && !LOG_NOISE.matcher(l).find()) out.add(l.length() > 240 ? l.substring(0, 240) + "..." : l);
            }
        } catch (IOException | RuntimeException e) {
            out.add("Log nicht lesbar: " + e);
        }
        return out.size() > 60 ? out.subList(out.size() - 60, out.size()) : out;
    }
}