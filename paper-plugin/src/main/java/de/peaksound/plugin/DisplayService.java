package de.peaksound.plugin;

import de.peaksound.protocol.DisplayWallLogic;
import de.peaksound.protocol.DisplayWallLogic.Facing;
import de.peaksound.protocol.DisplayWallLogic.Pos;
import de.peaksound.protocol.DisplayWallLogic.Tile;
import de.peaksound.protocol.DisplayWallLogic.Wall;
import de.peaksound.protocol.PeakBlockType;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

/**
 * Display-Bloecke zeigen das Cover (und Titel/Interpret) des Songs, der in ihrem Redstone-Netz laeuft. Nebeneinander liegende
 * Display-Bloecke in einer Ebene, die ein volles Rechteck bilden, ergeben eine grosse Cover-Wand. Technik: Auf der Bildschirm-
 * Seite jedes Blocks haengt ein unsichtbarer, leuchtender Bilderrahmen mit einer Karte (128x128 Pixel je Block). Das geht
 * komplett serverseitig und ist auch ohne Mod zu sehen. Die Karten werden nur an Spieler in der Naehe gesendet.
 */
final class DisplayService implements Listener {
    private static final int TILE = 128;
    private static final int SEND_RANGE = 48;
    private static final int MAX_COVERS = 8;

    /** Zeichnet das fertige Bild einer Kachel auf die Karte, einmal je Spieler und Version. */
    private static final class TileRenderer extends MapRenderer {
        volatile BufferedImage image;
        volatile int version;
        final Map<UUID, Integer> drawn = new ConcurrentHashMap<>();

        TileRenderer() { super(true); }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            BufferedImage img = image;
            if (img == null || drawn.getOrDefault(player.getUniqueId(), -1) == version) return;
            canvas.drawImage(0, 0, img);
            drawn.put(player.getUniqueId(), version);
        }
    }

    private static final class TileState {
        final TileRenderer renderer = new TileRenderer();
        MapView view;
        UUID frame;
        int version;
    }

    private final PeakSoundPlugin plugin;
    private final BlockRegistry registry;
    private final NetworkRegistry networks;
    private final PlaybackManager playback;
    private final SongStore songs;
    private final BlockSettingsStore<DisplaySettings> store;
    private final Map<BlockRegistry.Key, TileState> tiles = new HashMap<>();
    /** Je Wand (Schluessel = erster Block): was zuletzt gezeichnet wurde ("hash@WxH" oder "idle"). */
    private final Map<BlockRegistry.Key, String> wallSignature = new HashMap<>();
    /** Je Spieler: welche Version einer Kachel sein Client schon bekam. */
    private final Map<UUID, Map<BlockRegistry.Key, Integer>> sent = new HashMap<>();
    private final Map<String, BufferedImage> covers = new LinkedHashMap<>();
    private final Set<String> loading = ConcurrentHashMap.newKeySet();
    private final Set<String> noCover = ConcurrentHashMap.newKeySet();
    private final Set<UUID> ourFrames = ConcurrentHashMap.newKeySet();

    DisplayService(PeakSoundPlugin plugin, BlockRegistry registry, NetworkRegistry networks, PlaybackManager playback, SongStore songs,
                   BlockSettingsStore<DisplaySettings> store) {
        System.setProperty("java.awt.headless", "true");
        this.plugin = plugin;
        this.registry = registry;
        this.networks = networks;
        this.playback = playback;
        this.songs = songs;
        this.store = store;
    }

    private static BlockFace face(Facing f) {
        return switch (f) {
            case SOUTH -> BlockFace.SOUTH;
            case WEST -> BlockFace.WEST;
            case NORTH -> BlockFace.NORTH;
            case EAST -> BlockFace.EAST;
        };
    }

    private static Block blockOf(BlockRegistry.Key k) {
        World w = Bukkit.getWorld(k.world());
        return w == null || !w.isChunkLoaded(k.x() >> 4, k.z() >> 4) ? null : w.getBlockAt(k.x(), k.y(), k.z());
    }

    // ---- Platzieren, Drehen, Abbauen ----

    /** Neuer Display-Block: Der Bildschirm zeigt zum Spieler. */
    void placed(Block block, Player player) {
        BlockFace look = player.getFacing().getOppositeFace(); // Bildschirm zeigt dem Spieler entgegen
        Facing f = switch (look) {
            case WEST, NORTH_WEST, SOUTH_WEST -> Facing.WEST;
            case EAST, NORTH_EAST, SOUTH_EAST -> Facing.EAST;
            case NORTH -> Facing.NORTH;
            default -> Facing.SOUTH;
        };
        store.set(BlockRegistry.Key.of(block), new DisplaySettings(f.ordinal(), 0));
    }

    /** Test: Blickrichtung per Name setzen. */
    void setFacing(Block block, String name) {
        BlockRegistry.Key key = BlockRegistry.Key.of(block);
        Facing f = Facing.valueOf(name.toUpperCase());
        store.set(key, new DisplaySettings(f.ordinal(), store.get(key).mapId()));
        dropFrame(key);
        wallSignature.clear();
    }

    /** Peak-Tuner: Bildschirm um 90 Grad drehen. */
    void rotate(Player player, Block block) {
        BlockRegistry.Key key = BlockRegistry.Key.of(block);
        Facing next = Facing.byOrdinal(store.get(key).facing()).next();
        store.set(key, new DisplaySettings(next.ordinal(), store.get(key).mapId()));
        dropFrame(key);
        wallSignature.clear(); // alle Waende neu zeichnen
        player.sendActionBar(Messages.info(player, "Bildschirm zeigt jetzt nach " + next.name().toLowerCase() + ".",
                "Screen now faces " + next.name().toLowerCase() + "."));
    }

    void remove(Block block) {
        BlockRegistry.Key key = BlockRegistry.Key.of(block);
        dropFrame(key);
        tiles.remove(key);
        store.remove(block);
        wallSignature.clear();
    }

    private void dropFrame(BlockRegistry.Key key) {
        TileState t = tiles.get(key);
        if (t == null || t.frame == null) return;
        Entity e = Bukkit.getEntity(t.frame);
        if (e != null) e.remove();
        ourFrames.remove(t.frame);
        t.frame = null;
    }

    void shutdown() {
        for (BlockRegistry.Key k : new ArrayList<>(tiles.keySet())) dropFrame(k);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (ourFrames.contains(event.getEntity().getUniqueId())) event.setCancelled(true);
    }

    // ---- Zeitgeber (alle 10 Ticks) ----

    void tick() {
        List<BlockRegistry.Key> keys = registry.keysOf(PeakBlockType.DISPLAY);
        if (keys.isEmpty() && tiles.isEmpty()) return;
        Map<BlockRegistry.Key, PlaybackManager.State> playing = playback.effective().jukeboxes();
        // nach Welt gruppieren
        Map<UUID, Map<Pos, Facing>> byWorld = new HashMap<>();
        for (BlockRegistry.Key k : keys) {
            if (blockOf(k) == null) continue; // Chunk nicht geladen
            byWorld.computeIfAbsent(k.world(), w -> new HashMap<>())
                    .put(new Pos(k.x(), k.y(), k.z()), Facing.byOrdinal(store.get(k).facing()));
        }
        Set<BlockRegistry.Key> live = new HashSet<>();
        for (Map.Entry<UUID, Map<Pos, Facing>> world : byWorld.entrySet()) {
            for (Wall wall : DisplayWallLogic.walls(world.getValue())) {
                for (Tile t : wall.tiles()) live.add(new BlockRegistry.Key(world.getKey(), t.pos().x(), t.pos().y(), t.pos().z()));
                updateWall(world.getKey(), wall, playing);
            }
        }
        // Kacheln, die es nicht mehr gibt (abgebaut, Chunk entladen): Rahmen entfernen
        for (BlockRegistry.Key k : new ArrayList<>(tiles.keySet())) {
            if (!live.contains(k)) {
                dropFrame(k);
                if (!keys.contains(k)) tiles.remove(k);
            }
        }
        sendMaps();
    }

    private BlockRegistry.Key keyOf(UUID world, Pos p) { return new BlockRegistry.Key(world, p.x(), p.y(), p.z()); }

    private void updateWall(UUID world, Wall wall, Map<BlockRegistry.Key, PlaybackManager.State> playing) {
        BlockRegistry.Key first = keyOf(world, wall.tiles().get(0).pos());
        // Es genuegt, wenn EIN Block der Wand am Staub eines Jukebox-Netzes haengt
        PlaybackManager.State state = null;
        for (Tile t : wall.tiles()) {
            state = songOf(keyOf(world, t.pos()), playing);
            if (state != null) break;
        }
        String signature = (state == null ? "idle" : state.hash + "@" + state.startedAt) + "#" + wall.width() + "x" + wall.height();
        boolean changed = !signature.equals(wallSignature.get(first));
        BufferedImage cover = null;
        if (state != null && changed) {
            cover = cover(state.hash);
            if (cover == null && !noCover.contains(state.hash)) return; // Cover wird noch geladen: naechster Durchlauf
        }
        if (changed) {
            BufferedImage full = compose(wall, cover, state);
            for (Tile t : wall.tiles()) {
                TileState ts = tiles.computeIfAbsent(keyOf(world, t.pos()), k -> new TileState());
                ts.renderer.image = full.getSubimage(t.col() * TILE, t.row() * TILE, TILE, TILE);
                ts.version++;
                ts.renderer.version = ts.version;
            }
            wallSignature.put(first, signature);
        }
        for (Tile t : wall.tiles()) ensureFrame(world, wall, t);
    }

    private PlaybackManager.State songOf(BlockRegistry.Key display, Map<BlockRegistry.Key, PlaybackManager.State> playing) {
        NetworkRegistry.Net net = networks.networkOf(display);
        if (net == null) return null;
        for (BlockRegistry.Key jukebox : net.jukeboxes()) {
            PlaybackManager.State s = playing.get(jukebox);
            if (s != null) return s;
        }
        return null;
    }

    // ---- Bilder ----

    /** Das Cover, oder null, wenn es noch geladen wird oder keins hat (dann steht der Hash in {@code noCover}). */
    private BufferedImage cover(String hash) {
        BufferedImage cached = covers.get(hash);
        if (cached != null) return cached;
        if (noCover.contains(hash) || !loading.add(hash)) return null;
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            BufferedImage image = null;
            try {
                File f = songs.coverPath(hash).toFile();
                if (f.exists()) image = ImageIO.read(new ByteArrayInputStream(Files.readAllBytes(f.toPath())));
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "Cover nicht lesbar: " + hash, e);
            }
            BufferedImage result = image;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (result == null) noCover.add(hash);
                else {
                    covers.put(hash, result);
                    if (covers.size() > MAX_COVERS) covers.remove(covers.keySet().iterator().next());
                }
                loading.remove(hash);
            });
        });
        return null;
    }

    /** Das ganze Wandbild: Cover mittig zugeschnitten, darunter ein dunkler Streifen mit Titel und Interpret (wenn Schriften da sind). */
    private BufferedImage compose(Wall wall, BufferedImage cover, PlaybackManager.State state) {
        int w = wall.width() * TILE, h = wall.height() * TILE;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (cover != null) {
                int[] r = DisplayWallLogic.sourceRect(cover.getWidth(), cover.getHeight(), wall.width(), wall.height(), 0, 0);
                int sw = r[2] * wall.width(), sh = r[3] * wall.height();
                g.drawImage(cover, 0, 0, w, h, r[0], r[1], r[0] + sw, r[1] + sh, null);
            } else {
                // Platzhalter: Farbverlauf aus dem Song (oder dunkel, wenn nichts spielt)
                int hue = state == null ? 0 : Math.abs(state.hash.hashCode());
                Color a = state == null ? new Color(0x16161e) : Color.getHSBColor((hue % 360) / 360f, 0.55f, 0.55f);
                Color b = state == null ? new Color(0x2a2145) : Color.getHSBColor(((hue / 7) % 360) / 360f, 0.7f, 0.25f);
                g.setPaint(new GradientPaint(0, 0, a, w, h, b));
                g.fillRect(0, 0, w, h);
            }
            drawText(g, w, h, state);
        } finally {
            g.dispose();
        }
        return img;
    }

    private void drawText(Graphics2D g, int w, int h, PlaybackManager.State state) {
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF); // Karten haben wenige Farben
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, 12);
            g.setFont(font);
            int strip = 30;
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(0, h - strip, w, strip);
            g.setColor(Color.WHITE);
            String title = state == null ? "PeakSound" : state.title;
            String artist = state == null ? "" : state.artist;
            g.drawString(fit(g, title, w - 8), 4, h - strip + 12);
            if (!artist.isBlank()) {
                g.setFont(font.deriveFont(Font.PLAIN, 11f));
                g.setColor(new Color(0xBBBBBB));
                g.drawString(fit(g, artist, w - 8), 4, h - strip + 25);
            }
        } catch (Throwable t) { // keine Schriften auf diesem Server: Cover ohne Text
            plugin.getLogger().log(Level.FINE, "Text auf dem Display nicht moeglich", t);
        }
    }

    private static String fit(Graphics2D g, String text, int maxWidth) {
        String t = text;
        while (t.length() > 1 && g.getFontMetrics().stringWidth(t) > maxWidth) t = t.substring(0, t.length() - 1);
        return t;
    }

    // ---- Bilderrahmen und Karten ----

    private void ensureFrame(UUID world, Wall wall, Tile tile) {
        BlockRegistry.Key key = keyOf(world, tile.pos());
        TileState ts = tiles.get(key);
        if (ts == null) return;
        if (ts.frame != null) {
            Entity e = Bukkit.getEntity(ts.frame);
            if (e != null && e.isValid()) return;
            ourFrames.remove(ts.frame);
            ts.frame = null;
        }
        World w = Bukkit.getWorld(world);
        if (w == null) return;
        Block block = w.getBlockAt(tile.pos().x(), tile.pos().y(), tile.pos().z());
        BlockFace face = face(wall.facing());
        Block front = block.getRelative(face);
        if (!front.getType().isAir()) return; // der Platz vor dem Bildschirm ist belegt
        if (ts.view == null) ts.view = mapView(key, w, ts.renderer);
        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) map.getItemMeta();
        meta.setMapView(ts.view);
        map.setItemMeta(meta);
        try {
            GlowItemFrame frame = w.spawn(front.getLocation(), GlowItemFrame.class, f -> {
                f.setFacingDirection(face, true);
                f.setItem(map, false);
                f.setVisible(false);
                f.setFixed(true);
                f.setInvulnerable(true);
                f.setPersistent(false);
            });
            ts.frame = frame.getUniqueId();
            ourFrames.add(ts.frame);
            sent.values().forEach(m -> m.remove(key)); // Spieler bekommen die Karte neu
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.FINE, "Display-Rahmen konnte nicht gesetzt werden", e);
        }
    }

    /** Die Karte dieser Kachel: die gespeicherte ID wiederverwenden, sonst eine neue anlegen. */
    private MapView mapView(BlockRegistry.Key key, World world, TileRenderer renderer) {
        DisplaySettings s = store.get(key);
        MapView view = s.mapId() > 0 ? Bukkit.getMap(s.mapId() - 1) : null; // gespeichert wird ID + 1 (0 = noch keine Karte)
        if (view == null) {
            view = Bukkit.createMap(world);
            store.set(key, new DisplaySettings(s.facing(), view.getId() + 1));
        }
        for (MapRenderer r : new ArrayList<>(view.getRenderers())) view.removeRenderer(r);
        view.addRenderer(renderer);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        return view;
    }

    /** Schickt neue Kartenbilder an Spieler in der Naehe. */
    private void sendMaps() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Map<BlockRegistry.Key, Integer> got = sent.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
            for (Map.Entry<BlockRegistry.Key, TileState> e : tiles.entrySet()) {
                BlockRegistry.Key k = e.getKey();
                TileState t = e.getValue();
                if (t.view == null || t.frame == null || t.renderer.image == null) continue;
                if (!k.world().equals(p.getWorld().getUID())) continue;
                double dx = p.getX() - k.x(), dz = p.getZ() - k.z();
                if (dx * dx + dz * dz > (double) SEND_RANGE * SEND_RANGE) continue;
                if (got.getOrDefault(k, -1) == t.version) continue;
                p.sendMap(t.view);
                got.put(k, t.version);
            }
        }
    }

    @EventHandler
    public void onQuitEvent(org.bukkit.event.player.PlayerQuitEvent event) {
        onQuit(event.getPlayer());
    }

    void onQuit(Player player) {
        sent.remove(player.getUniqueId());
        for (TileState t : tiles.values()) t.renderer.drawn.remove(player.getUniqueId());
    }

    /** Fuer Tests: schreibt die aktuellen Wandbilder als PNG nach plugins/PeakSound/display-debug. @return Anzahl */
    int dump() {
        int n = 0;
        File dir = new File(plugin.getDataFolder(), "display-debug");
        dir.mkdirs();
        for (Map.Entry<BlockRegistry.Key, TileState> e : tiles.entrySet()) {
            BufferedImage img = e.getValue().renderer.image;
            if (img == null) continue;
            try {
                ImageIO.write(img, "png", new File(dir, e.getKey().x() + "_" + e.getKey().y() + "_" + e.getKey().z() + ".png"));
                n++;
            } catch (java.io.IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Debug-Bild nicht schreibbar", ex);
            }
        }
        return n;
    }

    String describe() {
        StringBuilder sb = new StringBuilder("  Display-Kacheln: ").append(tiles.size()).append('\n');
        for (Map.Entry<BlockRegistry.Key, TileState> e : tiles.entrySet()) {
            BlockRegistry.Key k = e.getKey();
            sb.append("    ").append(k.x()).append(' ').append(k.y()).append(' ').append(k.z()).append(": Karte ")
                    .append(e.getValue().view == null ? "-" : e.getValue().view.getId()).append(", Rahmen ")
                    .append(e.getValue().frame != null).append(", Version ").append(e.getValue().version).append('\n');
        }
        return sb.toString();
    }
}