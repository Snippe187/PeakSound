package de.peaksound.plugin;

import org.bukkit.plugin.java.JavaPlugin;
import de.peaksound.audio.Analysis;
import de.peaksound.protocol.PeakBlockType;
import de.peaksound.protocol.PortFunction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Comparator;

/**
 * Comparator-Equalizer: Ein Comparator, dessen Eingang eine PeakSound-Jukebox beruehrt, gibt live den Pegel
 * der Musik aus (0-15). Jede Seite der Jukebox gehoert zu einem anderen Frequenzband:
 * <pre>
 *   Norden = Bass, Osten = Mitten, Sueden = Hoehen, Westen = Gesamtpegel (lautestes Band)
 * </pre>
 * Der Comparator wird mit dem Eingang zur Jukebox gesetzt (wie immer in Vanilla: Rueckseite an die Jukebox).
 * Die Pegel stammen aus der beim Upload berechneten Analyse und werden aus der Serveruhr abgelesen. Alle
 * Spieler und alle Redstone-Schaltungen sehen deshalb exakt dasselbe, und im Tick-Loop wird nichts berechnet.
 */
final class EqualizerService {
    /** Seiten der Jukebox und ihr Band. {@code null} steht fuer den Gesamtpegel. */
    private static final BlockFace[] FACES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final JavaPlugin plugin;
    private final BlockRegistry registry;
    private final AnalysisStore analysis;
    private final ComparatorDriver driver; // null = Equalizer aus
    /** Zuletzt gesetzter Pegel je Comparator, damit nur Aenderungen etwas kosten. */
    private final Map<BlockRegistry.Key, Integer> lastLevel = new HashMap<>();

    private final JukeboxSettingsStore settings;

    EqualizerService(JavaPlugin plugin, BlockRegistry registry, AnalysisStore analysis, ComparatorDriver driver,
                     JukeboxSettingsStore settings) {
        this.settings = settings;
        this.plugin = plugin;
        this.registry = registry;
        this.analysis = analysis;
        this.driver = driver;
    }

    boolean available() { return driver != null; }

    /**
     * Ist dieser Block ein Equalizer-Comparator? Ja, wenn sein Eingang eine PeakSound-Jukebox beruehrt.
     * Solche Comparators duerfen nicht von Vanilla neu berechnet werden.
     *
     * <p>Achtung: Der Blockstate {@code facing} eines Comparators zeigt zum EINGANG hin (der Ausgang liegt auf der
     * Gegenseite), genau wie bei Repeatern.
     */
    boolean isPort(Block block) {
        if (driver == null || block.getType() != Material.COMPARATOR) return false;
        BlockFace inputSide = ((Comparator) block.getBlockData()).getFacing();
        Block input = block.getRelative(inputSide);
        return input.getType() == Material.NOTE_BLOCK
                && registry.get(input).filter(t -> t == PeakBlockType.JUKEBOX).isPresent();
    }

    /** Pegel eines Ausgangs zur Songposition. {@code a} ist die Analyse des laufenden Songs (leer: nichts spielt oder noch nicht berechnet). */
    static int levelFor(PortFunction function, Optional<Analysis> a, boolean playing, long positionMs, long msSinceSongEnd) {
        return switch (function) {
            case BASS -> a.map(x -> x.level(Analysis.Band.BASS, positionMs)).orElse(0);
            case MID -> a.map(x -> x.level(Analysis.Band.MID, positionMs)).orElse(0);
            case HIGH -> a.map(x -> x.level(Analysis.Band.HIGH, positionMs)).orElse(0);
            case OVERALL -> a.map(x -> Math.max(x.level(Analysis.Band.BASS, positionMs),
                    Math.max(x.level(Analysis.Band.MID, positionMs), x.level(Analysis.Band.HIGH, positionMs)))).orElse(0);
            case RUNNING -> playing ? 15 : 0;
            case SONG_END -> msSinceSongEnd >= 0 && msSinceSongEnd < SONG_END_PULSE_MS ? 15 : 0;
            case BEAT -> a.map(x -> {
                int beat = x.nextBeatAfter(positionMs - BEAT_PULSE_MS);
                return beat >= 0 && beat <= positionMs ? 15 : 0;
            }).orElse(0);
            case OFF, ITEM_IN, ITEM_OUT -> 0;
        };
    }

    private static final long SONG_END_PULSE_MS = 400;
    private static final long BEAT_PULSE_MS = 150;

    /**
     * Alle 2 Ticks: Pegel aller Equalizer-Comparators aktualisieren. Jede der vier Seiten einer Jukebox gibt die Funktion
     * aus, die im Seitenmenue der Jukebox gewaehlt wurde (Bass, Mitten, Hoehen, gesamt, laeuft, Song-Ende, Takt).
     *
     * @param effective Jukebox -> Song, der dort gerade zu hoeren ist (inklusive Netz-Mitglieder)
     * @param lastEnded wann an einer Jukebox zuletzt ein Song endete (ms, 0 = nie)
     */
    void update(Map<BlockRegistry.Key, PlaybackManager.State> effective, long now,
                java.util.function.ToLongFunction<BlockRegistry.Key> lastEnded) {
        if (driver == null) return;
        Set<BlockRegistry.Key> active = new HashSet<>();
        for (BlockRegistry.Key key : registry.keysOf(PeakBlockType.JUKEBOX)) {
            World world = Bukkit.getWorld(key.world());
            if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) continue;
            Block jukebox = world.getBlockAt(key.x(), key.y(), key.z());
            PlaybackManager.State state = effective.get(key);
            Optional<Analysis> a = state == null ? Optional.empty() : Ctx.songs().get(state.hash).flatMap(analysis::peek);
            long position = state == null ? 0 : now - state.startedAt;
            long ended = lastEnded.applyAsLong(key);
            long sinceEnd = ended == 0 ? -1 : now - ended;
            java.util.List<PortFunction> ports = settings.get(key).ports();
            for (int i = 0; i < FACES.length; i++) {
                BlockFace face = FACES[i];
                Block port = jukebox.getRelative(face);
                // Der Comparator liegt auf der Seite "face" der Jukebox; sein Eingang zeigt zurueck zur Jukebox
                if (port.getType() != Material.COMPARATOR
                        || ((Comparator) port.getBlockData()).getFacing() != face.getOppositeFace()) continue;
                BlockRegistry.Key pk = BlockRegistry.Key.of(port);
                active.add(pk);
                drive(port, pk, levelFor(ports.get(i), a, state != null, position, sinceEnd));
            }
        }
        // Comparators, die nicht mehr an einer Jukebox haengen, auf 0 setzen
        lastLevel.keySet().removeIf(pk -> {
            if (active.contains(pk)) return false;
            World world = Bukkit.getWorld(pk.world());
            if (world != null && world.isChunkLoaded(pk.x() >> 4, pk.z() >> 4)) {
                driver.set(world.getBlockAt(pk.x(), pk.y(), pk.z()), 0);
            }
            return true;
        });
    }
    private void drive(Block port, BlockRegistry.Key key, int level) {
        Integer last = lastLevel.get(key);
        if (last != null && last == level) return;
        if (driver.set(port, level)) lastLevel.put(key, level);
    }

    /** Fuer die Diagnose: Pegel direkt setzen und den Ausgang zurueckmelden. */
    int debugSet(Block comparator, int level) {
        if (driver == null) return -2;
        driver.set(comparator, level);
        return driver.read(comparator);
    }
}
