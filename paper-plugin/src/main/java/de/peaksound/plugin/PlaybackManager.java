package de.peaksound.plugin;

import de.peaksound.audio.Analysis;
import de.peaksound.protocol.ChannelLayout;
import de.peaksound.protocol.JukeboxSettings;
import de.peaksound.protocol.Limits;
import de.peaksound.protocol.LoopMode;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PlaylistLogic;
import de.peaksound.protocol.SyncMath;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Verwaltet, welche Jukebox welche Platte spielt, und meldet Spielern in der Naehe Start, Aenderungen und Ende.
 * Die Wiedergabe selbst (Audio) passiert beim Client; der Server liefert nur Song, Startzeit und Position.
 * Zusaetzlich: "Jetzt laeuft"-Anzeige und Noten-Partikel im Takt (aus der vorberechneten Analyse).
 */
final class PlaybackManager {
    /** Zustand einer Jukebox mit eingelegter Platte. */
    static final class State {
        final ItemStack record;
        final String hash;
        final String title;
        final String artist;
        final int durationMs;
        long startedAt;
        boolean playing;
        /** Ueberblendung: Das Ausblenden am Songende hat schon begonnen. */
        boolean fadedOut;
        /** Position (ms), an der pausiert wurde; -1 = nicht pausiert. */
        long pausedAtMs = -1;
        /** Songposition (ms), bis zu der Taktschlaege schon verarbeitet wurden. */
        long beatCursorMs;
        /** true: Der Song kommt aus der Playlist (Hopper/Kiste); record ist dann nur eine Kopie und wird nie ausgegeben. */
        final boolean fromPlaylist;
        int playlistIndex;
        /** Wunsch-Songs: Playlist-Position, an der es nach dem Wunsch weitergeht (-1 = kein Wunsch-Song). */
        int resumeIndex = -1;
        /** Bereits gespielte Playlist-Eintraege dieses Durchlaufs (fuer Shuffle). */
        final Set<Integer> played = new HashSet<>();

        State(ItemStack record, String hash, String title, String artist, int durationMs, long startedAt, boolean playing) {
            this(record, hash, title, artist, durationMs, startedAt, playing, false, -1);
        }

        State(ItemStack record, String hash, String title, String artist, int durationMs, long startedAt, boolean playing,
              boolean fromPlaylist, int playlistIndex) {
            this.record = record;
            this.hash = hash;
            this.title = title;
            this.artist = artist;
            this.durationMs = durationMs;
            this.startedAt = startedAt;
            this.playing = playing;
            this.beatCursorMs = Math.max(0, System.currentTimeMillis() - startedAt);
            this.fromPlaylist = fromPlaylist;
            this.playlistIndex = playlistIndex;
        }
    }

    /** Lautstaerke-Faktor (Prozent) je Jukebox, gesetzt von DJ-Pulten. Fehlt ein Eintrag, gilt 100. */
    private final Map<BlockRegistry.Key, Integer> deckGain = new ConcurrentHashMap<>();
    /** Bis wann (ms) eine Schallquelle gerade ueberblendet wird. */
    private final Map<BlockRegistry.Key, Long> fadeUntil = new ConcurrentHashMap<>();
    /** Wann ein Song an dieser Jukebox zuletzt zu Ende ging (fuer den Redstone-Ausgang Song-Ende). */
    private final Map<BlockRegistry.Key, Long> lastEnded = new ConcurrentHashMap<>();
    /** Laufende Ueberblendungen: von, nach (Prozent), Startzeit und Dauer, damit Neuankoemmlinge mittendrin einsteigen. */
    private record Fade(int from, int to, long startMs, int durationMs) {
        double current(long now) { return de.peaksound.protocol.DjMath.rampPercent(from, to, now - startMs, durationMs); }
    }

    private final Map<BlockRegistry.Key, Fade> fades = new ConcurrentHashMap<>();

    /** Was der Client einer Schallquelle zuletzt gemeldet bekam: Song@Start#Kanal (erkennt Songwechsel) sowie Reichweite und Lautstaerke. */
    private record Heard(String signature, int range, int volume) {}

    /** Eine Schallquelle: Jukebox (Kanal 0, Mono-Mix) oder Lautsprecher (Kanal 1-12) mit Reichweite und Lautstaerke. */
    record Emitter(BlockRegistry.Key key, State state, int channel, int range, int volume, int kind, int delayMs, int reverb) {
        Emitter(BlockRegistry.Key key, State state, int channel, int range, int volume, int kind) {
            this(key, state, channel, range, volume, kind, 0, 0);
        }
    }

    /** Ergebnis von {@link #effective()}: Jukeboxen mit ihrem Song und die tatsaechlichen Schallquellen. */
    record Effective(Map<BlockRegistry.Key, State> jukeboxes, Map<BlockRegistry.Key, Emitter> emitters) {}

    /** Spieler hoeren eine Jukebox schon ab dieser Entfernung (Reichweite + Vorlauf zum Laden). */
    private static final int PRELOAD_MARGIN = 8;
    /** Erst ab dieser zusaetzlichen Entfernung wird der Client wieder entlastet. */
    private static final int RELEASE_MARGIN = 24;

    private final PeakSoundPlugin plugin;
    private final NetworkHandler network;
    private final JukeboxSettingsStore settings;
    private final AnalysisStore analysis;
    /** Zaehlt jeden gestarteten Song (Beliebtheit in der Bibliothek). */
    private java.util.function.Consumer<String> playCounter;
    private RadioService radio;
    private QueueService queue;
    private final PlaylistService playlist;
    private final NetworkRegistry networks;
    private final EqualizerService equalizer;
    private final SpeakerService speakers;
    private final Map<BlockRegistry.Key, State> states = new ConcurrentHashMap<>();
    /** Pro Spieler: welche Schallquellen er gerade vom Server gemeldet bekam. */
    private final Map<UUID, Map<BlockRegistry.Key, Heard>> heard = new ConcurrentHashMap<>();
    /** Fuer welche Jukeboxen der Spieler "Jetzt laeuft" schon gesehen hat (bis er wieder weit weg ist). */
    private final Map<UUID, Set<BlockRegistry.Key>> announced = new ConcurrentHashMap<>();

    PlaybackManager(PeakSoundPlugin plugin, NetworkHandler network, JukeboxSettingsStore settings, AnalysisStore analysis,
                    PlaylistService playlist, NetworkRegistry networks, EqualizerService equalizer, SpeakerService speakers) {
        this.plugin = plugin;
        this.speakers = speakers;
        this.playlist = playlist;
        this.networks = networks;
        this.equalizer = equalizer;
        this.network = network;
        this.settings = settings;
        this.analysis = analysis;
    }

    void setRadio(RadioService radio) { this.radio = radio; }

    void setQueue(QueueService queue) { this.queue = queue; }

    void setPlayCounter(java.util.function.Consumer<String> counter) { this.playCounter = counter; }

    /** Lautstaerke-Faktor (Prozent) je Jukebox, gesetzt vom Mikrofon-Ducking (wirkt zusaetzlich zum DJ-Faktor). */
    private final Map<BlockRegistry.Key, Integer> duckGain = new ConcurrentHashMap<>();

    /** Liegt am Block ein Redstone-Signal an? (nur Hauptthread) */
    private static boolean powered(BlockRegistry.Key key) {
        World world = Bukkit.getWorld(key.world());
        return world != null && world.isChunkLoaded(key.x() >> 4, key.z() >> 4)
                && world.getBlockAt(key.x(), key.y(), key.z()).getBlockPower() > 0;
    }

    /** Lautstaerke-Faktor (Prozent) der Ueberblendung zwischen Playlist-Songs (ausblenden am Ende, einblenden am Anfang). */
    private final Map<BlockRegistry.Key, Integer> crossGain = new ConcurrentHashMap<>();

    private int gain(BlockRegistry.Key jukebox) {
        return deckGain.getOrDefault(jukebox, 100) * duckGain.getOrDefault(jukebox, 100) / 100 * crossGain.getOrDefault(jukebox, 100) / 100;
    }

    private List<BlockRegistry.Key> netJukeboxes(BlockRegistry.Key key) {
        NetworkRegistry.Net net = networks.networkOf(key);
        return net == null ? List.of(key) : net.jukeboxes();
    }

    /** Spielt diese Jukebox (oder eine im selben Netz) gerade einen Playlist-Song? Nur dann darf die Ueberblendung wirken. */
    private boolean crossActive(BlockRegistry.Key key) {
        State s = states.get(key);
        if (s != null && s.playing && s.fromPlaylist) return true;
        NetworkRegistry.Net net = networks.networkOf(key);
        if (net == null) return false;
        for (BlockRegistry.Key j : net.jukeboxes()) {
            State sj = states.get(j);
            if (sj != null && sj.playing && sj.fromPlaylist) return true;
        }
        return false;
    }

    /** Blendet gegen Songende aus (Ueberblendung) und raeumt auf, wenn kein Playlist-Song mehr laeuft. */
    private void crossfadeStep(long now) {
        for (Map.Entry<BlockRegistry.Key, State> e : states.entrySet()) {
            State s = e.getValue();
            int fadeMs = Math.min(settings.get(e.getKey()).crossfade() * 1000, s.durationMs / 2);
            if (!s.playing || !s.fromPlaylist || fadeMs <= 0 || s.fadedOut) continue;
            long remaining = s.durationMs - (now - s.startedAt);
            if (remaining > 0 && remaining <= fadeMs) {
                s.fadedOut = true;
                setGain(crossGain, netJukeboxes(e.getKey()), 0, (int) remaining);
            }
        }
        // Ohne laufenden Playlist-Song darf kein Faktor haengenbleiben (sonst waere die naechste eingelegte Platte stumm)
        crossGain.keySet().removeIf(k -> !crossActive(k));
    }

    /** Der neue Playlist-Song beginnt nach einem Ausblenden: einblenden. */
    private void crossfadeIn(BlockRegistry.Key key, int songDurationMs) {
        if (crossGain.getOrDefault(key, 100) >= 100) return;
        int fadeMs = Math.min(settings.get(key).crossfade() * 1000, songDurationMs / 2);
        setGain(crossGain, netJukeboxes(key), 100, Math.max(0, fadeMs));
    }

    int deckGainOf(BlockRegistry.Key jukebox) {
        return deckGain.getOrDefault(jukebox, 100);
    }

    /**
     * Setzt den DJ-Faktor der Jukeboxen eines Decks. Bei {@code durationMs > 0} ueberblenden alle Spieler, die die
     * betroffenen Schallquellen hoeren, in dieser Zeit (der Client rampt selbst, alle starten mit demselben Paket).
     */
    void setDeckGain(Collection<BlockRegistry.Key> jukeboxes, int percent, int durationMs) {
        setGain(deckGain, jukeboxes, percent, durationMs);
    }

    /** Mikrofon-Ducking: Musik der Jukeboxen leiser stellen (100 = normal), mit kurzer Ueberblendung. */
    void setDuckGain(Collection<BlockRegistry.Key> jukeboxes, int percent, int durationMs) {
        setGain(duckGain, jukeboxes, percent, durationMs);
    }

    private void setGain(Map<BlockRegistry.Key, Integer> target, Collection<BlockRegistry.Key> jukeboxes, int percent, int durationMs) {
        int value = Math.max(0, Math.min(100, percent));
        boolean changed = false;
        for (BlockRegistry.Key k : jukeboxes) changed |= target.getOrDefault(k, 100) != value;
        if (!changed) return;
        Map<BlockRegistry.Key, Emitter> before = effective().emitters();
        for (BlockRegistry.Key k : jukeboxes) {
            if (value == 100) target.remove(k);
            else target.put(k, value);
        }
        if (durationMs <= 0) {
            Bukkit.getScheduler().runTask(plugin, this::tick); // sofort per PlaybackUpdate
            return;
        }
        long until = System.currentTimeMillis() + durationMs;
        Map<BlockRegistry.Key, Emitter> after = effective().emitters();
        for (Emitter now : after.values()) {
            Emitter old = before.get(now.key());
            if (old == null || old.volume() == now.volume()) continue;
            Fade running = fades.get(now.key());
            int from = running != null && running.startMs() + running.durationMs() > System.currentTimeMillis()
                    ? (int) Math.round(running.current(System.currentTimeMillis())) : old.volume();
            fades.put(now.key(), new Fade(from, now.volume(), System.currentTimeMillis(), durationMs));
            fadeUntil.put(now.key(), until);
            for (Player p : Bukkit.getOnlinePlayers()) {
                Map<BlockRegistry.Key, Heard> hearing = heard.get(p.getUniqueId());
                Heard known = hearing == null ? null : hearing.get(now.key());
                if (known == null) continue;
                Net.send(plugin, p, new Packet.PlaybackFade(now.key().x(), now.key().y(), now.key().z(), now.volume(), durationMs));
                hearing.put(now.key(), new Heard(known.signature(), known.range(), now.volume()));
            }
        }
    }

    boolean hasRecord(Block block) {
        return states.containsKey(BlockRegistry.Key.of(block));
    }

    Optional<State> state(Block block) {
        return Optional.ofNullable(states.get(BlockRegistry.Key.of(block)));
    }

    /** Legt eine Platte ein und startet sie. */
    void insert(Block block, ItemStack record, Song song) {
        ItemStack single = record.clone();
        single.setAmount(1);
        states.put(BlockRegistry.Key.of(block),
                new State(single, song.hash(), song.title(), song.artist(), song.durationMs(), System.currentTimeMillis(), true));
        analysis.peek(song); // Analyse vorladen, damit der erste Takt schon Partikel ausloest
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
        // Platten aus der Playlist bleiben im Hopper/in der Kiste: nur Kopien, nie ausgeben (sonst Duplikate)
        return state.fromPlaylist ? Optional.empty() : Optional.of(state.record);
    }

    // ---- Playlist und Wiederholung ----

    /** Ein Song ist zu Ende: Loop-Modus und Playlist entscheiden, was als Naechstes kommt. */
    private void onEnded(BlockRegistry.Key key, State ended) {
        lastEnded.put(key, System.currentTimeMillis());
        stopFor(key);
        World world = Bukkit.getWorld(key.world());
        if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) return; // nichts zu tun, solange nicht geladen
        Block block = world.getBlockAt(key.x(), key.y(), key.z());
        if (queue != null && queue.hasQueued(key) && ended.fromPlaylist) {
            playNext(block, ended, false); // Wuensche gehen vor
        } else if (ended.fromPlaylist) {
            playNext(block, ended, false);
        } else if (settings.get(key).loop() == LoopMode.ONE) {
            // Einzelne eingelegte Platte wiederholen
            states.put(key, new State(ended.record, ended.hash, ended.title, ended.artist, ended.durationMs,
                    System.currentTimeMillis(), true));
            Bukkit.getScheduler().runTask(plugin, this::tick);
        }
    }

    /** Index des laufenden Songs in der Playlist, oder -1. Folgt dem Song, auch wenn die Reihenfolge geaendert wurde. */
    int currentIndex(Block block, List<PlaylistService.Track> tracks) {
        State s = states.get(BlockRegistry.Key.of(block));
        if (s == null || !s.fromPlaylist) return -1;
        return locate(tracks, s);
    }

    private static int locate(List<PlaylistService.Track> tracks, State s) {
        int i = s.playlistIndex;
        if (i >= 0 && i < tracks.size() && tracks.get(i).hash().equals(s.hash)) return i;
        for (int k = 0; k < tracks.size(); k++) if (tracks.get(k).hash().equals(s.hash)) return k;
        return -1;
    }

    /**
     * Naechsten spielbaren Eintrag starten. Nicht spielbare Songs (fehlen oder gesperrt) werden uebersprungen.
     *
     * @param skip true, wenn der Spieler "Skip" gedrueckt hat (dann wird auch bei "Ein Song wiederholen" weitergeschaltet)
     * @return false, wenn nichts mehr zu spielen ist (die Wiedergabe endet)
     */
    boolean playNext(Block block, State current, boolean skip) {
        BlockRegistry.Key key = BlockRegistry.Key.of(block);
        List<PlaylistService.Track> tracks = playlist.tracks(block);
        JukeboxSettings cfg = settings.get(key);
        Set<Integer> played = current != null ? current.played : new HashSet<>();
        if (queue != null && (current == null || current.fromPlaylist)) { // Wuensche zuerst; die Playlist laeuft danach an derselben Stelle weiter
            Song wished = queue.poll(key);
            if (wished != null) {
                startWished(block, current, tracks, wished);
                return true;
            }
        }
        int index = current != null ? (current.resumeIndex >= 0 ? current.resumeIndex : locate(tracks, current)) : -1;
        for (int tries = 0; tries <= tracks.size(); tries++) {
            index = tries == 0 && !skip
                    ? PlaylistLogic.next(tracks.size(), index, cfg.loop(), cfg.shuffle(), played, ThreadLocalRandom.current())
                    : PlaylistLogic.skip(tracks.size(), index, cfg.loop(), cfg.shuffle(), played, ThreadLocalRandom.current());
            if (index < 0) break;
            if (tracks.get(index).playable()) {
                startTrack(block, tracks, index, played);
                return true;
            }
        }
        if (states.remove(key) != null) save();
        return false;
    }

    private void startWished(Block block, State current, List<PlaylistService.Track> tracks, Song song) {
        BlockRegistry.Key key = BlockRegistry.Key.of(block);
        State next = new State(queue.recordFor(song), song.hash(), song.title(), song.artist(), song.durationMs(),
                System.currentTimeMillis(), true, true, -1);
        if (current != null) {
            next.resumeIndex = current.resumeIndex >= 0 ? current.resumeIndex : locate(tracks, current);
            next.played.addAll(current.played);
        }
        if (states.containsKey(key)) stopFor(key);
        analysis.peek(song);
        if (playCounter != null) playCounter.accept(song.hash());
        states.put(key, next);
        crossfadeIn(key, song.durationMs());
        save();
        Bukkit.getScheduler().runTask(plugin, this::tick);
    }

    /** Einen bestimmten Playlist-Eintrag starten (Auswahl im Menue). */
    boolean playIndex(Block block, int index) {
        List<PlaylistService.Track> tracks = playlist.tracks(block);
        if (index < 0 || index >= tracks.size() || !tracks.get(index).playable()) return false;
        startTrack(block, tracks, index, new HashSet<>());
        return true;
    }

    private void startTrack(Block block, List<PlaylistService.Track> tracks, int index, Set<Integer> played) {
        PlaylistService.Track track = tracks.get(index);
        Song song = track.song();
        BlockRegistry.Key key = BlockRegistry.Key.of(block);
        ItemStack copy = track.inventory().getItem(track.slot()).clone();
        copy.setAmount(1);
        if (states.containsKey(key)) stopFor(key); // Clients beginnen den neuen Song frisch
        State next = new State(copy, song.hash(), song.title(), song.artist(), song.durationMs(),
                System.currentTimeMillis(), true, true, index);
        next.played.addAll(played);
        analysis.peek(song);
        if (playCounter != null) playCounter.accept(song.hash());
        states.put(key, next);
        crossfadeIn(key, song.durationMs());
        save();
        Bukkit.getScheduler().runTask(plugin, this::tick);
    }

    long lastEnded(BlockRegistry.Key key) {
        return lastEnded.getOrDefault(key, 0L);
    }

    boolean isPlayingKey(BlockRegistry.Key key) {
        State s = states.get(key);
        return s != null && s.playing;
    }

    boolean isPlaying(Block block) {
        State s = states.get(BlockRegistry.Key.of(block));
        return s != null && s.playing;
    }

    boolean isPaused(Block block) {
        State s = states.get(BlockRegistry.Key.of(block));
        return s != null && !s.playing && s.pausedAtMs >= 0;
    }

    /** Pausiert den laufenden Song; die Position bleibt erhalten. */
    void pause(Block block) {
        BlockRegistry.Key key = BlockRegistry.Key.of(block);
        State s = states.get(key);
        if (s == null || !s.playing) return;
        s.pausedAtMs = Math.max(0, System.currentTimeMillis() - s.startedAt);
        s.playing = false;
        stopFor(key);
        save();
    }

    /** Setzt einen pausierten Song fort; ein angehaltener (nicht pausierter) beginnt von vorn. */
    void resume(Block block) {
        BlockRegistry.Key key = BlockRegistry.Key.of(block);
        State s = states.get(key);
        if (s == null || s.playing) return;
        long now = System.currentTimeMillis();
        s.startedAt = now - Math.max(0, s.pausedAtMs);
        s.pausedAtMs = -1;
        s.playing = true;
        save();
        Bukkit.getScheduler().runTask(plugin, this::tick);
    }

    void togglePause(Block block) {
        if (isPlaying(block)) pause(block);
        else resume(block);
    }

    /** Voriger Song der Playlist (springt vom ersten zum letzten); eine eingelegte Platte beginnt von vorn. */
    boolean previous(Block block) {
        BlockRegistry.Key key = BlockRegistry.Key.of(block);
        State s = states.get(key);
        if (s == null) return false;
        if (!s.fromPlaylist) {
            s.startedAt = System.currentTimeMillis();
            s.pausedAtMs = -1;
            s.playing = true;
            stopFor(key); // Clients beginnen frisch
            save();
            Bukkit.getScheduler().runTask(plugin, this::tick);
            return true;
        }
        List<PlaylistService.Track> tracks = playlist.tracks(block);
        int index = locate(tracks, s);
        for (int tries = 0; tries < tracks.size(); tries++) {
            index = index <= 0 ? tracks.size() - 1 : index - 1;
            if (tracks.get(index).playable()) {
                startTrack(block, tracks, index, new HashSet<>());
                return true;
            }
        }
        return false;
    }

    /** Wiedergabe anhalten. Playlist-Songs enden ganz; eine eingelegte Platte bleibt in der Jukebox. */
    void stopPlayback(Block block) {
        BlockRegistry.Key key = BlockRegistry.Key.of(block);
        State s = states.get(key);
        if (s == null) return;
        if (s.fromPlaylist) states.remove(key);
        else s.playing = false;
        stopFor(key);
        save();
    }

    /** Verschiebt einen Eintrag um eine Position (tauscht die Platten in ihren Slots). */
    boolean moveTrack(Block block, int index, boolean up) {
        List<PlaylistService.Track> tracks = playlist.tracks(block);
        int target = PlaylistLogic.swapTarget(index, up, tracks.size());
        if (target < 0) return false;
        State s = states.get(BlockRegistry.Key.of(block));
        int current = s != null && s.fromPlaylist ? locate(tracks, s) : -1;
        playlist.swap(tracks.get(index), tracks.get(target));
        if (s != null && s.fromPlaylist && current >= 0) {
            s.playlistIndex = PlaylistLogic.followSwap(current, index, target);
            boolean a = s.played.remove(index);
            boolean b = s.played.remove(target);
            if (a) s.played.add(target);
            if (b) s.played.add(index);
        }
        return true;
    }

    /** Ein Song wurde geloescht: alles, was ihn gerade spielt, stoppt (die Platte bleibt, wo sie ist). */
    void onSongRemoved(String hash) {
        boolean changed = false;
        for (Map.Entry<BlockRegistry.Key, State> e : states.entrySet()) {
            State s = e.getValue();
            if (!s.hash.equals(hash)) continue;
            if (s.fromPlaylist) states.remove(e.getKey());
            else s.playing = false;
            stopFor(e.getKey());
            changed = true;
        }
        if (changed) save();
    }

    /** Wiederholmodus einer Jukebox setzen (z. B. fuer Dauer-Tests). */
    void setLoop(Block block, LoopMode loop) {
        applySettings(block, settings.get(block).withLoop(loop));
    }

    /** Neue Einstellungen uebernehmen; laufende Wiedergabe wird sofort nachgezogen (naechster Abgleich). */
    void applySettings(Block block, JukeboxSettings updated) {
        settings.set(block, updated);
        Bukkit.getScheduler().runTask(plugin, this::tick);
    }

    private void stopFor(BlockRegistry.Key key) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Map<BlockRegistry.Key, Heard> hearing = heard.get(p.getUniqueId());
            if (hearing != null && hearing.remove(key) != null) {
                Net.send(plugin, p, new Packet.PlaybackStop(key.x(), key.y(), key.z()));
            }
            Set<BlockRegistry.Key> shown = announced.get(p.getUniqueId());
            if (shown != null) shown.remove(key);
        }
    }

    /**
     * Was ist wo zu hoeren? Jukeboxen eines Redstone-Netzes spielen den Song des "Anfuehrers" (der am laengsten
     * laufende), jede mit eigener Reichweite und Lautstaerke; so vergroessert ein Netz die Reichweite, und alle
     * hoeren dasselbe zur selben Zeit. Haengen Lautsprecher im Netz, schweigen die Jukeboxen: Dann ist jeder
     * Lautsprecher eine eigene Schallquelle fuer seinen Kanal (Lautsprecher-Lautstaerke mal Jukebox-Lautstaerke,
     * Reichweite der naechsten Jukebox).
     */
    Effective effective() {
        Map<BlockRegistry.Key, State> jukeboxes = new HashMap<>();
        Map<BlockRegistry.Key, Emitter> emitters = new HashMap<>();
        Set<NetworkRegistry.Net> seen = new HashSet<>();
        for (Map.Entry<BlockRegistry.Key, State> e : states.entrySet()) {
            if (!e.getValue().playing) continue;
            NetworkRegistry.Net net = networks.networkOf(e.getKey());
            if (net == null) { // Chunk nicht geladen oder Block weg: nur die eigene Jukebox
                jukeboxes.putIfAbsent(e.getKey(), e.getValue());
                JukeboxSettings cfg = settings.get(e.getKey());
                emitters.putIfAbsent(e.getKey(), new Emitter(e.getKey(), e.getValue(), 0, cfg.range(), cfg.volume() * gain(e.getKey()) / 100, 0));
                continue;
            }
            if (!seen.add(net)) continue;
            List<SyncMath.Candidate<BlockRegistry.Key>> candidates = new ArrayList<>();
            List<BlockRegistry.Key> members = net.jukeboxes();
            for (BlockRegistry.Key member : members) {
                State s = states.get(member);
                if (s != null && s.playing) candidates.add(new SyncMath.Candidate<>(member, s.startedAt));
            }
            State leader = SyncMath.pickLeader(candidates).map(c -> states.get(c.key())).orElse(null);
            if (leader == null) continue;
            for (BlockRegistry.Key member : members) jukeboxes.put(member, leader);

            List<SpeakerService.Speaker> netSpeakers = speakers.speakersOf(net).stream().filter(sp -> sp.number() > 0).toList();
            boolean hasSpeakers = !netSpeakers.isEmpty();
            // Lautsprecher mit Redstone-Steuerung spielen nur, solange ein Signal an ihnen anliegt
            netSpeakers = netSpeakers.stream().filter(sp -> !sp.settings().redstone() || powered(sp.key())).toList();
            // Stereo-/Mono-Songs bleiben Stereo/Mono, solange die Jukebox nicht auf Upmix steht (dann alle Lautsprecher)
            int sourceChannels = songChannels(leader.hash);
            List<SpeakerService.Speaker> playing = netSpeakers.stream()
                    .filter(sp -> settings.get(sp.jukebox()).upmix() || ChannelLayout.playsWithoutUpmix(sourceChannels, sp.settings().channel()))
                    .toList();
            if (!playing.isEmpty()) netSpeakers = playing; // gibt es keinen passenden Lautsprecher, spielen alle (besser als Stille)
            if (netSpeakers.isEmpty() && hasSpeakers) {
                continue; // alle Lautsprecher per Redstone stumm geschaltet: nichts spielt
            }
            if (netSpeakers.isEmpty()) {
                for (BlockRegistry.Key member : members) {
                    JukeboxSettings cfg = settings.get(member);
                    emitters.put(member, new Emitter(member, leader, 0, cfg.range(), cfg.volume() * gain(member) / 100, 0));
                }
            } else {
                for (SpeakerService.Speaker sp : netSpeakers) {
                    JukeboxSettings cfg = settings.get(sp.jukebox());
                    int volume = cfg.volume() * sp.settings().volume() / 100 * gain(sp.jukebox()) / 100;
                    emitters.put(sp.key(), new Emitter(sp.key(), leader, sp.settings().channel().ordinal() + 1, cfg.range(), volume,
                            sp.settings().kind().ordinal(), sp.settings().delayMs(), sp.settings().reverb().ordinal()));
                }
            }
        }
        if (radio != null) emitters.putAll(radio.emittersFor(jukeboxes)); // Empfaenger spielen den Song ihres Senders
        return new Effective(jukeboxes, emitters);
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
                onEnded(e.getKey(), s);
            }
        }
        if (changed) save();
        crossfadeStep(now);

        Effective effective = effective();
        for (Player p : Bukkit.getOnlinePlayers()) {
            announce(p, effective.jukeboxes());
            if (!network.hasMod(p)) continue;
            Map<BlockRegistry.Key, Heard> hearing = heard.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
            UUID world = p.getWorld().getUID();
            Location loc = p.getLocation();
            for (Emitter em : effective.emitters().values()) {
                BlockRegistry.Key key = em.key();
                State s = em.state();
                double d2 = distanceSquared(key, world, loc);
                String signature = s.hash + "@" + s.startedAt + "#" + em.channel() + "/" + em.kind() + "/" + em.delayMs() + "/" + em.reverb();
                Heard known = hearing.get(key);
                if (known == null) {
                    if (d2 <= sq(em.range() + PRELOAD_MARGIN)) {
                        sendStart(p, em, now);
                        hearing.put(key, new Heard(signature, em.range(), em.volume()));
                    }
                } else if (d2 > sq(em.range() + RELEASE_MARGIN)) {
                    Net.send(plugin, p, new Packet.PlaybackStop(key.x(), key.y(), key.z()));
                    hearing.remove(key);
                } else if (!known.signature().equals(signature)) {
                    sendStart(p, em, now); // Anfuehrer oder Kanal hat gewechselt: neu beginnen
                    hearing.put(key, new Heard(signature, em.range(), em.volume()));
                } else if (known.range() != em.range() || known.volume() != em.volume()) {
                    // Waehrend einer Ueberblendung rampt der Client selbst; ein Update wuerde die Rampe abbrechen
                    if (fadeUntil.getOrDefault(key, 0L) <= now || known.range() != em.range()) {
                        Net.send(plugin, p, new Packet.PlaybackUpdate(key.x(), key.y(), key.z(), em.range(), em.volume()));
                    }
                    hearing.put(key, new Heard(signature, em.range(), em.volume()));
                }
            }
            // Schallquellen, die nicht mehr spielen (Anfuehrer beendet, Platte entnommen): Client entlasten
            hearing.entrySet().removeIf(en -> {
                if (effective.emitters().containsKey(en.getKey())) return false;
                Net.send(plugin, p, new Packet.PlaybackStop(en.getKey().x(), en.getKey().y(), en.getKey().z()));
                return true;
            });
        }
    }

    private void sendStart(Player p, Emitter em, long now) {
        State s = em.state();
        Fade fade = fades.get(em.key());
        boolean fading = fade != null && fade.startMs() + fade.durationMs() > now;
        if (fade != null && !fading) fades.remove(em.key());
        int offset = (int) Math.min(Integer.MAX_VALUE, now - s.startedAt);
        Net.send(plugin, p, new Packet.SongInfo(s.hash, cutText(s.title), cutText(s.artist), s.startedAt, s.durationMs)); // Anzeige am Bildschirm
        Net.send(plugin, p, new Packet.PlaybackStart(em.key().x(), em.key().y(), em.key().z(), s.hash, offset, now,
                s.durationMs, songChannels(s.hash), em.range(), fading ? (int) Math.round(fade.current(now)) : em.volume(), em.channel(), em.kind(),
                em.delayMs(), em.reverb()));
        if (fading) { // mitten in einer Ueberblendung: von hier aus zum Ziel weiterrampen
            Net.send(plugin, p, new Packet.PlaybackFade(em.key().x(), em.key().y(), em.key().z(), fade.to(),
                    (int) (fade.startMs() + fade.durationMs() - now)));
        }
    }

    private static String cutText(String s) { return s.length() > Limits.MAX_TEXT ? s.substring(0, Limits.MAX_TEXT) : s; }

    /** Startpaket fuer eine Quelle an beliebiger (auch Marker-)Position, z. B. das Taschenradio. */
    Packet.PlaybackStart startPacket(State s, int x, int y, int z, int range, int volume, long now) {
        int offset = (int) Math.min(Integer.MAX_VALUE, now - s.startedAt);
        return new Packet.PlaybackStart(x, y, z, s.hash, offset, now, s.durationMs, songChannels(s.hash), range, volume, 0, 0);
    }

    /** "Jetzt laeuft"-Anzeige (Actionbar) fuer alle Spieler, auch ohne Mod. */
    private void announce(Player p, Map<BlockRegistry.Key, State> jukeboxes) {
        Set<BlockRegistry.Key> shown = announced.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        UUID world = p.getWorld().getUID();
        Location loc = p.getLocation();
        for (Map.Entry<BlockRegistry.Key, State> e : jukeboxes.entrySet()) {
            updateAnnouncement(p, e.getKey(), e.getValue(), settings.get(e.getKey()), distanceSquared(e.getKey(), world, loc), shown);
        }
    }
    private void updateAnnouncement(Player p, BlockRegistry.Key key, State s, JukeboxSettings cfg, double d2,
                                    Set<BlockRegistry.Key> shown) {
        if (!cfg.showNowPlaying()) return;
        if (d2 <= sq(cfg.range()) && shown.add(key)) {
            String artist = s.artist.isBlank() ? "" : " – " + s.artist;
            boolean de = p.locale().getLanguage().equals("de");
            p.sendActionBar(Component.text("♪ ", NamedTextColor.GOLD)
                    .append(Component.text(de ? "Jetzt läuft: " : "Now playing: ", NamedTextColor.GRAY))
                    .append(Component.text(s.title + artist, NamedTextColor.WHITE)));
        } else if (d2 > sq(cfg.range() + 4)) {
            shown.remove(key);
        }
    }

    /**
     * Alle 2 Ticks: Noten-Partikel bei Taktschlaegen und Comparator-Equalizer. Beides nutzt nur die vorberechnete
     * Analyse (Nachschlagen im Speicher), im Tick-Loop wird nichts gerechnet.
     */
    void fastTick() {
        long now = System.currentTimeMillis();
        Map<BlockRegistry.Key, State> jukeboxes = effective().jukeboxes();
        spawnBeatParticles(jukeboxes, now);
        equalizer.update(jukeboxes, now, this::lastEnded);
    }

    private void spawnBeatParticles(Map<BlockRegistry.Key, State> effective, long now) {
        // Ein Netz hat einen Anfuehrer: der Takt wird einmal bestimmt und an allen Jukeboxen des Netzes gezeigt
        Map<State, List<BlockRegistry.Key>> byLeader = new IdentityHashMap<>();
        effective.forEach((key, state) -> byLeader.computeIfAbsent(state, s -> new ArrayList<>()).add(key));
        for (Map.Entry<State, List<BlockRegistry.Key>> e : byLeader.entrySet()) {
            State s = e.getKey();
            long position = now - s.startedAt;
            Optional<Analysis> a = plugin.songs().get(s.hash).flatMap(analysis::peek);
            if (a.isEmpty()) continue;
            int beat = a.get().nextBeatAfter(s.beatCursorMs);
            if (beat < 0 || beat > position) continue;
            s.beatCursorMs = position;
            for (BlockRegistry.Key key : e.getValue()) {
                JukeboxSettings cfg = settings.get(key);
                if (!cfg.particles()) continue;
                World world = Bukkit.getWorld(key.world());
                if (world == null) continue;
                Location center = new Location(world, key.x() + 0.5, key.y() + 1.0, key.z() + 0.5);
                if (world.getNearbyPlayers(center, 48).isEmpty()) continue; // niemand sieht es
                spawnNotes(world, center, cfg);
            }
        }
    }
    private static void spawnNotes(World world, Location center, JukeboxSettings cfg) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double spread = 1.6 - 0.14 * cfg.particleDensity(); // Dichte 10 = eng, Dichte 1 = weit gestreut
        for (int i = 0; i < cfg.particleAmount(); i++) {
            Location at = center.clone().add((rnd.nextDouble() - 0.5) * 2 * spread,
                    rnd.nextDouble() * Math.max(0.2, cfg.particleHeight()), (rnd.nextDouble() - 0.5) * 2 * spread);
            if (cfg.rainbow()) {
                world.spawnParticle(Particle.NOTE, at, 0, rnd.nextDouble(), 0, 0, 1.0); // Farbe = erster Offset (0-1)
            } else {
                world.spawnParticle(Particle.NOTE, at, 0, 0.0, 0, 0, 1.0);
            }
        }
    }

    private double distanceSquared(BlockRegistry.Key key, UUID world, Location loc) {
        if (!key.world().equals(world)) return Double.MAX_VALUE;
        return sq(loc.getX() - (key.x() + 0.5)) + sq(loc.getY() - (key.y() + 0.5)) + sq(loc.getZ() - (key.z() + 0.5));
    }

    private int songChannels(String hash) {
        return plugin.songs().get(hash).map(Song::channels).filter(c -> c >= 1 && c <= 16).orElse(2);
    }

    void onQuit(Player player) {
        heard.remove(player.getUniqueId());
        announced.remove(player.getUniqueId());
    }

    /** Beim Weltwechsel/Neuverbinden muss der Client neu informiert werden. */
    void forget(Player player) {
        Map<BlockRegistry.Key, Heard> hearing = heard.get(player.getUniqueId());
        if (hearing != null) hearing.clear();
        Set<BlockRegistry.Key> shown = announced.get(player.getUniqueId());
        if (shown != null) shown.clear();
    }

    private static double sq(double v) { return v * v; }

    // ---- Persistenz (playback.yml) ----

    /** Aktueller Zustand als YAML-Text (wird asynchron oder beim Abschalten geschrieben). */
    private String snapshot() {
        List<Map<String, Object>> list = new ArrayList<>();
        states.forEach((k, s) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", k.world().toString());
            m.put("x", k.x());
            m.put("y", k.y());
            m.put("z", k.z());
            m.put("record", Base64.getEncoder().encodeToString(s.record.serializeAsBytes()));
            m.put("hash", s.hash);
            m.put("title", s.title);
            m.put("artist", s.artist);
            m.put("duration", s.durationMs);
            m.put("started", s.startedAt);
            m.put("playing", s.playing);
            m.put("playlist", s.fromPlaylist);
            m.put("index", s.playlistIndex);
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

    void load() {
        java.nio.file.Path file = plugin.getDataFolder().toPath().resolve("playback.yml");
        if (!java.nio.file.Files.exists(file)) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        for (Map<?, ?> m : yaml.getMapList("jukeboxes")) {
            try {
                BlockRegistry.Key key = new BlockRegistry.Key(UUID.fromString((String) m.get("world")),
                        ((Number) m.get("x")).intValue(), ((Number) m.get("y")).intValue(), ((Number) m.get("z")).intValue());
                ItemStack record = ItemStack.deserializeBytes(Base64.getDecoder().decode((String) m.get("record")));
                Object title = m.get("title");
                Object artist = m.get("artist");
                states.put(key, new State(record, (String) m.get("hash"), title == null ? (String) m.get("hash") : title.toString(),
                        artist == null ? "" : artist.toString(), ((Number) m.get("duration")).intValue(),
                        ((Number) m.get("started")).longValue(), (Boolean) m.get("playing"),
                        Boolean.TRUE.equals(m.get("playlist")), m.get("index") instanceof Number n ? n.intValue() : -1));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Ungueltiger Eintrag in playback.yml uebersprungen: " + e.getMessage());
            }
        }
    }
}
