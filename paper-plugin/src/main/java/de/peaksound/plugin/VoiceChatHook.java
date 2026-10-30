package de.peaksound.plugin;

import org.bukkit.plugin.java.JavaPlugin;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.World;

/**
 * Anbindung an Simple Voice Chat (optional, nur geladen, wenn das Plugin vorhanden ist). Spricht ein Spieler in der
 * Naehe eines Mikrofon-Blocks, wird seine Stimme ueber die angeschlossenen Lautsprecher als ortsgebundener Kanal
 * (3D) wiedergegeben. Die Klasse laeuft auf dem Thread von Simple Voice Chat und fasst keine Bukkit-Bloecke an.
 */
final class VoiceChatHook implements VoicechatPlugin {
    private final MicService mic;
    private volatile VoicechatServerApi api;
    private final Map<UUID, OpusDecoder> decoders = new ConcurrentHashMap<>();
    private final Map<BlockRegistry.Key, LocationalAudioChannel> channels = new ConcurrentHashMap<>();
    private final Map<BlockRegistry.Key, OpusEncoder> encoders = new ConcurrentHashMap<>();

    private VoiceChatHook(MicService mic) {
        this.mic = mic;
    }

    /** Meldet das Plugin bei Simple Voice Chat an. Gibt false zurueck, wenn das nicht moeglich ist. */
    static boolean register(JavaPlugin plugin, MicService mic) {
        try {
            BukkitVoicechatService service = plugin.getServer().getServicesManager().load(BukkitVoicechatService.class);
            if (service == null) return false;
            service.registerPlugin(new VoiceChatHook(mic));
            return true;
        } catch (LinkageError | RuntimeException e) {
            Ctx.logger().log(Level.WARNING, "Simple Voice Chat konnte nicht angebunden werden (Mikrofon-Block ohne Funktion)", e);
            return false;
        }
    }

    @Override
    public String getPluginId() {
        return "peaksound";
    }

    @Override
    public void initialize(VoicechatApi voicechatApi) {
        if (voicechatApi instanceof VoicechatServerApi server) {
            api = server;
            mic.setVoiceChatAvailable(true);
        }
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        VoicechatServerApi server = api;
        VoicechatConnection sender = event.getSenderConnection();
        if (server == null || sender == null || sender.getPlayer() == null) return;
        UUID speaker = sender.getPlayer().getUuid();
        List<MicService.Target> targets = mic.targetsOf(speaker);
        if (targets.isEmpty()) return;
        byte[] opus = event.getPacket().getOpusEncodedData();
        if (opus == null || opus.length == 0) return; // leeres Paket = Sprechpause

        short[] pcm = null;
        for (MicService.Target target : targets) {
            mic.spoke(target.mic());
            for (MicService.Output out : target.outputs()) {
                if (out.volume() <= 0) continue;
                World world = MicService.worldOf(out.key());
                if (world == null) continue;
                LocationalAudioChannel channel = channels.computeIfAbsent(out.key(), k -> {
                    LocationalAudioChannel c = server.createLocationalAudioChannel(UUID.randomUUID(),
                            server.fromServerLevel(world), server.createPosition(out.x(), out.y(), out.z()));
                    return c;
                });
                if (channel == null) continue;
                channel.setDistance(out.range());
                // Der Sprecher hoert sich nicht selbst mit Verzoegerung
                channel.setFilter(p -> !p.getUuid().equals(speaker));
                if (out.volume() == 100) {
                    channel.send(opus);
                    continue;
                }
                if (pcm == null) pcm = decoders.computeIfAbsent(speaker, k -> server.createDecoder()).decode(opus);
                short[] scaled = new short[pcm.length];
                double gain = out.volume() / 100.0;
                for (int i = 0; i < pcm.length; i++) {
                    scaled[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(pcm[i] * gain)));
                }
                channel.send(encoders.computeIfAbsent(out.key(), k -> server.createEncoder()).encode(scaled));
            }
        }
    }
}