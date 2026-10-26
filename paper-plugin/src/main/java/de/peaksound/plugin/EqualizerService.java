package de.peaksound.plugin;

import de.peaksound.audio.Analysis;
import de.peaksound.protocol.PeakBlockType;
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

    private final PeakSoundPlugin plugin;
    private final BlockRegistry registry;
    private final AnalysisStore analysis;
    private final ComparatorDriver driver; // null = Equalizer aus
    /** Zuletzt gesetzter Pegel je Comparator, damit nur Aenderungen etwas kosten. */
    private final Map<BlockRegistry.Key, Integer> lastLevel = new HashMap<>();

    EqualizerService(PeakSoundPlugin plugin, BlockRegistry registry, AnalysisStore analysis, ComparatorDriver driver) {
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

    /** Pegel fuer die Seite {@code face} zur Songposition {@code positionMs}. */
    static int levelFor(BlockFace face, Analysis a, long positionMs) {
        return switch (face) {
            case NORTH -> a.level(Analysis.Band.BASS, positionMs);
            case EAST -> a.level(Analysis.Band.MID, positionMs);
            case SOUTH -> a.level(Analysis.Band.HIGH, positionMs);
            default -> Math.max(a.level(Analysis.Band.BASS, positionMs),
                    Math.max(a.level(Analysis.Band.MID, positionMs), a.level(Analysis.Band.HIGH, positionMs)));
        };
    }

    /**
     * Alle 2 Ticks: Pegel aller Equalizer-Comparators aktualisieren.
     *
     * @param effective Jukebox -> Song, der dort gerade zu hoeren ist (inklusive Netz-Mitglieder)
     */
    void update(Map<BlockRegistry.Key, PlaybackManager.State> effective, long now) {
        if (driver == null) return;
        Set<BlockRegistry.Key> active = new HashSet<>();
        for (Map.Entry<BlockRegistry.Key, PlaybackManager.State> e : effective.entrySet()) {
            World world = Bukkit.getWorld(e.getKey().world());
            if (world == null || !world.isChunkLoaded(e.getKey().x() >> 4, e.getKey().z() >> 4)) continue;
            Block jukebox = world.getBlockAt(e.getKey().x(), e.getKey().y(), e.getKey().z());
            Optional<Analysis> a = plugin.songs().get(e.getValue().hash).flatMap(analysis::peek);
            long position = now - e.getValue().startedAt;
            for (BlockFace face : FACES) {
                Block port = jukebox.getRelative(face);
                // Der Comparator liegt auf der Seite "face" der Jukebox; sein Eingang zeigt zurueck zur Jukebox
                if (port.getType() != Material.COMPARATOR
                        || ((Comparator) port.getBlockData()).getFacing() != face.getOppositeFace()) continue;
                BlockRegistry.Key pk = BlockRegistry.Key.of(port);
                active.add(pk);
                drive(port, pk, a.map(x -> levelFor(face, x, position)).orElse(0));
            }
        }
        // Comparators, deren Jukebox nicht mehr spielt, auf 0 setzen
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
