package de.peaksound.plugin;

import de.peaksound.protocol.DjMath;
import de.peaksound.protocol.DjSettings;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PeakBlockType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * DJ-Pult: zwei Jukebox-Netze (Deck A und B) werden ueber Crossfader und Decklautstaerke gemischt. Der Server
 * bestimmt die Ziellautstaerke je Deck ({@link PlaybackManager#setDeckGain}); beim Ueberblenden bekommen alle
 * Spieler in Reichweite gleichzeitig dasselbe Paket und rampen die Lautstaerke selbst, also klingt es ueberall gleich.
 */
final class DjService {
    private static final long DECK_CACHE_MS = 2000;

    private record Decks(List<List<BlockRegistry.Key>> jukeboxes, long at) {}

    private final PeakSoundPlugin plugin;
    private final BlockRegistry registry;
    private final NetworkRegistry networks;
    private final BlockSettingsStore<DjSettings> store;
    private final PlaybackManager playback;
    private final PlaylistService playlist;
    private final JukeboxSettingsStore jukeboxSettings;
    private final EditAccess access;
    private final Map<BlockRegistry.Key, Decks> cache = new HashMap<>();
    /** Jukeboxen, deren Faktor von einem Pult gesetzt wurde (zum Zuruecksetzen, wenn sie nicht mehr angeschlossen sind). */
    private final Set<BlockRegistry.Key> managed = new HashSet<>();

    DjService(PeakSoundPlugin plugin, BlockRegistry registry, NetworkRegistry networks, BlockSettingsStore<DjSettings> store,
              PlaybackManager playback, PlaylistService playlist, JukeboxSettingsStore jukeboxSettings, EditAccess access) {
        this.plugin = plugin;
        this.registry = registry;
        this.networks = networks;
        this.store = store;
        this.playback = playback;
        this.playlist = playlist;
        this.jukeboxSettings = jukeboxSettings;
        this.access = access;
    }

    private static final Comparator<BlockRegistry.Key> ORDER = Comparator
            .comparingInt(BlockRegistry.Key::x).thenComparingInt(BlockRegistry.Key::z).thenComparingInt(BlockRegistry.Key::y);

    /** Die Jukeboxen von Deck A (Index 0) und Deck B (Index 1); fehlt ein Deck, ist die Liste leer. */
    private List<List<BlockRegistry.Key>> decks(Block desk, DjSettings settings) {
        BlockRegistry.Key key = BlockRegistry.Key.of(desk);
        long now = System.currentTimeMillis();
        Decks cached = cache.get(key);
        if (cached == null || now - cached.at() > DECK_CACHE_MS) {
            List<List<BlockRegistry.Key>> nets = new ArrayList<>();
            for (NetworkRegistry.Net net : networks.netsAround(desk)) {
                List<BlockRegistry.Key> jukeboxes = net.jukeboxes();
                jukeboxes.sort(ORDER);
                nets.add(jukeboxes);
            }
            nets.sort(Comparator.comparing(l -> l.get(0), ORDER));
            while (nets.size() < 2) nets.add(List.of());
            cached = new Decks(List.copyOf(nets.subList(0, 2)), now);
            cache.put(key, cached);
        }
        List<List<BlockRegistry.Key>> out = cached.jukeboxes();
        return settings.swapped() ? List.of(out.get(1), out.get(0)) : out;
    }

    private Block blockOf(BlockRegistry.Key k) {
        World w = Bukkit.getWorld(k.world());
        return w == null || !w.isChunkLoaded(k.x() >> 4, k.z() >> 4) ? null : w.getBlockAt(k.x(), k.y(), k.z());
    }

    // ---- Menue ----

    void open(Player player, Block desk) {
        send(player, desk, true);
    }

    void onRequest(Player player, Packet.DjRequest request) {
        Block desk = access.validated(player, request.x(), request.y(), request.z(), PeakBlockType.DJ_DESK);
        if (desk != null) send(player, desk, false);
    }

    private void send(Player player, Block desk, boolean open) {
        DjSettings settings = store.get(BlockRegistry.Key.of(desk));
        List<List<BlockRegistry.Key>> decks = decks(desk, settings);
        Net.send(plugin, player, new Packet.DjState(desk.getX(), desk.getY(), desk.getZ(), settings, open,
                describe(decks.get(0)), describe(decks.get(1))));
    }

    private Packet.DjDeck describe(List<BlockRegistry.Key> jukeboxes) {
        if (jukeboxes.isEmpty()) return new Packet.DjDeck(false, "", false, "", -1, List.of());
        Block block = blockOf(jukeboxes.get(0));
        if (block == null) return new Packet.DjDeck(false, "", false, "", -1, List.of());
        List<PlaylistService.Track> tracks = playlist.tracks(block);
        List<Packet.PlaylistEntry> entries = new ArrayList<>();
        for (PlaylistService.Track t : tracks) {
            if (entries.size() >= Packet.DjDeck.MAX_ENTRIES) break;
            String title = cut(t.song() != null ? t.song().title() : t.written().title());
            String artist = cut(t.song() != null ? t.song().artist() : t.written().artist());
            entries.add(new Packet.PlaylistEntry(title, artist, t.song() != null ? t.song().durationMs() : 0, t.playable()));
        }
        var state = playback.state(block);
        String name = cut(jukeboxSettings.get(jukeboxes.get(0)).name());
        return new Packet.DjDeck(true, name, state.isPresent() && state.get().playing,
                state.isPresent() ? cut(state.get().title) : "", playback.currentIndex(block, tracks), entries);
    }

    private static String cut(String s) {
        return s.length() > 64 ? s.substring(0, 64) : s;
    }

    // ---- Aenderungen ----

    void onUpdate(Player player, Packet.DjUpdate update) {
        Block desk = access.validated(player, update.x(), update.y(), update.z(), PeakBlockType.DJ_DESK);
        if (desk == null) return;
        BlockRegistry.Key key = BlockRegistry.Key.of(desk);
        DjSettings old = store.get(key);
        DjSettings next = update.settings().sanitized();
        store.set(key, next);
        cache.remove(key); // "swapped" kann die Decks vertauscht haben
        // Nur eine Bewegung des Crossfaders ueberblendet; Lautstaerken folgen sofort
        int fadeMs = old.crossfader() != next.crossfader() ? next.fadeSeconds() * 1000 : 0;
        apply(desk, next, fadeMs);
        send(player, desk, false);
    }

    void onDeckAction(Player player, Packet.DjDeckAction action) {
        Block desk = access.validated(player, action.x(), action.y(), action.z(), PeakBlockType.DJ_DESK);
        if (desk == null || action.deck() < 0 || action.deck() > 1) return;
        List<BlockRegistry.Key> jukeboxes = decks(desk, store.get(BlockRegistry.Key.of(desk))).get(action.deck());
        Block block = jukeboxes.isEmpty() ? null : blockOf(jukeboxes.get(0));
        if (block != null) {
            var state = playback.state(block);
            boolean manualRecord = state.isPresent() && !state.get().fromPlaylist;
            switch (action.action()) {
                case PLAY -> {
                    if (manualRecord) player.sendMessage(Messages.error(player,
                            "Entnimm zuerst die eingelegte Platte, dann kann die Playlist spielen.",
                            "Remove the inserted record first so the playlist can play."));
                    else if (!playback.playIndex(block, action.index())) player.sendMessage(Messages.error(player,
                            "Dieser Song ist nicht verfuegbar.", "This song is not available."));
                }
                case SKIP -> {
                    if (!manualRecord) playback.playNext(block, state.orElse(null), true);
                }
                case STOP -> playback.stopPlayback(block);
            }
        }
        send(player, desk, false);
    }

    /** Berechnet die Ziellautstaerken beider Decks und wendet sie an (mit Ueberblendung, wenn {@code fadeMs > 0}). */
    private void apply(Block desk, DjSettings settings, int fadeMs) {
        List<List<BlockRegistry.Key>> decks = decks(desk, settings);
        double position = settings.crossfader() / 100.0;
        for (int d = 0; d < 2; d++) {
            List<BlockRegistry.Key> jukeboxes = decks.get(d);
            if (jukeboxes.isEmpty()) continue;
            int percent = DjMath.deckPercent(d == 1, position, d == 0 ? settings.volumeA() : settings.volumeB());
            playback.setDeckGain(jukeboxes, percent, fadeMs);
            managed.addAll(jukeboxes);
        }
    }

    /** Jede Sekunde: Pulte nachfuehren (neu angeschlossene Jukeboxen, geaenderte Netze) und verwaiste Faktoren loeschen. */
    void tick() {
        Set<BlockRegistry.Key> used = new HashSet<>();
        for (BlockRegistry.Key key : registry.keysOf(PeakBlockType.DJ_DESK)) {
            Block desk = blockOf(key);
            if (desk == null) continue;
            DjSettings settings = store.get(key);
            apply(desk, settings, 0);
            decks(desk, settings).forEach(used::addAll);
        }
        managed.removeIf(k -> {
            if (used.contains(k)) return false;
            playback.setDeckGain(List.of(k), 100, 0);
            return true;
        });
    }

    void remove(Block desk) {
        store.remove(desk);
        cache.remove(BlockRegistry.Key.of(desk));
    }

    /** Fuer den Testbefehl: welche Jukeboxen zu Deck A und B gehoeren und mit welchem Lautstaerke-Faktor. */
    String describe(Block desk) {
        DjSettings settings = store.get(BlockRegistry.Key.of(desk));
        StringBuilder sb = new StringBuilder("Crossfader ").append(settings.crossfader()).append(", Fade ").append(settings.fadeSeconds()).append(" s");
        List<List<BlockRegistry.Key>> decks = decks(desk, settings);
        for (int d = 0; d < 2; d++) {
            sb.append("\nDeck ").append(d == 0 ? "A" : "B").append(": ");
            for (BlockRegistry.Key k : decks.get(d)) {
                sb.append("(").append(k.x()).append(" ").append(k.y()).append(" ").append(k.z()).append(") Faktor ").append(playback.deckGainOf(k)).append(" % ");
            }
            if (decks.get(d).isEmpty()) sb.append("nicht verbunden");
        }
        return sb.toString();
    }

    DjSettings settingsOf(Block desk) { return store.get(BlockRegistry.Key.of(desk)); }

    void put(Block desk, DjSettings settings) { store.set(BlockRegistry.Key.of(desk), settings); cache.remove(BlockRegistry.Key.of(desk)); }
}