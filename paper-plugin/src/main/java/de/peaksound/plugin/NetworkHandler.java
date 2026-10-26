package de.peaksound.plugin;

import de.peaksound.protocol.MalformedPacketException;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PacketCodec;
import de.peaksound.protocol.PeakIds;
import de.peaksound.protocol.Limits;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Zentrale Anlaufstelle fuer alle Client-Pakete: Handshake (Versionspruefung, Operator-Status) und
 * Weiterleitung an Upload- und Streaming-Dienst. Spieler ohne Mod bekommen nur einen Chat-Hinweis.
 */
final class NetworkHandler implements PluginMessageListener, Listener {
    private final PeakSoundPlugin plugin;
    private final PeakRecipes recipes;
    private final Set<UUID> modded = ConcurrentHashMap.newKeySet();
    private UploadService uploads;
    private SongStreamService streams;
    private PlaybackManager playback;
    private JukeboxMenuService menu;
    private SpeakerMenuService speakerMenu;
    private EditAccess access;
    private AdminService admin;
    private LightService lights;
    private DjService dj;
    private MicService mic;
    private DebugService debug;
    /** Spieler, deren HelloAck noch aussteht, weil ihr Client den Kanal beim Server noch nicht angemeldet hatte (Paper verwirft solche Sendungen still). */
    private final Set<UUID> pendingAck = ConcurrentHashMap.newKeySet();
    /** Letztes Hello je Spieler (auch bei Versionskonflikt) und Paketzaehler, nur fuer die Diagnose. */
    private record HelloInfo(int protocol, String modVersion, long at, boolean compatible) {}

    private final Map<UUID, HelloInfo> hellos = new ConcurrentHashMap<>();
    private final Map<UUID, long[]> traffic = new ConcurrentHashMap<>(); // empfangen, ungueltig, zuletzt (ms)
    private final Map<UUID, String> lastError = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> lastOperator = new ConcurrentHashMap<>();

    NetworkHandler(PeakSoundPlugin plugin, PeakRecipes recipes) {
        this.plugin = plugin;
        this.recipes = recipes;
    }

    /** Verbindet die Dienste (erst nach deren Erzeugung moeglich, da sie das NetworkHandler brauchen). */
    void wire(UploadService uploads, SongStreamService streams, PlaybackManager playback, JukeboxMenuService menu,
              SpeakerMenuService speakerMenu, EditAccess access, AdminService admin, LightService lights, DjService dj, MicService mic) {
        this.mic = mic;
        this.lights = lights;
        this.dj = dj;
        this.admin = admin;
        this.uploads = uploads;
        this.streams = streams;
        this.playback = playback;
        this.menu = menu;
        this.speakerMenu = speakerMenu;
        this.access = access;
    }

    void setDebug(DebugService debug) { this.debug = debug; }

    /** Diagnosezeilen ueber diesen Spieler: Hello, Paketzaehler, letzter Fehler. */
    String describe(Player player) {
        HelloInfo h = hellos.get(player.getUniqueId());
        long[] t = traffic.get(player.getUniqueId());
        StringBuilder sb = new StringBuilder();
        sb.append("  Hello empfangen: ").append(h == null ? "NEIN (der Server hat von diesem Spieler noch nie ein gueltiges Hello gesehen)"
                : "ja, Mod " + h.modVersion() + ", Protokoll " + h.protocol() + " (Server " + PeakIds.PROTOCOL_VERSION + "), kompatibel: "
                + h.compatible() + ", vor " + (System.currentTimeMillis() - h.at()) / 1000 + " s").append('\n');
        sb.append("  Pakete von diesem Spieler auf Kanal ").append(PeakIds.CHANNEL).append(": ").append(t == null ? 0 : t[0])
                .append(" (davon ungueltig: ").append(t == null ? 0 : t[1]).append(")\n");
        String err = lastError.get(player.getUniqueId());
        if (err != null) sb.append("  Letzter Paketfehler: ").append(err).append('\n');
        return sb.toString();
    }

    boolean hasMod(Player player) {
        return modded.contains(player.getUniqueId());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(PeakIds.CHANNEL)) return;
        Packet.Serverbound packet;
        long[] stats = traffic.computeIfAbsent(player.getUniqueId(), k -> new long[3]);
        stats[0]++;
        stats[2] = System.currentTimeMillis();
        try {
            packet = PacketCodec.decodeServerbound(message);
        } catch (MalformedPacketException e) {
            stats[1]++;
            lastError.put(player.getUniqueId(), e.getMessage() + " (" + message.length + " Bytes)");
            plugin.getLogger().fine("Ungueltiges Paket von " + player.getName() + ": " + e.getMessage());
            return; // manipulierte Pakete werden still ignoriert
        }
        if (packet instanceof Packet.Hello hello) {
            onHello(player, hello);
            return;
        }
        if (packet instanceof Packet.DebugRequest) { // die Diagnose muss auch ohne Handshake antworten
            if (debug != null) debug.onRequest(player);
            return;
        }
        if (!hasMod(player)) return; // alles andere nur nach erfolgreichem Handshake
        switch (packet) {
            case Packet.UploadStart start -> uploads.onStart(player, start);
            case Packet.UploadChunk chunk -> uploads.onChunk(player, chunk);
            case Packet.SongRequest request -> streams.onRequest(player, request);
            case Packet.CoverRequest request -> streams.onCover(player, request);
            case Packet.JukeboxUpdate update -> menu.onUpdate(player, update);
            case Packet.PlaylistRequest request -> menu.onPlaylistRequest(player, request);
            case Packet.PlaylistAction action -> menu.onPlaylistAction(player, action);
            case Packet.SpeakerUpdate update -> speakerMenu.onUpdate(player, update);
            case Packet.AdminListRequest request -> admin.onListRequest(player);
            case Packet.AdminAction action -> admin.onAction(player, action);
            case Packet.SpeakerOverviewRequest request -> menu.onSpeakerOverviewRequest(player, request);
            case Packet.LightUpdate update -> lights.onUpdate(player, update);
            case Packet.AnalysisRequest request -> lights.onAnalysisRequest(player, request);
            case Packet.DjRequest request -> dj.onRequest(player, request);
            case Packet.DjUpdate update -> dj.onUpdate(player, update);
            case Packet.MicUpdate update -> mic.onUpdate(player, update);
            case Packet.DjDeckAction action -> dj.onDeckAction(player, action);
            case Packet.DebugRequest ignored -> { /* oben behandelt */ }
            case Packet.Hello ignored -> { /* oben behandelt */ }
        }
    }

    private void onHello(Player player, Packet.Hello hello) {
        boolean compatible = hello.protocolVersion() == PeakIds.PROTOCOL_VERSION;
        hellos.put(player.getUniqueId(), new HelloInfo(hello.protocolVersion(), hello.modVersion(), System.currentTimeMillis(), compatible));
        plugin.getLogger().info(player.getName() + ": PeakSound-Mod meldet sich (Protokoll " + hello.protocolVersion() + ", Server " + PeakIds.PROTOCOL_VERSION
                + ", Operator: " + Permissions.admin(player) + ")");
        if (compatible) {
            modded.add(player.getUniqueId());
        } else {
            player.sendMessage(Messages.versionMismatch(player, PeakIds.PROTOCOL_VERSION, hello.protocolVersion()));
        }
        pendingAck.add(player.getUniqueId());
        trySendAck(player);
    }

    /**
     * Antwortet auf das Hello, sobald der Client den Kanal angemeldet hat. Der Client schickt Hello und
     * Kanal-Anmeldung unmittelbar nacheinander; kommt das Hello zuerst an, wuerde Paper die Antwort verwerfen.
     */
    private void trySendAck(Player player) {
        HelloInfo h = hellos.get(player.getUniqueId());
        if (h == null || !player.getListeningPluginChannels().contains(PeakIds.CHANNEL)) return;
        if (!pendingAck.remove(player.getUniqueId())) return;
        if (h.compatible()) playback.forget(player); // Client startet leer: laufende Jukeboxen erneut melden
        Net.send(plugin, player, new Packet.HelloAck(PeakIds.PROTOCOL_VERSION, h.compatible(),
                h.compatible() && Permissions.admin(player), System.currentTimeMillis(), UploadService.maxSongSeconds(plugin)));
        if (h.compatible()) lastOperator.put(player.getUniqueId(), Permissions.admin(player));
    }

    @EventHandler
    public void onRegisterChannel(org.bukkit.event.player.PlayerRegisterChannelEvent event) {
        if (event.getChannel().equals(PeakIds.CHANNEL)) trySendAck(event.getPlayer());
    }
    /** Meldet Aenderungen des Operator-Status (z. B. nach /op), damit der Button im Escape-Menue stimmt. */
    void checkOperators() {
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (!hasMod(p)) continue;
            boolean now = Permissions.admin(p);
            Boolean before = lastOperator.put(p.getUniqueId(), now);
            if (before == null || before != now) Net.send(plugin, p, new Packet.OperatorState(now));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.discoverRecipes(recipes.keys()); // Rezepte im Rezeptbuch sichtbar machen
        if (!plugin.getConfig().getBoolean("non-mod-hint.enabled", true)) return;
        long delay = Math.max(1, plugin.getConfig().getLong("non-mod-hint.delay-ticks", 100));
        player.getScheduler().runDelayed(plugin, task -> {
            if (!hasMod(player)) player.sendMessage(Messages.nonModHint(player));
        }, null, delay);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        playback.forget(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        modded.remove(player.getUniqueId());
        uploads.onQuit(player);
        streams.onQuit(player);
        playback.onQuit(player);
        access.onQuit(player);
        admin.onQuit(player);
        lights.onQuit(player);
        lastOperator.remove(player.getUniqueId());
        hellos.remove(player.getUniqueId());
        pendingAck.remove(player.getUniqueId());
        traffic.remove(player.getUniqueId());
        lastError.remove(player.getUniqueId());
        if (debug != null) debug.onQuit(player);
    }
}
