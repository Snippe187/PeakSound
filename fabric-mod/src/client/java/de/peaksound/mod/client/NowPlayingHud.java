package de.peaksound.mod.client;

import de.peaksound.protocol.LyricsLogic;
import de.peaksound.protocol.Packet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Anzeige am Bildschirm: beim Start eines Songs in Hoerweite kurz Cover, Titel und Interpret (oben in der Mitte), dazu,
 * solange der Song laeuft und einen Text in den Tags hat, die aktuelle Textzeile unten ueber der Hotbar. Beides laesst
 * sich mit /peaksoundhud ausschalten. Songtexte ohne Zeitstempel werden nur gleichmaessig verteilt und mit "~" markiert.
 */
final class NowPlayingHud {
    private static final long TOAST_MS = 6000, FADE_MS = 800;

    private record Info(String hash, String title, String artist, long startedAt, int durationMs, long shownAt) {}

    private static Info current;
    private static final Set<String> requested = new HashSet<>();
    private static final Map<String, String> rawLyrics = new HashMap<>();
    private static final Map<String, LyricsLogic.Lyrics> parsed = new HashMap<>();

    private NowPlayingHud() {}

    static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("peaksound", "now_playing"), NowPlayingHud::render);
    }

    static void onInfo(Packet.SongInfo p) {
        Info old = current;
        boolean same = old != null && old.hash().equals(p.hash()) && old.startedAt() == p.startedAtServerMs();
        // Dieselbe Wiedergabe (z. B. mehrere Lautsprecher) zeigt die Meldung nur einmal
        current = new Info(p.hash(), p.title(), p.artist(), p.startedAtServerMs(), p.durationMs(), same ? old.shownAt() : System.currentTimeMillis());
        if (ClientSettings.lyrics() && requested.add(p.hash())) ClientNetwork.send(new Packet.LyricsRequest(p.hash()));
    }

    static void onLyrics(Packet.LyricsData d) {
        rawLyrics.put(d.hash(), d.text());
        parsed.remove(d.hash());
    }

    static void clear() {
        current = null;
        requested.clear();
        rawLyrics.clear();
        parsed.clear();
    }

    private static LyricsLogic.Lyrics lyricsOf(Info info) {
        String raw = rawLyrics.get(info.hash());
        if (raw == null || raw.isEmpty()) return null;
        return parsed.computeIfAbsent(info.hash(), h -> LyricsLogic.parse(raw, info.durationMs()));
    }

    private static int withAlpha(int rgb, float alpha) {
        return (Math.max(4, Math.min(255, (int) (alpha * 255))) << 24) | (rgb & 0xFFFFFF);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Info info = current;
        if (info == null) return;
        long position = ServerState.serverNow() - info.startedAt();
        if (position > info.durationMs() + 1500L) {
            current = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        var font = mc.font;

        long age = System.currentTimeMillis() - info.shownAt();
        if (ClientSettings.hud() && age < TOAST_MS) {
            float alpha = age > TOAST_MS - FADE_MS ? Math.max(0f, (TOAST_MS - age) / (float) FADE_MS) : 1f;
            int boxW = Math.max(120, Math.max(font.width(info.title()), font.width(info.artist())) + 36);
            int x = w / 2 - boxW / 2, y = 8;
            g.fill(x, y, x + boxW, y + 30, withAlpha(0x000000, 0.65f * alpha));
            CoverTooltip.draw(g, info.hash(), true, x + 3, y + 3, 24);
            g.text(font, Component.literal("\u266A " + info.title()), x + 31, y + 5, withAlpha(0xFFFFFF, alpha));
            if (!info.artist().isBlank()) g.text(font, Component.literal(info.artist()), x + 31, y + 17, withAlpha(0xAAAAAA, alpha));
        }

        if (!ClientSettings.lyrics()) return;
        LyricsLogic.Lyrics lyrics = lyricsOf(info);
        if (lyrics == null || lyrics.isEmpty()) return;
        List<LyricsLogic.Line> lines = lyrics.lines();
        int index = LyricsLogic.lineAt(lines, position);
        if (index < 0) return;
        String mark = lyrics.synced() ? "" : "~ ";
        Component now = Component.literal(mark + lines.get(index).text());
        int y = h - 66;
        g.text(font, now, w / 2 - font.width(now) / 2, y, 0xFFFFFFFF);
        if (index + 1 < lines.size()) {
            Component next = Component.literal(lines.get(index + 1).text());
            g.text(font, next, w / 2 - font.width(next) / 2, y + 11, 0xFF9A9AA4);
        }
    }
}
