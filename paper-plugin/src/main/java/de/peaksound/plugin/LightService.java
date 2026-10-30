package de.peaksound.plugin;

import org.bukkit.plugin.java.JavaPlugin;
import de.peaksound.audio.Analysis;
import de.peaksound.audio.LightLogic;
import de.peaksound.protocol.LightSettings;
import de.peaksound.protocol.Limits;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PeakBlockType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Player;

/**
 * Scheinwerfer: Einstellungen, Verbindung zur Jukebox (ueber das Redstone-Netz), Meldungen an Spieler in der Naehe
 * und das echte Licht. Die Effekte selbst berechnet jeder Client aus Einstellungen, Songposition und Serveruhr
 * (so sind sie synchron); der Server sendet deshalb nur bei Aenderungen. Das echte Licht ist ein unsichtbarer
 * Lichtblock vor dem Scheinwerfer, dessen Stufe der Helligkeit folgt (mit hoechstens 2 Aenderungen pro Sekunde).
 */
final class LightService {
    /** Spieler bekommen einen Scheinwerfer ab dieser Entfernung (Strahl 16 Bloecke + Reserve). */
    private static final double SEND_RADIUS = 80;
    private static final double RELEASE_RADIUS = 100;
    private static final int BEAM_BLOCKS = 4;

    private final JavaPlugin plugin;
    private final BlockRegistry registry;
    private final NetworkRegistry networks;
    private final BlockSettingsStore<LightSettings> store;
    private final PlaybackManager playback;
    private final AnalysisStore analysis;
    private final SongStore songs;
    private final NetworkHandler network;
    private final EditAccess access;
    /** Pro Spieler: welche Scheinwerfer er bekam (Wert = Signatur aus Einstellungen und Song). */
    private final Map<UUID, Map<BlockRegistry.Key, String>> heard = new ConcurrentHashMap<>();
    /** Aktuell gesetzte Lichtbloecke je Scheinwerfer (Position und Stufe). */
    private record Placed(BlockRegistry.Key position, int level) {}

    private final Map<BlockRegistry.Key, Placed> placed = new HashMap<>();
    private final Path placedFile;

    LightService(JavaPlugin plugin, BlockRegistry registry, NetworkRegistry networks, BlockSettingsStore<LightSettings> store,
                 PlaybackManager playback, AnalysisStore analysis, SongStore songs, NetworkHandler network, EditAccess access) {
        this.plugin = plugin;
        this.registry = registry;
        this.networks = networks;
        this.store = store;
        this.playback = playback;
        this.analysis = analysis;
        this.songs = songs;
        this.network = network;
        this.access = access;
        this.placedFile = Ctx.dataFolder().toPath().resolve("light-blocks.txt");
    }

    // ---- Menue ----

    private boolean connected(BlockRegistry.Key key) {
        NetworkRegistry.Net net = networks.networkOf(key);
        return net != null && !net.jukeboxes().isEmpty();
    }

    void open(Player player, Block block) {
        BlockRegistry.Key key = BlockRegistry.Key.of(block);
        Net.send(plugin, player, new Packet.OpenLightMenu(block.getX(), block.getY(), block.getZ(), store.get(key), connected(key)));
    }

    void onUpdate(Player player, Packet.LightUpdate update) {
        Block block = access.validated(player, update.x(), update.y(), update.z(), PeakBlockType.SPOTLIGHT);
        if (block != null) store.set(BlockRegistry.Key.of(block), update.settings().sanitized());
    }

    LightSettings settingsOf(Block block) { return store.get(BlockRegistry.Key.of(block)); }

    void apply(Block block, LightSettings settings) { store.set(BlockRegistry.Key.of(block), settings); }

    void remove(Block block) {
        store.remove(block);
        removeLight(BlockRegistry.Key.of(block));
    }

    // ---- Analyse fuer die Effekte der Clients ----

    void onAnalysisRequest(Player player, Packet.AnalysisRequest request) {
        if (!SongStore.validHash(request.hash())) return;
        Song song = songs.get(request.hash()).orElse(null);
        if (song == null || (song.status() != Song.Status.OK && !Permissions.admin(player))) return;
        // leer = noch nicht berechnet, der Client fragt spaeter erneut (peek stoesst die Berechnung an)
        byte[] data = analysis.peek(song).map(Analysis::toBytes).orElse(new byte[0]);
        if (data.length > Limits.MAX_ANALYSIS_BYTES) data = new byte[0];
        Net.send(plugin, player, new Packet.AnalysisData(song.hash(), data));
    }
    // ---- Tick ----

    /** Alle 10 Ticks: Spieler in der Naehe informieren und das echte Licht nachfuehren. */
    void tick() {
        List<BlockRegistry.Key> spotlights = registry.keysOf(PeakBlockType.SPOTLIGHT);
        if (spotlights.isEmpty() && placed.isEmpty()) return;
        Map<BlockRegistry.Key, PlaybackManager.State> playing = playback.effective().jukeboxes();
        long now = System.currentTimeMillis();

        Set<BlockRegistry.Key> active = new HashSet<>();
        Map<BlockRegistry.Key, PlaybackManager.State> leaders = new HashMap<>();
        for (BlockRegistry.Key key : spotlights) {
            World world = Bukkit.getWorld(key.world());
            if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) continue;
            NetworkRegistry.Net net = networks.networkOf(key);
            if (net == null) continue;
            List<BlockRegistry.Key> jukeboxes = net.jukeboxes();
            if (jukeboxes.isEmpty()) continue; // nicht angeschlossen: aus
            active.add(key);
            for (BlockRegistry.Key j : jukeboxes) {
                PlaybackManager.State s = playing.get(j);
                if (s != null) { leaders.put(key, s); break; }
            }
        }

        // Spieler mit Mod: Start/Stop/Aenderung melden
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!network.hasMod(p)) continue;
            Map<BlockRegistry.Key, String> known = heard.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
            UUID world = p.getWorld().getUID();
            for (BlockRegistry.Key key : spotlights) {
                double d = key.world().equals(world) ? distance(p, key) : Double.MAX_VALUE;
                boolean isActive = active.contains(key);
                String signature = null;
                if (isActive) {
                    PlaybackManager.State s = leaders.get(key);
                    signature = store.get(key).hashCode() + "|" + (s == null ? "" : s.hash + "@" + s.startedAt);
                }
                String before = known.get(key);
                if (signature != null && d <= SEND_RADIUS && !signature.equals(before)) {
                    PlaybackManager.State s = leaders.get(key);
                    Net.send(plugin, p, new Packet.LightStart(key.x(), key.y(), key.z(), store.get(key),
                            s == null ? "" : s.hash, s == null ? 0 : s.startedAt));
                    known.put(key, signature);
                } else if (before != null && (signature == null || d > RELEASE_RADIUS)) {
                    Net.send(plugin, p, new Packet.LightStop(key.x(), key.y(), key.z()));
                    known.remove(key);
                }
            }
        }

        updateRealLight(active, leaders, now);
    }

    private static double distance(Player p, BlockRegistry.Key k) {
        double dx = p.getX() - (k.x() + 0.5), dy = p.getY() - (k.y() + 0.5), dz = p.getZ() - (k.z() + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ---- Echtes Licht ----

    /** Setzt je Scheinwerfer einen unsichtbaren Lichtblock im Strahl; Stufe nach Helligkeit, Modus und Band. */
    private void updateRealLight(Set<BlockRegistry.Key> active, Map<BlockRegistry.Key, PlaybackManager.State> leaders, long now) {
        // Nicht mehr aktive Scheinwerfer: Licht entfernen
        new ArrayList<>(placed.keySet()).stream().filter(k -> !active.contains(k)).forEach(this::removeLight);

        for (BlockRegistry.Key key : active) {
            LightSettings settings = store.get(key);
            PlaybackManager.State s = leaders.get(key);
            Analysis a = s == null ? null : songs.get(s.hash).flatMap(analysis::peek).orElse(null);
            long position = s == null ? 0 : now - s.startedAt;
            int level = LightLogic.lightLevel(settings, a, position);
            World world = Bukkit.getWorld(key.world());
            if (world == null) continue;
            Block lamp = world.getBlockAt(key.x(), key.y(), key.z());
            Block target = beamBlock(lamp, settings);
            Placed old = placed.get(key);
            if (target == null || level <= 0) {
                if (old != null) removeLight(key);
                continue;
            }
            BlockRegistry.Key targetKey = BlockRegistry.Key.of(target);
            if (old != null && old.position().equals(targetKey) && old.level() == level) continue;
            if (old != null && !old.position().equals(targetKey)) removeLight(key);
            Light data = (Light) Material.LIGHT.createBlockData();
            data.setLevel(level);
            target.setBlockData(data, false);
            placed.put(key, new Placed(targetKey, level));
            savePlaced();
        }
    }

    /** Der erste freie Block im Strahl (hoechstens {@link #BEAM_BLOCKS} Bloecke vom Scheinwerfer), oder null. */
    private Block beamBlock(Block lamp, LightSettings settings) {
        double yaw = Math.toRadians(settings.yaw()), pitch = Math.toRadians(settings.pitch());
        double dx = -Math.sin(yaw) * Math.cos(pitch), dy = Math.sin(pitch), dz = Math.cos(yaw) * Math.cos(pitch);
        for (int step = 1; step <= BEAM_BLOCKS; step++) {
            Block b = lamp.getWorld().getBlockAt((int) Math.floor(lamp.getX() + 0.5 + dx * step),
                    (int) Math.floor(lamp.getY() + 0.5 + dy * step), (int) Math.floor(lamp.getZ() + 0.5 + dz * step));
            if (b.equals(lamp)) continue;
            if (b.getType() == Material.LIGHT || b.getType().isAir()) return b;
            if (b.getType().isOccluding()) break; // vor einer Wand endet der Strahl
        }
        // Fallback: irgendein freier Nachbarblock
        for (var face : new org.bukkit.block.BlockFace[] {org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.NORTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.WEST, org.bukkit.block.BlockFace.DOWN}) {
            Block b = lamp.getRelative(face);
            if (b.getType() == Material.LIGHT || b.getType().isAir()) return b;
        }
        return null;
    }

    private void removeLight(BlockRegistry.Key spotlight) {
        Placed old = placed.remove(spotlight);
        if (old == null) return;
        World world = Bukkit.getWorld(old.position().world());
        if (world != null && world.isChunkLoaded(old.position().x() >> 4, old.position().z() >> 4)) {
            Block b = world.getBlockAt(old.position().x(), old.position().y(), old.position().z());
            if (b.getType() == Material.LIGHT) b.setType(Material.AIR, false);
        }
        savePlaced();
    }

    // ---- Aufraeumen: Lichtbloecke ueberleben keinen Absturz, darum merken wir uns ihre Positionen ----

    private void savePlaced() {
        List<String> lines = new ArrayList<>();
        placed.values().forEach(p -> lines.add(p.position().world() + ";" + p.position().x() + ";" + p.position().y() + ";" + p.position().z()));
        Bukkit.getAsyncScheduler().runNow(plugin, t -> {
            try {
                Files.createDirectories(placedFile.getParent());
                Files.write(placedFile, lines);
            } catch (IOException e) {
                Ctx.logger().log(Level.WARNING, "light-blocks.txt konnte nicht gespeichert werden", e);
            }
        });
    }

    /** Beim Start: Lichtbloecke aus einer frueheren Sitzung entfernen. */
    void cleanupLeftovers() {
        if (!Files.exists(placedFile)) return;
        try {
            for (String line : Files.readAllLines(placedFile)) {
                String[] p = line.split(";");
                if (p.length != 4) continue;
                World world = Bukkit.getWorld(UUID.fromString(p[0]));
                if (world == null) continue;
                Block b = world.getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
                if (b.getType() == Material.LIGHT) b.setType(Material.AIR, false);
            }
            Files.deleteIfExists(placedFile);
        } catch (IOException | RuntimeException e) {
            Ctx.logger().log(Level.WARNING, "Alte Lichtbloecke konnten nicht entfernt werden", e);
        }
    }

    /** Beim Abschalten: alle Lichtbloecke entfernen. */
    void shutdown() {
        new ArrayList<>(placed.keySet()).forEach(k -> {
            Placed old = placed.remove(k);
            World world = Bukkit.getWorld(old.position().world());
            if (world != null) {
                Block b = world.getBlockAt(old.position().x(), old.position().y(), old.position().z());
                if (b.getType() == Material.LIGHT) b.setType(Material.AIR, false);
            }
        });
        try {
            Files.deleteIfExists(placedFile);
        } catch (IOException ignored) { /* nichts mehr zu retten */ }
    }

    void onQuit(Player player) {
        heard.remove(player.getUniqueId());
    }

}
