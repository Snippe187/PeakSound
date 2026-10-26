package de.peaksound.mod.client;

import de.peaksound.mod.PeakSoundMod;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PeakIds;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;

/**
 * {@code /peaksound debug}: sammelt so viele Angaben wie moeglich ueber Client, Verbindung, Audio und (per Anfrage)
 * den Server in einem Text. Im Chat steht eine Zusammenfassung und ein anklickbarer Text, der den ganzen Bericht in
 * die Zwischenablage kopiert; zusaetzlich wird er in eine Datei geschrieben. Jeder Abschnitt faengt Fehler selbst ab,
 * damit ein einzelnes Problem den Bericht nie verhindert.
 */
final class DebugReport {
    private static final long SERVER_TIMEOUT_MS = 4000;
    private static final Pattern LOG_FILTER = Pattern.compile("(?i)peaksound|mixin|exception|\\bERROR\\b|voicechat|fabric-api|fabric loader");

    private static final Pattern LOG_NOISE = Pattern.compile("(?i)Perflib|oshi|Failed to retrieve profile key");
    private static boolean waiting;
    private static long deadline;

    private DebugReport() {}

    static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("peaksound").then(ClientCommands.literal("debug").executes(context -> {
                run();
                return 1;
            })));
            dispatcher.register(ClientCommands.literal("peaksounddebug").executes(context -> {
                run();
                return 1;
            }));
        });
    }

    static void run() {
        Minecraft mc = Minecraft.getInstance();
        say(Component.literal("[PeakSound] Sammle Diagnose-Daten ...").withStyle(ChatFormatting.GOLD));
        if (ClientPlayNetworking.canSend(de.peaksound.mod.RawPayload.TYPE)) {
            waiting = true;
            deadline = System.currentTimeMillis() + SERVER_TIMEOUT_MS;
            ClientNetwork.send(new Packet.DebugRequest());
        } else {
            finish(null, "Der Server kennt den Kanal " + PeakIds.CHANNEL + " nicht: Das PeakSound-Plugin ist dort nicht geladen "
                    + "(oder du bist nicht auf einem Server).");
        }
        if (mc.player == null) waiting = false;
    }

    /** Fuer automatische Tests: existiert die Datei peaksound-autodebug.flag im Spielordner, laeuft der Bericht 10 s nach dem Beitritt und landet im Spiel-Log. */
    private static final boolean AUTO = Files.exists(FabricLoader.getInstance().getGameDir().resolve("peaksound-autodebug.flag"));
    private static int autoTicks;
    private static boolean autoDone;

    static void tick() {
        if (AUTO && !autoDone && Minecraft.getInstance().player != null && ++autoTicks == 200) {
            autoDone = true;
            run();
        }
        if (waiting && System.currentTimeMillis() > deadline) {
            waiting = false;
            finish(null, "Keine Antwort vom Server innerhalb von " + SERVER_TIMEOUT_MS / 1000 + " s (Plugin zu alt oder haengt).");
        }
    }

    static void onServerText(String text) {
        if (!waiting) return;
        waiting = false;
        finish(text, null);
    }

    // ---- Bericht ----

    private static void finish(String serverText, String serverProblem) {
        StringBuilder sb = new StringBuilder(16384);
        sb.append("===== PeakSound Debug-Bericht =====\n");
        sb.append("Erstellt: ").append(Instant.now()).append('\n');
        section(sb, "Client: Mod und Umgebung", DebugReport::environment);
        section(sb, "Client: Verbindung und Handshake", () -> connection(serverProblem));
        section(sb, "Client: Pakete", ClientNetwork::debug);
        section(sb, "Client: Audio (OpenAL)", DebugReport::audio);
        section(sb, "Client: Wiedergabe", AudioEngine::debug);
        section(sb, "Client: Song-Cache", SongLibrary::debug);
        section(sb, "Client: Lichtshow", LightShow::debug);
        section(sb, "Client: Einstellungen", () -> "  PeakSound-Lautstaerke=" + ClientSettings.volumePercent() + " % Strobo erlaubt=" + ClientSettings.strobeAllowed() + "\n");
        section(sb, "Client: Geladene Mods", DebugReport::mods);
        section(sb, "Client: Log-Auszug (PeakSound, Mixin, Fehler; neueste zuletzt)", DebugReport::clientLog);
        sb.append("\n===== SERVER =====\n");
        if (serverText != null) sb.append(serverText);
        else sb.append("KEINE ANTWORT VOM SERVER: ").append(serverProblem).append('\n');
        String report = sb.toString();

        String file = save(report);
        if (AUTO) for (String l : report.split("\\R")) System.out.println("[PEAKSOUND-DEBUG] " + l);
        // Zusammenfassung im Chat
        say(Component.literal("[PeakSound] Debug-Zusammenfassung").withStyle(ChatFormatting.GOLD));
        boolean channel = canSend();
        say(check("Mod geladen", true, "PeakSound " + PeakSoundMod.VERSION + ", Protokoll " + PeakIds.PROTOCOL_VERSION));
        say(check("Server kennt den PeakSound-Kanal", channel, channel ? "ja" : "NEIN: Plugin fehlt oder ist nicht aktiv"));
        say(check("Plugin hat den Handshake beantwortet", ServerState.active(),
                ServerState.active() ? "ja" : ServerState.versionMismatch() ? "Versionskonflikt Mod/Plugin" : "NEIN"));
        say(check("Operator laut Plugin", ServerState.operator(), ServerState.operator() ? "ja" : "nein (Admin-Button/Befehl nicht verfuegbar)"));
        say(check("Server-Bericht erhalten", serverText != null, serverText != null ? "ja" : serverProblem));
        MutableComponent copy = Component.literal("[>> HIER KLICKEN: gesamten Debug-Text in die Zwischenablage kopieren <<]")
                .withStyle(style -> style.withColor(ChatFormatting.GREEN).withUnderlined(true)
                        .withClickEvent(new ClickEvent.CopyToClipboard(report))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Kopiert " + report.length() + " Zeichen"))));
        say(copy);
        if (file != null) say(Component.literal("Zusaetzlich gespeichert in: " + file).withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent check(String what, boolean ok, String detail) {
        return Component.literal((ok ? "[OK] " : "[FEHLER] ") + what + ": " + detail).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static boolean canSend() {
        try {
            return ClientPlayNetworking.canSend(de.peaksound.mod.RawPayload.TYPE);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void say(Component c) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(c);
        });
    }

    private static void section(StringBuilder sb, String title, Supplier<String> body) {
        sb.append("\n[").append(title).append("]\n");
        try {
            sb.append(body.get());
        } catch (Throwable t) {
            sb.append("  FEHLER beim Sammeln: ").append(t).append('\n');
        }
    }

    private static String save(String report) {
        try {
            Path file = FabricLoader.getInstance().getGameDir().resolve("peaksound-debug.txt");
            Files.writeString(file, report, StandardCharsets.UTF_8);
            return file.toAbsolutePath().toString();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String environment() {
        FabricLoader loader = FabricLoader.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append("  PeakSound-Mod: ").append(PeakSoundMod.VERSION).append(", Protokoll ").append(PeakIds.PROTOCOL_VERSION).append('\n');
        sb.append("  Minecraft: ").append(version(loader, "minecraft")).append('\n');
        sb.append("  Fabric Loader: ").append(version(loader, "fabricloader")).append('\n');
        sb.append("  Fabric API: ").append(version(loader, "fabric-api")).append('\n');
        sb.append("  Java: ").append(System.getProperty("java.version")).append(" (").append(System.getProperty("java.vendor"))
                .append("), ").append(System.getProperty("java.home")).append('\n');
        sb.append("  System: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
                .append(' ').append(System.getProperty("os.arch")).append('\n');
        sb.append("  Spielordner: ").append(loader.getGameDir().toAbsolutePath()).append('\n');
        sb.append("  Startbefehl: ").append(cut(String.valueOf(System.getProperty("sun.java.command")), 300)).append('\n');
        boolean lunar = loader.getGameDir().toString().toLowerCase().contains("lunar")
                || String.valueOf(System.getProperty("java.home")).toLowerCase().contains("lunar")
                || String.valueOf(System.getProperty("sun.java.command")).toLowerCase().contains("lunar");
        sb.append("  Lunar Client erkannt: ").append(lunar).append('\n');
        sb.append("  Entwicklungsumgebung: ").append(loader.isDevelopmentEnvironment()).append('\n');
        return sb.toString();
    }

    private static String version(FabricLoader loader, String id) {
        return loader.getModContainer(id).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("NICHT GELADEN");
    }

    private static String connection(String serverProblem) {
        Minecraft mc = Minecraft.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append("  Spieler: ").append(mc.player == null ? "keiner (nicht in einer Welt)" : mc.player.getName().getString()
                + " / " + mc.player.getUUID()).append('\n');
        var server = mc.getCurrentServer();
        sb.append("  Server: ").append(server == null ? "keiner (Einzelspieler oder nicht verbunden)" : server.ip).append('\n');
        sb.append("  Server kennt Kanal ").append(PeakIds.CHANNEL).append(": ").append(canSend()).append('\n');
        sb.append("  ServerState:\n").append(ServerState.debug());
        if (serverProblem != null) sb.append("  Hinweis: ").append(serverProblem).append('\n');
        return sb.toString();
    }

    private static String audio() {
        StringBuilder sb = new StringBuilder();
        long context = ALC10.alcGetCurrentContext();
        sb.append("  OpenAL-Kontext vorhanden: ").append(context != 0).append('\n');
        if (context != 0) {
            sb.append("  AL_VERSION: ").append(AL10.alGetString(AL10.AL_VERSION)).append('\n');
            sb.append("  AL_RENDERER: ").append(AL10.alGetString(AL10.AL_RENDERER)).append('\n');
            sb.append("  AL_VENDOR: ").append(AL10.alGetString(AL10.AL_VENDOR)).append('\n');
            float[] x = new float[1], y = new float[1], z = new float[1];
            AL10.alGetListener3f(AL10.AL_POSITION, x, y, z);
            sb.append("  Listener-Position: ").append(x[0]).append(' ').append(y[0]).append(' ').append(z[0]).append('\n');
        }
        sb.append("  Minecraft-Lautstaerke Plattenspieler: ")
                .append(Minecraft.getInstance().options.getFinalSoundSourceVolume(net.minecraft.sounds.SoundSource.RECORDS)).append('\n');
        sb.append("  Minecraft-Lautstaerke Master: ")
                .append(Minecraft.getInstance().options.getFinalSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER)).append('\n');
        return sb.toString();
    }

    private static String mods() {
        List<ModContainer> all = new ArrayList<>(FabricLoader.getInstance().getAllMods());
        all.sort(Comparator.comparing(m -> m.getMetadata().getId()));
        StringBuilder sb = new StringBuilder("  Anzahl: " + all.size() + "\n");
        for (ModContainer m : all) {
            String id = m.getMetadata().getId();
            if (id.startsWith("fabric-") && !id.equals("fabric-api") && !id.equals("fabric-loader")) continue; // Fabric-API-Module weglassen
            sb.append("  ").append(id).append(' ').append(m.getMetadata().getVersion().getFriendlyString()).append('\n');
        }
        return sb.toString();
    }

    private static String clientLog() {
        Path log = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("latest.log");
        if (!Files.exists(log)) return "  " + log + " nicht gefunden\n";
        List<String> out = new ArrayList<>();
        try (RandomAccessFile f = new RandomAccessFile(log.toFile(), "r")) {
            long start = Math.max(0, f.length() - 400_000);
            f.seek(start);
            byte[] data = new byte[(int) (f.length() - start)];
            f.readFully(data);
            for (String l : new String(data, StandardCharsets.UTF_8).split("\\R")) {
                if (LOG_FILTER.matcher(l).find() && !LOG_NOISE.matcher(l).find()) out.add("  " + cut(l, 260));
            }
        } catch (IOException | RuntimeException e) {
            return "  Log nicht lesbar: " + e + "\n";
        }
        List<String> tail = out.size() > 50 ? out.subList(out.size() - 50, out.size()) : out;
        return String.join("\n", tail) + "\n";
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}