package de.peaksound.plugin;

import de.peaksound.protocol.Limits;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PeakIds;
import de.peaksound.protocol.PeakItemType;
import de.peaksound.protocol.PeakBlockType;
import de.peaksound.protocol.RadioLogic;
import de.peaksound.protocol.RadioSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Radio: Ein Sender (Block) haengt im Redstone-Netz einer Jukebox und sendet deren aktuellen Song auf seiner Frequenz,
 * solange die Jukebox spielt. Ein Empfaenger (Block) ist auf eine Frequenz eingestellt und spielt den Song dieses Senders
 * wie ein Lautsprecher an seiner Position (gleiche 3D-Wiedergabe und Synchronisation wie Jukeboxen, siehe
 * {@link PlaybackManager#effective()}). Liegt kein Sender auf der Frequenz, rauscht der Empfaenger.
 * Der Sender muss in einem geladenen Chunk stehen.
 */
final class RadioService implements Listener {
    /** Wie weit ueber die Reichweite hinaus ein Spieler das Rauschen schon vorgeladen bekommt. */
    private static final int STATIC_MARGIN = 6;

    private final PeakSoundPlugin plugin;
    private final BlockRegistry registry;
    private final NetworkRegistry networks;
    private final BlockSettingsStore<RadioSettings> store;
    private final EditAccess access;
    private final NetworkHandler network;
    private final PeakItems items;
    private final NamespacedKey pocketFreq = new NamespacedKey(PeakIds.NAMESPACE, "pocket_freq");
    private final NamespacedKey pocketOn = new NamespacedKey(PeakIds.NAMESPACE, "pocket_on");
    private final NamespacedKey pocketVol = new NamespacedKey(PeakIds.NAMESPACE, "pocket_vol");
    /** Je Spieler: was sein Taschenradio gerade an den Client geschickt hat ("" = nichts). */
    private final Map<UUID, String> pocketHeard = new ConcurrentHashMap<>();
    private PlaybackManager playback;
    /** Zuletzt berechnete Sender: Frequenz -> laufender Song (nur Hauptthread, wird in {@link #emittersFor} erneuert). */
    private volatile Map<Integer, PlaybackManager.State> stations = Map.of();
    /** Je Spieler: Rauschen, das sein Client gerade spielt (Staerke, Reichweite, Lautstaerke). */
    private final Map<UUID, Map<BlockRegistry.Key, int[]>> staticHeard = new ConcurrentHashMap<>();

    RadioService(PeakSoundPlugin plugin, BlockRegistry registry, NetworkRegistry networks, BlockSettingsStore<RadioSettings> store,
                 EditAccess access, NetworkHandler network, PeakItems items) {
        this.items = items;
        this.network = network;
        this.plugin = plugin;
        this.registry = registry;
        this.networks = networks;
        this.store = store;
        this.access = access;
    }

    void setPlayback(PlaybackManager playback) { this.playback = playback; }

    // ---- Sender und Empfaenger -> Schallquellen ----

    /**
     * Berechnet aus den gerade spielenden Jukeboxen die Sender und liefert die Schallquellen der Empfaenger,
     * die einen Sender empfangen. Wird von {@link PlaybackManager#effective()} aufgerufen.
     */
    Map<BlockRegistry.Key, PlaybackManager.Emitter> emittersFor(Map<BlockRegistry.Key, PlaybackManager.State> playing) {
        Map<Integer, PlaybackManager.State> found = new HashMap<>();
        List<BlockRegistry.Key> transmitters = new ArrayList<>(registry.keysOf(PeakBlockType.RADIO_TRANSMITTER));
        transmitters.sort(Comparator.comparingInt(BlockRegistry.Key::x).thenComparingInt(BlockRegistry.Key::y).thenComparingInt(BlockRegistry.Key::z));
        for (BlockRegistry.Key t : transmitters) {
            RadioSettings cfg = store.get(t);
            if (!cfg.enabled() || found.containsKey(cfg.frequency())) continue; // bei gleicher Frequenz gewinnt der erste Sender
            NetworkRegistry.Net net = networks.networkOf(t);
            if (net == null) continue;
            for (BlockRegistry.Key jukebox : net.jukeboxes()) {
                PlaybackManager.State s = playing.get(jukebox);
                if (s != null) {
                    found.put(cfg.frequency(), s);
                    break;
                }
            }
        }
        stations = found;
        Map<BlockRegistry.Key, PlaybackManager.Emitter> out = new HashMap<>();
        for (BlockRegistry.Key r : registry.keysOf(PeakBlockType.RADIO_RECEIVER)) {
            RadioSettings cfg = store.get(r);
            PlaybackManager.State s = cfg.enabled() ? found.get(cfg.frequency()) : null;
            if (s != null && cfg.volume() > 0) out.put(r, new PlaybackManager.Emitter(r, s, 0, cfg.range(), cfg.volume(), 0));
        }
        return out;
    }

    /** Titel des Senders auf dieser Frequenz, "" wenn dort nichts gesendet wird. */
    private String stationTitle(int frequency) {
        PlaybackManager.State s = stations.get(frequency);
        return s == null ? "" : s.title;
    }

    // ---- Rauschen (alle 10 Ticks) ----

    void tick() {
        List<BlockRegistry.Key> receivers = registry.keysOf(PeakBlockType.RADIO_RECEIVER);
        if (receivers.isEmpty() && staticHeard.isEmpty()) return;
        Map<Integer, PlaybackManager.State> current = stations;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Map<BlockRegistry.Key, int[]> heard = staticHeard.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
            if (!network.hasMod(p)) {
                heard.clear();
                continue;
            }
            UUID world = p.getWorld().getUID();
            Map<BlockRegistry.Key, int[]> wanted = new HashMap<>();
            for (BlockRegistry.Key r : receivers) {
                if (!r.world().equals(world)) continue;
                RadioSettings cfg = store.get(r);
                if (!cfg.enabled() || cfg.volume() == 0) continue;
                int level = RadioLogic.staticLevel(cfg.frequency(), current.keySet());
                if (level == 0) continue;
                double dx = p.getX() - (r.x() + 0.5), dy = p.getY() - r.y(), dz = p.getZ() - (r.z() + 0.5);
                double reach = cfg.range() + STATIC_MARGIN;
                if (dx * dx + dy * dy + dz * dz > reach * reach) continue;
                wanted.put(r, new int[] {level, cfg.range(), cfg.volume()});
            }
            for (Map.Entry<BlockRegistry.Key, int[]> w : wanted.entrySet()) {
                int[] old = heard.get(w.getKey());
                if (old == null || old[0] != w.getValue()[0] || old[1] != w.getValue()[1] || old[2] != w.getValue()[2]) {
                    BlockRegistry.Key k = w.getKey();
                    int[] v = w.getValue();
                    Net.send(plugin, p, new Packet.RadioStatic(k.x(), k.y(), k.z(), v[1], v[2], v[0]));
                    heard.put(k, v);
                }
            }
            heard.entrySet().removeIf(e -> {
                if (wanted.containsKey(e.getKey())) return false;
                BlockRegistry.Key k = e.getKey();
                Net.send(plugin, p, new Packet.RadioStatic(k.x(), k.y(), k.z(), Math.max(1, e.getValue()[1]), e.getValue()[2], 0));
                return true;
            });
            pocketTick(p, current);
        }
    }

    // ---- Taschenradio ----

    private ItemStack pocketItem(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        if (items.typeOf(main).filter(t -> t == PeakItemType.POCKET_RADIO).isPresent()) return main;
        ItemStack off = p.getInventory().getItemInOffHand();
        return items.typeOf(off).filter(t -> t == PeakItemType.POCKET_RADIO).isPresent() ? off : null;
    }

    private RadioSettings pocketSettings(ItemStack item) {
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return new RadioSettings(pdc.getOrDefault(pocketOn, PersistentDataType.BYTE, (byte) 0) != 0,
                pdc.getOrDefault(pocketFreq, PersistentDataType.INTEGER, 1000), 16,
                pdc.getOrDefault(pocketVol, PersistentDataType.INTEGER, 80)).sanitized();
    }

    private void writePocket(ItemStack item, RadioSettings s) {
        ItemMeta meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        pdc.set(pocketOn, PersistentDataType.BYTE, (byte) (s.enabled() ? 1 : 0));
        pdc.set(pocketFreq, PersistentDataType.INTEGER, s.frequency());
        pdc.set(pocketVol, PersistentDataType.INTEGER, s.volume());
        item.setItemMeta(meta);
    }

    /** Das Taschenradio spielt weltweit: der Sender kommt ohne Entfernung direkt "ins Ohr" (Marker-Quelle am Spieler). */
    private void pocketTick(Player p, Map<Integer, PlaybackManager.State> current) {
        ItemStack item = pocketItem(p);
        String want = "";
        PlaybackManager.State st = null;
        int level = 0;
        RadioSettings cfg = null;
        if (item != null) {
            cfg = pocketSettings(item);
            if (cfg.enabled() && cfg.volume() > 0) {
                st = current.get(cfg.frequency());
                if (st != null) {
                    want = "song:" + st.hash + "@" + st.startedAt + "/" + cfg.volume();
                } else {
                    level = RadioLogic.staticLevel(cfg.frequency(), current.keySet());
                    want = "static:" + level + "/" + cfg.volume();
                }
            }
        }
        String old = pocketHeard.getOrDefault(p.getUniqueId(), "");
        if (want.equals(old)) return;
        int c = Limits.POCKET_COORD;
        if (old.startsWith("song:")) Net.send(plugin, p, new Packet.PlaybackStop(c, 0, c));
        else if (old.startsWith("static:")) Net.send(plugin, p, new Packet.RadioStatic(c, 0, c, 16, 80, 0));
        if (st != null) {
            Net.send(plugin, p, new Packet.SongInfo(st.hash, st.title.length() > Limits.MAX_TEXT ? st.title.substring(0, Limits.MAX_TEXT) : st.title,
                    st.artist.length() > Limits.MAX_TEXT ? st.artist.substring(0, Limits.MAX_TEXT) : st.artist, st.startedAt, st.durationMs));
            Net.send(plugin, p, playback.startPacket(st, c, 0, c, 16, cfg.volume(), System.currentTimeMillis()));
        }
        else if (want.startsWith("static:")) Net.send(plugin, p, new Packet.RadioStatic(c, 0, c, 16, cfg.volume(), level));
        if (want.isEmpty()) pocketHeard.remove(p.getUniqueId());
        else pocketHeard.put(p.getUniqueId(), want);
    }

    /** Rechtsklick: Taschenradio ein/aus, mit Shift das Einstell-Menue (mit Mod). */
    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (items.typeOf(hand).filter(t -> t == PeakItemType.POCKET_RADIO).isEmpty()) return;
        event.setCancelled(true);
        if (!Permissions.check(p, "peaksound.radio", "Du darfst das Taschenradio nicht benutzen.", "You may not use the pocket radio.")) return;
        RadioSettings cfg = pocketSettings(hand);
        if (p.isSneaking() && network.hasMod(p)) {
            int c = Limits.POCKET_COORD;
            Net.send(plugin, p, new Packet.OpenRadioMenu(c, 0, c, cfg, false, stationTitle(cfg.frequency())));
            return;
        }
        RadioSettings next = cfg.withEnabled(!cfg.enabled());
        writePocket(hand, next);
        p.sendActionBar(Messages.info(p, next.enabled() ? "Taschenradio an: " + RadioLogic.format(next.frequency()) : "Taschenradio aus.",
                next.enabled() ? "Pocket radio on: " + RadioLogic.format(next.frequency()) : "Pocket radio off."));
    }

    void onQuit(Player player) {
        staticHeard.remove(player.getUniqueId());
        pocketHeard.remove(player.getUniqueId());
    }

    /** Dem Spieler vergessen, was sein Client hoert (Welt- oder Verbindungswechsel: der Client startet leer). */
    void forget(Player player) {
        staticHeard.remove(player.getUniqueId());
        pocketHeard.remove(player.getUniqueId());
    }

    // ---- Menue ----

    void open(Player player, Block block, PeakBlockType type) {
        BlockRegistry.Key key = BlockRegistry.Key.of(block);
        RadioSettings cfg = store.get(key);
        Net.send(plugin, player, new Packet.OpenRadioMenu(block.getX(), block.getY(), block.getZ(), cfg,
                type == PeakBlockType.RADIO_TRANSMITTER, stationTitle(cfg.frequency())));
    }

    void onUpdate(Player player, Packet.RadioUpdate update) {
        if (update.x() == Limits.POCKET_COORD && update.z() == Limits.POCKET_COORD) { // Taschenradio in der Hand
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (items.typeOf(hand).filter(t -> t == PeakItemType.POCKET_RADIO).isEmpty() || !access.rateOk(player)) return;
            RadioSettings next = update.settings().sanitized();
            writePocket(hand, next);
            Net.send(plugin, player, new Packet.RadioStatus(update.x(), update.y(), update.z(), stationTitle(next.frequency())));
            return;
        }
        Block block = access.validatedAny(player, update.x(), update.y(), update.z(),
                PeakBlockType.RADIO_RECEIVER, PeakBlockType.RADIO_TRANSMITTER);
        if (block == null) return;
        RadioSettings next = update.settings().sanitized();
        store.set(BlockRegistry.Key.of(block), next);
        Bukkit.getScheduler().runTask(plugin, () -> {
            playback.tick(); // Sender und Empfaenger sofort neu zuordnen
            Net.send(plugin, player, new Packet.RadioStatus(update.x(), update.y(), update.z(), stationTitle(next.frequency())));
        });
    }

    /** Diagnosezeilen fuer den Debug-Bericht: Sender, Empfaenger und was dort ankommt. */
    String describe() {
        StringBuilder sb = new StringBuilder();
        Map<Integer, PlaybackManager.State> current = stations;
        sb.append("  Sender im Betrieb: ").append(current.size()).append('\n');
        current.forEach((f, s) -> sb.append("    ").append(RadioLogic.format(f)).append(" -> ").append(s.title).append('\n'));
        for (BlockRegistry.Key t : registry.keysOf(PeakBlockType.RADIO_TRANSMITTER)) {
            RadioSettings c = store.get(t);
            sb.append("  Sender ").append(t.x()).append(' ').append(t.y()).append(' ').append(t.z()).append(": ")
                    .append(RadioLogic.format(c.frequency())).append(c.enabled() ? " an" : " aus").append('\n');
        }
        for (BlockRegistry.Key r : registry.keysOf(PeakBlockType.RADIO_RECEIVER)) {
            RadioSettings c = store.get(r);
            PlaybackManager.State s = c.enabled() ? current.get(c.frequency()) : null;
            sb.append("  Empfaenger ").append(r.x()).append(' ').append(r.y()).append(' ').append(r.z()).append(": ")
                    .append(RadioLogic.format(c.frequency())).append(", Reichweite ").append(c.range()).append(", ").append(c.volume()).append(" %, ")
                    .append(!c.enabled() ? "aus" : s != null ? "empfaengt " + s.title : "Rauschen " + RadioLogic.staticLevel(c.frequency(), current.keySet()) + " %")
                    .append('\n');
        }
        return sb.toString();
    }

    void put(Block block, RadioSettings settings) { store.set(BlockRegistry.Key.of(block), settings); }

    void remove(Block block) {
        store.remove(block);
    }

    RadioSettings settingsOf(Block block) { return store.get(BlockRegistry.Key.of(block)); }
}
