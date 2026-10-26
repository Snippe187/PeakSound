package de.peaksound.plugin;

import de.peaksound.protocol.MicSettings;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PeakBlockType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Mikrofon-Bloecke. Dieser Dienst kennt Simple Voice Chat nicht: Er bestimmt auf dem Hauptthread, welche Mikrofone ein
 * Spieler gerade anspricht und ueber welche Lautsprecher (oder Jukeboxen) seine Stimme laeuft, und regelt das Ducking
 * der Musik. Die Sprachdaten selbst verarbeitet {@link VoiceChatHook} auf dem Thread von Simple Voice Chat.
 */
final class MicService {
    /** Ein Ausgang: Position und Reichweite, ueber die die Stimme zu hoeren ist. */
    record Output(BlockRegistry.Key key, double x, double y, double z, int range, int volume) {}

    /** Ein Mikrofon, das ein Spieler anspricht. */
    record Target(BlockRegistry.Key mic, UUID world, MicSettings settings, List<Output> outputs) {}

    /** So lange (ms) nach dem letzten Sprachpaket gilt ein Mikrofon als "wird gesprochen". */
    private static final long SPEAKING_MS = 400;
    private static final int DUCK_FADE_MS = 300;
    private static final int RESTORE_FADE_MS = 700;

    private final PeakSoundPlugin plugin;
    private final BlockRegistry registry;
    private final NetworkRegistry networks;
    private final BlockSettingsStore<MicSettings> store;
    private final SpeakerService speakers;
    private final JukeboxSettingsStore jukeboxSettings;
    private final PlaybackManager playback;
    private final EditAccess access;
    private final Map<UUID, List<Target>> targets = new ConcurrentHashMap<>();
    private final Map<BlockRegistry.Key, Long> lastSpoke = new ConcurrentHashMap<>();
    /** Jukeboxen, deren Musik gerade wegen eines Mikrofons leiser ist. */
    private final Map<BlockRegistry.Key, List<BlockRegistry.Key>> ducked = new ConcurrentHashMap<>();
    private volatile boolean voiceChat;

    MicService(PeakSoundPlugin plugin, BlockRegistry registry, NetworkRegistry networks, BlockSettingsStore<MicSettings> store,
               SpeakerService speakers, JukeboxSettingsStore jukeboxSettings, PlaybackManager playback, EditAccess access) {
        this.plugin = plugin;
        this.registry = registry;
        this.networks = networks;
        this.store = store;
        this.speakers = speakers;
        this.jukeboxSettings = jukeboxSettings;
        this.playback = playback;
        this.access = access;
    }

    private static Block blockOf(BlockRegistry.Key k) {
        World w = Bukkit.getWorld(k.world());
        return w == null || !w.isChunkLoaded(k.x() >> 4, k.z() >> 4) ? null : w.getBlockAt(k.x(), k.y(), k.z());
    }

    void setVoiceChatAvailable(boolean available) { voiceChat = available; }

    boolean voiceChatAvailable() { return voiceChat; }

    // ---- Menue ----

    void open(Player player, Block block) {
        BlockRegistry.Key key = BlockRegistry.Key.of(block);
        NetworkRegistry.Net net = networks.networkOf(key);
        boolean connected = net != null && net.members.values().stream()
                .anyMatch(t -> t == PeakBlockType.SPEAKER || t == PeakBlockType.JUKEBOX);
        Net.send(plugin, player, new Packet.OpenMicMenu(block.getX(), block.getY(), block.getZ(), store.get(key), connected, voiceChat));
    }

    void onUpdate(Player player, Packet.MicUpdate update) {
        Block block = access.validated(player, update.x(), update.y(), update.z(), PeakBlockType.MICROPHONE);
        if (block != null) store.set(BlockRegistry.Key.of(block), update.settings().sanitized());
    }

    MicSettings settingsOf(Block block) { return store.get(BlockRegistry.Key.of(block)); }

    void put(Block block, MicSettings settings) { store.set(BlockRegistry.Key.of(block), settings); }

    void remove(Block block) {
        BlockRegistry.Key key = BlockRegistry.Key.of(block);
        store.remove(block);
        lastSpoke.remove(key);
        List<BlockRegistry.Key> jukeboxes = ducked.remove(key);
        if (jukeboxes != null) playback.setDuckGain(jukeboxes, 100, 0);
    }

    // ---- Zuordnung Spieler -> Mikrofone (Hauptthread) ----

    /** Alle 5 Ticks: fuer jeden Spieler die angesprochenen Mikrofone neu bestimmen und das Ducking nachfuehren. */
    void tick() {
        List<BlockRegistry.Key> mics = registry.keysOf(PeakBlockType.MICROPHONE);
        if (!voiceChat || mics.isEmpty()) {
            targets.clear();
            restoreAll();
            return;
        }
        Map<BlockRegistry.Key, Target> built = new java.util.HashMap<>();
        for (BlockRegistry.Key key : mics) {
            MicSettings settings = store.get(key);
            if (!settings.enabled()) continue;
            if (settings.redstone()) {
                Block mic = blockOf(key);
                if (mic == null || mic.getBlockPower() <= 0) continue; // Redstone-Steuerung: nur mit Signal aktiv
            }
            Target t = build(key, settings);
            if (t != null) built.put(key, t);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            List<Target> hit = new ArrayList<>();
            for (Target t : built.values()) {
                if (!t.world().equals(p.getWorld().getUID())) continue;
                double dx = p.getX() - (t.mic().x() + 0.5), dy = p.getY() - t.mic().y(), dz = p.getZ() - (t.mic().z() + 0.5);
                if (dx * dx + dy * dy + dz * dz <= (double) t.settings().range() * t.settings().range()) hit.add(t);
            }
            if (hit.isEmpty()) targets.remove(p.getUniqueId());
            else targets.put(p.getUniqueId(), List.copyOf(hit));
        }
        updateDucking(built);
    }

    private Target build(BlockRegistry.Key key, MicSettings settings) {
        NetworkRegistry.Net net = networks.networkOf(key);
        if (net == null) return null;
        List<Output> outputs = new ArrayList<>();
        List<SpeakerService.Speaker> netSpeakers = speakers.speakersOf(net);
        if (!netSpeakers.isEmpty()) {
            for (SpeakerService.Speaker sp : netSpeakers) {
                outputs.add(new Output(sp.key(), sp.key().x() + 0.5, sp.key().y() + 0.5, sp.key().z() + 0.5,
                        jukeboxSettings.get(sp.jukebox()).range(), settings.volume() * sp.settings().volume() / 100));
            }
        } else {
            for (BlockRegistry.Key j : net.jukeboxes()) {
                outputs.add(new Output(j, j.x() + 0.5, j.y() + 0.5, j.z() + 0.5, jukeboxSettings.get(j).range(), settings.volume()));
            }
        }
        return outputs.isEmpty() ? null : new Target(key, key.world(), settings, List.copyOf(outputs));
    }

    List<Target> targetsOf(UUID player) {
        List<Target> t = targets.get(player);
        return t == null ? List.of() : t;
    }

    /** Wird von {@link VoiceChatHook} (beliebiger Thread) aufgerufen, wenn jemand ins Mikrofon spricht. */
    void spoke(BlockRegistry.Key mic) {
        lastSpoke.put(mic, System.currentTimeMillis());
    }

    private void updateDucking(Map<BlockRegistry.Key, Target> built) {
        long now = System.currentTimeMillis();
        Set<BlockRegistry.Key> active = new HashSet<>();
        for (Target t : built.values()) {
            if (!t.settings().ducking()) continue;
            Long spoke = lastSpoke.get(t.mic());
            if (spoke == null || now - spoke > SPEAKING_MS) continue;
            NetworkRegistry.Net net = networks.networkOf(t.mic());
            if (net == null) continue;
            List<BlockRegistry.Key> jukeboxes = net.jukeboxes();
            active.add(t.mic());
            if (!jukeboxes.equals(ducked.get(t.mic()))) {
                ducked.put(t.mic(), jukeboxes);
            }
            playback.setDuckGain(jukeboxes, t.settings().duckPercent(), DUCK_FADE_MS);
        }
        ducked.entrySet().removeIf(e -> {
            if (active.contains(e.getKey())) return false;
            playback.setDuckGain(e.getValue(), 100, RESTORE_FADE_MS);
            return true;
        });
    }

    private void restoreAll() {
        ducked.entrySet().removeIf(e -> {
            playback.setDuckGain(e.getValue(), 100, 0);
            return true;
        });
    }

    void shutdown() {
        restoreAll();
    }

    /** Die Welt zu einem Schluessel (fuer den Voice-Chat-Hook). */
    static World worldOf(BlockRegistry.Key key) { return Bukkit.getWorld(key.world()); }
}