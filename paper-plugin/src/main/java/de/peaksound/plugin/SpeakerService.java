package de.peaksound.plugin;

import de.peaksound.protocol.PeakBlockType;
import de.peaksound.protocol.SpeakerChannel;
import de.peaksound.protocol.SpeakerNumbering;
import de.peaksound.protocol.SpeakerSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Verwaltet die Lautsprecher: Zuordnung zur Jukebox ueber das Redstone-Netz, Nummern (1, 2, 3 ... je System, auf
 * der Textur sichtbar) und Standard-Kanaele. Ein Lautsprecher gehoert zu dem Netz, in dem er haengt, und spielt
 * dort den Song des Anfuehrers. Seine Jukebox ist die naechstgelegene des Netzes (liefert Reichweite und Name).
 */
final class SpeakerService {
    /** Ein Lautsprecher mit allen Angaben, die Wiedergabe und Menues brauchen. */
    record Speaker(BlockRegistry.Key key, SpeakerSettings settings, int number, BlockRegistry.Key jukebox) {}

    private final PeakSoundPlugin plugin;
    private final BlockRegistry registry;
    private final NetworkRegistry networks;
    private final SpeakerStore store;

    SpeakerService(PeakSoundPlugin plugin, BlockRegistry registry, NetworkRegistry networks, SpeakerStore store) {
        this.plugin = plugin;
        this.registry = registry;
        this.networks = networks;
        this.store = store;
    }

    /** Alle Lautsprecher eines Netzes (mit Jukebox, Nummer und Einstellungen), nach Nummer sortiert. Leer ohne Jukebox. */
    List<Speaker> speakersOf(NetworkRegistry.Net net) {
        List<BlockRegistry.Key> jukeboxes = net.jukeboxes();
        if (jukeboxes.isEmpty()) return List.of();
        List<Speaker> out = new ArrayList<>();
        net.members.forEach((key, type) -> {
            if (type != PeakBlockType.SPEAKER) return;
            store.get(key).ifPresent(d -> out.add(new Speaker(key, d.settings(), d.number(), nearest(key, jukeboxes))));
        });
        out.sort(Comparator.comparingInt((Speaker s) -> s.number() == 0 ? Integer.MAX_VALUE : s.number()));
        return out;
    }

    /** Die naechstgelegene Jukebox (3D-Abstand) zu einem Block. */
    private static BlockRegistry.Key nearest(BlockRegistry.Key from, List<BlockRegistry.Key> jukeboxes) {
        BlockRegistry.Key best = jukeboxes.get(0);
        long bestD = Long.MAX_VALUE;
        for (BlockRegistry.Key j : jukeboxes) {
            long dx = j.x() - from.x(), dy = j.y() - from.y(), dz = j.z() - from.z();
            long d = dx * dx + dy * dy + dz * dz;
            if (d < bestD || (d == bestD && j.toString().compareTo(best.toString()) < 0)) {
                best = j;
                bestD = d;
            }
        }
        return best;
    }

    /** Die Lautsprecher, die zu genau dieser Jukebox gehoeren. */
    List<Speaker> speakersOfJukebox(BlockRegistry.Key jukebox) {
        NetworkRegistry.Net net = networks.networkOf(jukebox);
        if (net == null) return List.of();
        return speakersOf(net).stream().filter(s -> s.jukebox().equals(jukebox)).toList();
    }

    /** Ein Lautsprecher mit seinen aktuellen Angaben, oder null, wenn er (noch) mit keiner Jukebox verbunden ist. */
    Speaker speaker(Block block) {
        BlockRegistry.Key key = BlockRegistry.Key.of(block);
        NetworkRegistry.Net net = networks.networkOf(key);
        if (net == null) return null;
        return speakersOf(net).stream().filter(s -> s.key().equals(key)).findFirst().orElse(null);
    }

    SpeakerSettings settingsOf(Block block) {
        return store.get(BlockRegistry.Key.of(block)).map(SpeakerStore.Data::settings)
                .orElse(SpeakerSettings.defaults(SpeakerChannel.FRONT_LEFT));
    }

    void update(Block block, SpeakerSettings settings) {
        BlockRegistry.Key key = BlockRegistry.Key.of(block);
        int number = store.get(key).map(SpeakerStore.Data::number).orElse(0);
        store.put(key, new SpeakerStore.Data(settings.sanitized(), number));
    }

    void remove(Block block) {
        store.remove(block);
    }

    /**
     * Einmal pro Sekunde: Nummern und Standard-Kanaele aller Lautsprecher abgleichen. Die Netze kommen aus dem
     * Cache, hier wird nur dann gerechnet, wenn sich etwas geaendert hat.
     */
    void refresh() {
        Set<NetworkRegistry.Net> done = new HashSet<>();
        for (BlockRegistry.Key key : registry.keysOf(PeakBlockType.SPEAKER)) {
            World world = Bukkit.getWorld(key.world());
            if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) continue;
            NetworkRegistry.Net net = networks.networkOf(key);
            if (net == null) continue;
            if (!done.add(net)) continue;
            numberNet(net, world);
        }
    }

    /** Vergibt in einem Netz stabile, eindeutige Nummern (bestehende bleiben) und Standard-Kanaele. */
    private void numberNet(NetworkRegistry.Net net, World world) {
        List<BlockRegistry.Key> speakerKeys = new ArrayList<>();
        net.members.forEach((k, t) -> { if (t == PeakBlockType.SPEAKER) speakerKeys.add(k); });
        boolean hasJukebox = !net.jukeboxes().isEmpty();
        // Feste Reihenfolge: bestehende Nummern zuerst (aufsteigend), dann Neue nach Position
        speakerKeys.sort(Comparator
                .comparingInt((BlockRegistry.Key k) -> store.get(k).map(SpeakerStore.Data::number).filter(n -> n > 0).orElse(Integer.MAX_VALUE))
                .thenComparingInt(BlockRegistry.Key::x).thenComparingInt(BlockRegistry.Key::y).thenComparingInt(BlockRegistry.Key::z));

        Set<Integer> usedNumbers = new HashSet<>();
        Set<SpeakerChannel> usedChannels = new HashSet<>();
        for (BlockRegistry.Key k : speakerKeys) store.get(k).ifPresent(d -> usedChannels.add(d.settings().channel()));

        for (BlockRegistry.Key key : speakerKeys) {
            SpeakerStore.Data data = store.get(key).orElse(null);
            if (data == null) {
                SpeakerChannel suggested = SpeakerChannel.FRONT_LEFT;
                for (SpeakerChannel c : SpeakerChannel.DEFAULT_ORDER) {
                    if (!usedChannels.contains(c)) { suggested = c; break; }
                }
                usedChannels.add(suggested);
                data = store.getOrCreate(key, suggested);
            }
            int number = 0;
            if (hasJukebox) {
                number = data.number();
                if (number < 1 || number > SpeakerNumbering.MAX_NUMBER || !usedNumbers.add(number)) {
                    number = 0;
                    for (int n = 1; n <= SpeakerNumbering.MAX_NUMBER; n++) {
                        if (usedNumbers.add(n)) { number = n; break; }
                    }
                }
            }
            if (number != data.number()) store.put(key, new SpeakerStore.Data(data.settings(), number));
            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            if (PeakBlocks.speakerNumber(block) != number && registry.get(block).isPresent()) {
                PeakBlocks.setSpeakerNumber(block, number);
            }
        }
        if (speakerKeys.size() > SpeakerNumbering.MAX_NUMBER && hasJukebox) {
            plugin.getLogger().warning("Mehr als " + SpeakerNumbering.MAX_NUMBER + " Lautsprecher in einem System: die weiteren bleiben stumm.");
        }
    }
}
