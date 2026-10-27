package de.peaksound.plugin;

import de.peaksound.protocol.PeakBlockType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Wunsch-Warteschlange: Spieler waehlen an einer Song-Bibliothek einen Song als Wunsch. Die Bibliothek haengt (per Redstone-
 * Staub) im Netz einer Jukebox; der Wunsch wird dort nach dem laufenden Song gespielt, danach geht die Playlist weiter.
 * Ueber /peaksoundvote stimmen die Zuhoerer in Reichweite ab, den laufenden Song zu ueberspringen (Mehrheit).
 * Die Warteschlange liegt nur im Speicher und ist nach einem Neustart leer.
 */
final class QueueService implements CommandExecutor {
    private record Entry(String hash, UUID requester, String requesterName) {}

    private final PeakSoundPlugin plugin;
    private final BlockRegistry registry;
    private final NetworkRegistry networks;
    private final SongStore songs;
    private final PlaybackManager playback;
    private final JukeboxSettingsStore settings;
    private final PeakItems items;
    private final Map<BlockRegistry.Key, Deque<Entry>> queues = new ConcurrentHashMap<>();
    /** Je Jukebox: Abstimmung zum laufenden Song (Kennung "hash@start") und wer schon dafuer gestimmt hat. */
    private final Map<BlockRegistry.Key, String> voteSong = new ConcurrentHashMap<>();
    private final Map<BlockRegistry.Key, Set<UUID>> votes = new ConcurrentHashMap<>();

    QueueService(PeakSoundPlugin plugin, BlockRegistry registry, NetworkRegistry networks, SongStore songs, PlaybackManager playback,
                 JukeboxSettingsStore settings, PeakItems items) {
        this.plugin = plugin;
        this.registry = registry;
        this.networks = networks;
        this.songs = songs;
        this.playback = playback;
        this.settings = settings;
        this.items = items;
    }

    private int maxSize() { return Math.max(1, plugin.getConfig().getInt("queue.max-size", 10)); }

    private int maxPerPlayer() { return Math.max(1, plugin.getConfig().getInt("queue.max-per-player", 3)); }

    boolean enabled() { return plugin.getConfig().getBoolean("queue.enabled", true); }

    // ---- Wuensche ----

    /** Ein Spieler wuenscht sich einen Song an einer Song-Bibliothek. */
    void request(Player player, Block library, Song song) {
        if (!enabled()) {
            player.sendMessage(Messages.error(player, "Wuensche sind auf diesem Server ausgeschaltet.", "Song requests are turned off on this server."));
            return;
        }
        NetworkRegistry.Net net = networks.networkOf(BlockRegistry.Key.of(library));
        List<BlockRegistry.Key> jukeboxes = net == null ? List.of() : new ArrayList<>(net.jukeboxes());
        jukeboxes.sort(Comparator.comparingInt(BlockRegistry.Key::x).thenComparingInt(BlockRegistry.Key::y).thenComparingInt(BlockRegistry.Key::z));
        if (jukeboxes.isEmpty()) {
            player.sendMessage(Messages.error(player, "Diese Bibliothek ist an keine Jukebox angeschlossen (Redstone-Staub).",
                    "This library is not connected to a jukebox (redstone dust)."));
            return;
        }
        BlockRegistry.Key target = jukeboxes.get(0);
        Block jukebox = blockOf(target);
        if (jukebox == null) return;
        var state = playback.state(jukebox).orElse(null);
        if (state != null && !state.fromPlaylist) {
            player.sendMessage(Messages.error(player, "Die Jukebox spielt eine eingelegte Platte; Wuensche gehen nur mit Playlist.",
                    "The jukebox is playing an inserted record; requests only work with a playlist."));
            return;
        }
        Deque<Entry> queue = queues.computeIfAbsent(target, k -> new ArrayDeque<>());
        synchronized (queue) {
            if (queue.size() >= maxSize()) {
                player.sendMessage(Messages.error(player, "Die Warteschlange ist voll.", "The queue is full."));
                return;
            }
            if (queue.stream().anyMatch(e -> e.hash().equals(song.hash()))) {
                player.sendMessage(Messages.error(player, "Dieser Song ist schon gewuenscht.", "This song is already requested."));
                return;
            }
            if (queue.stream().filter(e -> e.requester().equals(player.getUniqueId())).count() >= maxPerPlayer()) {
                player.sendMessage(Messages.error(player, "Du hast schon " + maxPerPlayer() + " Wuensche in der Warteschlange.",
                        "You already have " + maxPerPlayer() + " requests in the queue."));
                return;
            }
            queue.add(new Entry(song.hash(), player.getUniqueId(), player.getName()));
        }
        int place = queue.size();
        player.sendMessage(Messages.info(player, "Wunsch eingereiht (Platz " + place + "): " + song.title(),
                "Request queued (position " + place + "): " + song.title()));
        announce(target, song, player);
        if (state == null || !state.playing) playback.playNext(jukebox, state, false); // Jukebox ist still: sofort loslegen
    }

    private void announce(BlockRegistry.Key jukebox, Song song, Player requester) {
        int range = settings.get(jukebox).range();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(requester) || !p.getWorld().getUID().equals(jukebox.world())) continue;
            double dx = p.getX() - (jukebox.x() + 0.5), dy = p.getY() - jukebox.y(), dz = p.getZ() - (jukebox.z() + 0.5);
            if (dx * dx + dy * dy + dz * dz > (double) range * range) continue;
            boolean de = p.locale().getLanguage().equals("de");
            p.sendMessage(Component.text("\u266A ", NamedTextColor.GOLD)
                    .append(Component.text(requester.getName() + (de ? " wuenscht sich: " : " requested: "), NamedTextColor.GRAY))
                    .append(Component.text(song.title(), NamedTextColor.WHITE))
                    .append(Component.text(de ? "  [Skip-Abstimmung]" : "  [Vote to skip]", NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/peaksoundvote"))));
        }
    }

    boolean hasQueued(BlockRegistry.Key jukebox) {
        Deque<Entry> q = queues.get(jukebox);
        return q != null && !q.isEmpty();
    }

    /** Naechster Wunsch dieser Jukebox, der noch abspielbar ist (Songs, die es nicht mehr gibt, werden uebersprungen). */
    Song poll(BlockRegistry.Key jukebox) {
        Deque<Entry> q = queues.get(jukebox);
        if (q == null) return null;
        synchronized (q) {
            while (!q.isEmpty()) {
                Entry e = q.poll();
                Song s = songs.get(e.hash()).filter(x -> x.status() == Song.Status.OK).orElse(null);
                if (s != null) return s;
            }
        }
        return null;
    }

    ItemStack recordFor(Song song) {
        return items.createWritten(new PeakItems.Written(song.hash(), song.title(), song.artist(), song.uploaderName()));
    }

    // ---- Skip-Abstimmung (/peaksoundvote) ----

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        boolean de = player.locale().getLanguage().equals("de");
        BlockRegistry.Key jukebox = listeningTo(player);
        if (jukebox == null) {
            player.sendMessage(Messages.error(player, "Hier spielt gerade keine Jukebox in deiner Naehe.", "No jukebox is playing near you."));
            return true;
        }
        Block block = blockOf(jukebox);
        var state = block == null ? null : playback.state(block).orElse(null);
        if (state == null || !state.playing) return true;
        String signature = state.hash + "@" + state.startedAt;
        if (!signature.equals(voteSong.get(jukebox))) { // neuer Song: Abstimmung beginnt von vorn
            voteSong.put(jukebox, signature);
            votes.put(jukebox, new HashSet<>());
        }
        Set<UUID> voted = votes.computeIfAbsent(jukebox, k -> new HashSet<>());
        List<Player> listeners = listeners(jukebox);
        voted.add(player.getUniqueId());
        voted.retainAll(listeners.stream().map(Player::getUniqueId).toList()); // Gegangene zaehlen nicht mehr
        int needed = listeners.size() / 2 + 1;
        if (voted.size() >= needed) {
            votes.remove(jukebox);
            voteSong.remove(jukebox);
            for (Player p : listeners) {
                p.sendMessage(Messages.info(p, "Skip beschlossen (" + voted.size() + "/" + listeners.size() + ").",
                        "Skip approved (" + voted.size() + "/" + listeners.size() + ")."));
            }
            playback.playNext(block, state, true);
        } else {
            for (Player p : listeners) {
                p.sendMessage(Messages.info(p, player.getName() + " moechte skippen: " + voted.size() + "/" + needed + " Stimmen. /peaksoundvote",
                        player.getName() + " wants to skip: " + voted.size() + "/" + needed + " votes. /peaksoundvote"));
            }
        }
        return true;
    }

    /** Die naechste spielende Jukebox, in deren Reichweite der Spieler steht. */
    private BlockRegistry.Key listeningTo(Player player) {
        BlockRegistry.Key best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockRegistry.Key k : registry.keysOf(PeakBlockType.JUKEBOX)) {
            if (!k.world().equals(player.getWorld().getUID())) continue;
            Block b = blockOf(k);
            if (b == null || !playback.isPlaying(b)) continue;
            double dx = player.getX() - (k.x() + 0.5), dy = player.getY() - k.y(), dz = player.getZ() - (k.z() + 0.5);
            double d2 = dx * dx + dy * dy + dz * dz;
            int range = settings.get(k).range();
            if (d2 <= (double) range * range && d2 < bestDistance) {
                best = k;
                bestDistance = d2;
            }
        }
        return best;
    }

    private List<Player> listeners(BlockRegistry.Key jukebox) {
        int range = settings.get(jukebox).range();
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().getUID().equals(jukebox.world())) continue;
            double dx = p.getX() - (jukebox.x() + 0.5), dy = p.getY() - jukebox.y(), dz = p.getZ() - (jukebox.z() + 0.5);
            if (dx * dx + dy * dy + dz * dz <= (double) range * range) out.add(p);
        }
        return out;
    }

    private static Block blockOf(BlockRegistry.Key k) {
        World w = Bukkit.getWorld(k.world());
        return w == null || !w.isChunkLoaded(k.x() >> 4, k.z() >> 4) ? null : w.getBlockAt(k.x(), k.y(), k.z());
    }

    void remove(BlockRegistry.Key jukebox) {
        queues.remove(jukebox);
        votes.remove(jukebox);
        voteSong.remove(jukebox);
    }
}
