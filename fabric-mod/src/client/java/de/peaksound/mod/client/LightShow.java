package de.peaksound.mod.client;

import de.peaksound.audio.Analysis;
import de.peaksound.audio.AudioException;
import de.peaksound.audio.LightLogic;
import de.peaksound.protocol.LightSettings;
import de.peaksound.protocol.Packet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Die Lichtshow beim Client. Der Server meldet nur Start, Aenderung und Ende eines Scheinwerfers; jedes Bild
 * berechnet der Client selbst aus Einstellungen, Songposition (Serveruhr) und der Musikanalyse. Weil alle Clients
 * dieselben Eingaben haben, sehen alle Spieler dieselbe Show. Der Strahl besteht aus Staub-Partikeln.
 */
final class LightShow {
    private record Light(LightSettings settings, String hash, long startedAtServerMs) {}

    /** Scheinwerfer ab dieser Entfernung (Bloecke) zeichnen wir nicht. */
    private static final double DRAW_DISTANCE = 56;
    private static final double BEAM_LENGTH = 16;
    private static final long REQUEST_RETRY_MS = 2000;

    private static final Map<Long, Light> lights = new HashMap<>();
    private static final Map<Long, int[]> positions = new HashMap<>();
    private static final Map<String, Analysis> analyses = new HashMap<>();
    private static final Map<String, Long> requested = new HashMap<>();
    private static boolean warned;

    private LightShow() {}

    static void start(Packet.LightStart start) {
        long key = PlaybackSession.key(start.x(), start.y(), start.z());
        lights.put(key, new Light(start.settings(), start.hash(), start.startedAtServerMs()));
        positions.put(key, new int[] {start.x(), start.y(), start.z()});
    }

    static void stop(int x, int y, int z) {
        long key = PlaybackSession.key(x, y, z);
        lights.remove(key);
        positions.remove(key);
    }

    static void onAnalysis(Packet.AnalysisData data) {
        if (data.data().length == 0) return; // noch nicht berechnet: tick() fragt spaeter erneut
        try {
            analyses.put(data.hash(), Analysis.fromBytes(data.data()));
        } catch (AudioException | RuntimeException ignored) {
            // beschaedigte Analyse: die Show laeuft dann ohne Takt weiter
        }
    }

    static void clear() {
        lights.clear();
        positions.clear();
        analyses.clear();
        requested.clear();
        warned = false;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || lights.isEmpty()) return;
        long now = ServerState.serverNow();
        long wall = System.currentTimeMillis();
        Vec3 eye = mc.player.position();
        boolean allowStrobe = ClientSettings.strobeAllowed();

        for (Map.Entry<Long, Light> e : lights.entrySet()) {
            Light light = e.getValue();
            int[] p = positions.get(e.getKey());
            double cx = p[0] + 0.5, cy = p[1] + 0.5, cz = p[2] + 0.5;
            double dx = cx - eye.x, dy = cy - eye.y, dz = cz - eye.z;
            if (dx * dx + dy * dy + dz * dz > DRAW_DISTANCE * DRAW_DISTANCE) continue;

            Analysis analysis = null;
            long position = 0;
            if (!light.hash().isEmpty()) {
                position = Math.max(0, now - light.startedAtServerMs());
                analysis = analyses.get(light.hash());
                if (analysis == null && light.settings().mode() == LightSettings.LightMode.BEAT
                        && wall - requested.getOrDefault(light.hash(), 0L) > REQUEST_RETRY_MS) {
                    requested.put(light.hash(), wall);
                    ClientNetwork.send(new Packet.AnalysisRequest(light.hash()));
                }
            }
            LightLogic.Frame frame = LightLogic.frame(light.settings(), analysis, position, now, allowStrobe);
            if (frame.strobing() && allowStrobe && !warned) {
                warned = true;
                mc.player.sendSystemMessage(Component.translatable("peaksound.light.strobe.warning"));
            }
            if (frame.intensity() < 0.03) continue;
            spawnBeam(level, cx, cy, cz, frame);
        }
    }

    private static void spawnBeam(ClientLevel level, double cx, double cy, double cz, LightLogic.Frame frame) {
        double yaw = Math.toRadians(frame.yaw()), pitch = Math.toRadians(frame.pitch());
        // Gleiche Richtung wie beim echten Licht des Servers (0 Grad = Sueden)
        double dirX = -Math.sin(yaw) * Math.cos(pitch), dirY = Math.sin(pitch), dirZ = Math.cos(yaw) * Math.cos(pitch);
        double scale = 0.35 + 0.65 * Math.min(1, frame.intensity());
        int r = (int) (((frame.rgb() >> 16) & 255) * scale), g = (int) (((frame.rgb() >> 8) & 255) * scale), b = (int) ((frame.rgb() & 255) * scale);
        DustParticleOptions options = new DustParticleOptions((r << 16) | (g << 8) | b, (float) (0.8 + frame.intensity() * 0.8));
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int count = 2 + (int) Math.round(frame.intensity() * 8);
        for (int i = 0; i < count; i++) {
            double dist = 0.7 + rnd.nextDouble() * BEAM_LENGTH;
            double spread = 0.03 + dist * 0.025; // der Strahl wird nach vorn leicht breiter
            level.addParticle(options,
                    cx + dirX * dist + (rnd.nextDouble() - 0.5) * spread * 2,
                    cy + dirY * dist + (rnd.nextDouble() - 0.5) * spread * 2,
                    cz + dirZ * dist + (rnd.nextDouble() - 0.5) * spread * 2, 0, 0, 0);
        }
    }
}