package de.peaksound.plugin;

import de.peaksound.protocol.Limits;
import de.peaksound.protocol.Packet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Verwaltet, welche Jukebox welche Platte spielt, und meldet Spielern in der Naehe Start und Ende.
 * Die Wiedergabe selbst (Audio) passiert beim Client; der Server liefert nur Song, Startzeit und Position.
 */
final class PlaybackManager {
    /** Zustand einer Jukebox mit eingelegter Platte. */
    static final class State {
        final ItemStack record;
        final String hash;
        final int durationMs;
        final long startedAt;
        final int range;
        final int volume;
        boolean playing;

        State(ItemStack record, String hash, int durationMs, long startedAt, int range, int volume, boolean playing) {
            this.record = record;
            this.hash = hash;
            this.durationMs = durationMs;
            this.startedAt = startedAt;
            this.range = range;
            this.volume = volume;
            this.playing = playing;
        }
    }

    /** Spieler hoeren eine Jukebox schon ab dieser Entfernung (Reichweite + Vorlauf zum Laden). */
    private static final int PRELOAD_MARGIN = 8;
    /** Erst ab dieser zusaetzlichen Entfernung wird der Client wieder entlastet. */
    private static final int RELEASE_MARGIN = 24;

    private final PeakSoundPlugin plugin;
    private final NetworkHandler network;
    private final Map<BlockRegistry.Key, State> states = new ConcurrentHashMap<>();
    private final Map<UUID, Set<BlockRegistry.Key>> heard = new ConcurrentHashMap<>();

    PlaybackManager(PeakSoundPlugin plugin, NetworkHandler network) {
        this.plugin = plugin;
        this.network = network;
    }

    boolean hasRecord(Block block) {
        return states.containsKey(BlockRegistry.Key.of(block));
    }

    /** Legt eine Platte ein und startet sie. */
    void insert(Block block, ItemStack record, Song song) {
        int range = clamp(plugin.getConfig().getInt("playback.default-range", 16), Limits.MIN_RANGE, Limits.MAX_RANGE);
        int volume = clamp(plugin.getConfig().getInt("playback.default-volume", 100), 0, 100);
        ItemStack single = record.clone();
        single.setAmount(1);
        states.put(BlockRegistry.Key.of(block),
                new State(single, song.hash(), song.durationMs(), System.currentTimeMillis(), range, volume, true));
        save();
        tick(); // Spieler in der Naehe sofort informieren
    }

    /** Nimmt die Platte heraus und stoppt die Wiedergabe. */
    Optional<ItemStack> eject(Block block) {
        BlockRegistry.Key key = BlockRegistry.Key.of(block);
        State state = states.remove(key);
        if (state == null) return Optional.empty();
        stopFor(key);
        save();
        return Optional.of(state.record);
    }

    private void stopFor(BlockRegistry.Key key) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Set<BlockRegistry.Key> set = heard.get(p.getUniqueId());
            if (set != null && set.remove(key)) {
                Net.send(plugin, p, new Packet.PlaybackStop(key.x(), key.y(), key.z()));
            }
        }
    }

    /** Wird alle 10 Ticks aufgerufen: Songende erkennen und Spieler in Reichweite informieren. */
    void tick() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Map.Entry<BlockRegistry.Key, State> e : states.entrySet()) {
            State s = e.getValue();
            if (s.playing && now - s.startedAt >= s.durationMs) {
                s.playing = false;
                changed = true;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Set<BlockRegistry.Key> set = heard.get(p.getUniqueId());
                    if (set != null && set.remove(e.getKey())) {
                        Net.send(plugin, p, new Packet.PlaybackStop(e.getKey().x(), e.getKey().y(), e.getKey().z()));
                    }
                }
            }
        }
        if (changed) save();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!network.hasMod(p)) continue;
            Set<BlockRegistry.Key> set = heard.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
            UUID world = p.getWorld().getUID();
            Location loc = p.getLocation();
            for (Map.Entry<BlockRegistry.Key, State> e : states.entrySet()) {
                BlockRegistry.Key key = e.getKey();
                State s = e.getValue();
                if (!s.playing) continue;
                boolean sameWorld = key.world().equals(world);
                double d2 = sameWorld
                        ? sq(loc.getX() - (key.x() + 0.5)) + sq(loc.getY() - (key.y() + 0.5)) + sq(loc.getZ() - (key.z() + 0.5))
                        : Double.MAX_VALUE;
                boolean listening = set.contains(key);
                if (!listening && d2 <= sq(s.range + PRELOAD_MARGIN)) {
                    int offset = (int) Math.min(Integer.MAX_VALUE, now - s.startedAt);
                    Net.send(plugin, p, new Packet.PlaybackStart(key.x(), key.y(), key.z(), s.hash, offset, now,
                            s.durationMs, songChannels(s.hash), s.range, s.volume));
                    set.add(key);
                } else if (listening && d2 > sq(s.range + RELEASE_MARGIN)) {
                    Net.send(plugin, p, new Packet.PlaybackStop(key.x(), key.y(), key.z()));
                    set.remove(key);
                }
            }
        }
    }

    private int songChannels(String hash) {
        return plugin.songs().get(hash).map(Song::channels).filter(c -> c >= 1 && c <= 16).orElse(2);
    }

    void onQuit(Player player) {
        heard.remove(player.getUniqueId());
    }

    /** Beim Weltwechsel/Respawn muss der Client neu informiert werden. */
    void forget(Player player) {
        Set<BlockRegistry.Key> set = heard.get(player.getUniqueId());
        if (set != null) set.clear();
    }

    private static double sq(double v) { return v * v; }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    // ---- Persistenz (playback.yml) ----

    /** Aktueller Zustand als YAML-Text (wird asynchron oder beim Abschalten geschrieben). */
    private String snapshot() {
        List<Map<String, Object>> list = new ArrayList<>();
        states.forEach((k, s) -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("world", k.world().toString());
            m.put("x", k.x());
            m.put("y", k.y());
            m.put("z", k.z());
            m.put("record", Base64.getEncoder().encodeToString(s.record.serializeAsBytes()));
            m.put("hash", s.hash);
            m.put("duration", s.durationMs);
            m.put("started", s.startedAt);
            m.put("range", s.range);
            m.put("volume", s.volume);
            m.put("playing", s.playing);
            list.add(m);
        });
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("jukeboxes", list);
        return yaml.saveToString();
    }

    private void write(String text) {
        try {
            java.nio.file.Path file = plugin.getDataFolder().toPath().resolve("playback.yml");
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, text);
        } catch (java.io.IOException e) {
            plugin.getLogger().log(Level.SEVERE, "playback.yml konnte nicht gespeichert werden", e);
        }
    }

    private void save() {
        String text = snapshot();
        Bukkit.getAsyncScheduler().runNow(plugin, t -> write(text));
    }

    void saveBlocking() {
        write(snapshot());
    }
    @SuppressWarnings("unchecked")
    void load() {
        java.nio.file.Path file = plugin.getDataFolder().toPath().resolve("playback.yml");
        if (!java.nio.file.Files.exists(file)) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        for (Map<?, ?> m : yaml.getMapList("jukeboxes")) {
            try {
                BlockRegistry.Key key = new BlockRegistry.Key(UUID.fromString((String) m.get("world")),
                        ((Number) m.get("x")).intValue(), ((Number) m.get("y")).intValue(), ((Number) m.get("z")).intValue());
                ItemStack record = ItemStack.deserializeBytes(Base64.getDecoder().decode((String) m.get("record")));
                states.put(key, new State(record, (String) m.get("hash"), ((Number) m.get("duration")).intValue(),
                        ((Number) m.get("started")).longValue(), ((Number) m.get("range")).intValue(),
                        ((Number) m.get("volume")).intValue(), (Boolean) m.get("playing")));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Ungueltiger Eintrag in playback.yml uebersprungen: " + e.getMessage());
            }
        }
    }
}
