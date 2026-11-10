package de.peaksound.mod.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Fortschrittsansicht im Stil der PeakVerse-Web-Oberflaeche: Reiter PeakVerse (Standard) und Vanilla, Baum mit Zoom (Mausrad zur Maus,
 * + / -, "Alles zeigen"), Suche, Filter, einklappbare Aeste (Rechtsklick), Seitenleiste mit Details und Checkliste der Teilziele,
 * Anpinnen fuer die Anzeige im Spiel. Ersetzt die Standardansicht (Taste L), solange {@link ClientSettings#peakAdvancements()} an ist.
 */
final class PeakAdvancementsScreen extends Screen implements ClientAdvancements.Listener {
    private static final int CW = 44, CH = 36, PAD = 30, NODE = 26, SIDE_W = 200;
    private static final float ZMIN = 0.10f, ZMAX = 3.0f;
    private static final int BG = 0xFF1E1E1E, PANEL = 0xFF2B2B2B, LINE_OFF = 0xFF555555, LINE_ON = 0xFFE8E8E8, ACCENT = 0xFF4A6FA5;

    private record Placed(AdvancementNode node, DisplayInfo info, int depth, double row, int hidden) {}

    private final ClientAdvancements adv;
    private boolean peakTab = true;
    private AdvancementHolder selectedRoot, selectedNode;
    private float zoom = 1f;
    private double offX, offY;
    private boolean dirty = true, dragging, moved;
    private int filter;   // 0 alle, 1 offen, 2 erreicht
    private final Set<Identifier> collapsed = new HashSet<>();
    private List<Placed> placed = List.of();
    private List<AdvancementNode> peakRoots = List.of(), vanillaRoots = List.of();
    private double maxRow;
    private int maxDepth;
    private EditBox search;
    private String query = "";
    private int sideScroll;
    // Klickflaechen, jedes Bild neu berechnet: x0, y0, x1, y1, Art, Index
    private final List<int[]> hits = new ArrayList<>();
    private int cx0, cy0, cx1, cy1;

    PeakAdvancementsScreen(ClientAdvancements adv) {
        super(Component.translatable("gui.advancements"));
        this.adv = adv;
    }

    // ---------- Daten ----------

    @Override
    protected void init() {
        adv.setListener(this);
        search = new EditBox(font, Math.max(80, Math.min(150, width / 6)), 16, Component.translatable("peaksound.advancements.search"));
        search.setHint(Component.translatable("peaksound.advancements.search"));
        search.setMaxLength(40);
        search.setValue(query);
        search.setResponder(s -> query = s.toLowerCase(Locale.ROOT).trim());
        addWidget(search);
        layout();
        if (placed.isEmpty() && !peakTab) return;
        fit();
    }

    @Override
    public void removed() {
        adv.setListener(null);
        ClientPacketListener conn = minecraft.getConnection();
        if (conn != null) conn.send(ServerboundSeenAdvancementsPacket.closedScreen());
    }

    @Override public void onAddAdvancementRoot(AdvancementNode node) { dirty = true; }
    @Override public void onRemoveAdvancementRoot(AdvancementNode node) { dirty = true; }
    @Override public void onAddAdvancementTask(AdvancementNode node) { dirty = true; }
    @Override public void onRemoveAdvancementTask(AdvancementNode node) { dirty = true; }
    @Override public void onAdvancementsCleared() { dirty = true; }
    @Override public void onUpdateAdvancementProgress(AdvancementNode node, AdvancementProgress p) { /* Fortschritt wird live gelesen */ }
    @Override public void onSelectedTabChanged(AdvancementHolder holder) { /* Auswahl macht der Bildschirm selbst */ }

    private boolean done(AdvancementNode n) {
        return AdvData.done(adv, n.holder());
    }

    private static boolean shown(AdvancementNode n) {
        return n.advancement().display().isPresent();
    }

    private static boolean isPeak(AdvancementNode n) {
        return AdvData.PEAK_NAMESPACE.equals(n.holder().id().getNamespace());
    }

    private void layout() {
        if (!dirty) return;
        dirty = false;
        List<AdvancementNode> peak = new ArrayList<>(), vanilla = new ArrayList<>();
        for (AdvancementNode n : adv.getTree().roots()) {
            if (!shown(n)) continue;
            (isPeak(n) ? peak : vanilla).add(n);
        }
        peakRoots = peak;
        vanillaRoots = vanilla;
        if (peak.isEmpty() && peakTab) peakTab = false;
        List<AdvancementNode> roots = peakTab ? peak : vanilla;
        if (roots.isEmpty()) { placed = List.of(); maxRow = 0; maxDepth = 0; return; }
        AdvancementNode root = roots.get(0);
        for (AdvancementNode n : roots) if (selectedRoot != null && n.holder().id().equals(selectedRoot.id())) root = n;
        selectedRoot = root.holder();
        List<Placed> out = new ArrayList<>();
        double[] next = {0};
        int[] md = {0};
        place(root, 0, out, next, md);
        placed = out;
        maxRow = Math.max(0, next[0] - 1);
        maxDepth = md[0];
    }

    private double place(AdvancementNode n, int depth, List<Placed> out, double[] next, int[] md) {
        md[0] = Math.max(md[0], depth);
        DisplayInfo info = n.advancement().display().orElse(null);
        List<AdvancementNode> kids = new ArrayList<>();
        for (AdvancementNode c : n.children()) if (shown(c) && (!c.advancement().display().get().isHidden() || done(c))) kids.add(c);
        double row;
        int hiddenCount = 0;
        if (kids.isEmpty() || collapsed.contains(n.holder().id())) {
            row = next[0]++;
            if (!kids.isEmpty()) hiddenCount = countAll(n) - 1;
        } else {
            double first = 0, last = 0;
            for (int i = 0; i < kids.size(); i++) {
                double r = place(kids.get(i), depth + 1, out, next, md);
                if (i == 0) first = r;
                last = r;
            }
            row = (first + last) / 2;
        }
        out.add(new Placed(n, info, depth, row, hiddenCount));
        return row;
    }

    private int countAll(AdvancementNode n) {
        int c = 1;
        for (AdvancementNode k : n.children()) if (shown(k)) c += countAll(k);
        return c;
    }

    private int[] counts(AdvancementNode n) {
        int[] c = {0, 0};
        count(n, c);
        return c;
    }

    private void count(AdvancementNode n, int[] c) {
        if (!shown(n)) return;
        c[1]++;
        if (done(n)) c[0]++;
        for (AdvancementNode k : n.children()) count(k, c);
    }

    // ---------- Ansicht (Zoom, Verschieben) ----------

    private float sx(double wx) { return (float) (cx0 + offX + wx * zoom); }

    private float sy(double wy) { return (float) (cy0 + offY + wy * zoom); }

    private int viewW() { return cx1 - cx0 - (selectedNode != null ? SIDE_W : 0); }

    /** Zeigt den ganzen Baum. */
    private void fit() {
        computeFrame();
        double w = PAD * 2 + maxDepth * CW, h = PAD * 2 + maxRow * CH;
        zoom = (float) Math.max(ZMIN, Math.min(1.2, Math.min(viewW() / w, (cy1 - cy0) / h)));
        offX = (viewW() - w * zoom) / 2;
        offY = ((cy1 - cy0) - h * zoom) / 2;
    }

    private void centerOn(Placed p) {
        offX = viewW() / 2.0 - (PAD + p.depth * CW) * zoom;
        offY = (cy1 - cy0) / 2.0 - (PAD + p.row * CH) * zoom;
    }

    private void zoomAt(double mx, double my, float factor) {
        float nz = Math.max(ZMIN, Math.min(ZMAX, zoom * factor));
        double wx = (mx - cx0 - offX) / zoom, wy = (my - cy0 - offY) / zoom;
        zoom = nz;
        offX = mx - cx0 - wx * zoom;
        offY = my - cy0 - wy * zoom;
    }

    private void computeFrame() {
        int x0 = 12, y0 = 12, x1 = width - 12, y1 = height - 12;
        cx0 = x0 + 2;
        cy0 = y0 + 52;
        cx1 = x1 - 2;
        cy1 = y1 - 18;
    }

    // ---------- Filter, Suche ----------

    private boolean visibleByFilter(AdvancementNode n) {
        boolean d = done(n);
        return filter == 0 || (filter == 1 && !d) || (filter == 2 && d);
    }

    private boolean matches(DisplayInfo i) {
        return query.isEmpty() || i.getTitle().getString().toLowerCase(Locale.ROOT).contains(query)
                || i.getDescription().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    // ---------- Zeichnen ----------

    @Override
    public boolean isPauseScreen() { return false; }

    private static int frameColor(AdvancementType t, boolean done) {
        if (!done) return t == AdvancementType.CHALLENGE ? 0xFF5E4180 : 0xFF6B6B6B;
        return t == AdvancementType.CHALLENGE ? 0xFFC77DFF : t == AdvancementType.GOAL ? 0xFFFFB454 : 0xFFE8E8E8;
    }

    private void button(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String text, boolean on, int kind, int index) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 18;
        g.fill(x, y, x + w, y + 18, on ? ACCENT : hover ? 0xFF4A4A4A : 0xFF383838);
        g.text(font, text, x + (w - font.width(text)) / 2, y + 5, 0xFFFFFFFF);
        hits.add(new int[] {x, y, x + w, y + 18, kind, index});
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractRenderState(g, mx, my, partial);
        layout();
        computeFrame();
        hits.clear();
        int x0 = 12, y0 = 12, x1 = width - 12, y1 = height - 12;
        g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0xFF000000);
        g.fill(x0, y0, x1, y1, BG);
        g.fill(x0, y0, x1, y0 + 50, PANEL);

        // Gesamtfortschritt
        List<AdvancementNode> rootsHere = peakTab ? peakRoots : vanillaRoots;
        int total = 0, done = 0;
        for (AdvancementNode n : rootsHere) { int[] c = counts(n); done += c[0]; total += c[1]; }
        String sum = done + " / " + total;
        int barW = Math.min(110, (x1 - x0) / 6), barX = x1 - 8 - font.width(sum) - 6 - barW;
        g.fill(barX, y0 + 7, barX + barW, y0 + 14, 0xFF3A3A3A);
        if (total > 0) g.fill(barX, y0 + 7, barX + Math.round(barW * (float) done / total), y0 + 14, 0xFF5BC0EB);
        g.text(font, sum, x1 - 8 - font.width(sum), y0 + 6, 0xFFFFFFFF);

        // Reihe 1: Hauptreiter
        int tx = x0 + 6;
        String peakLabel = Component.translatable("peaksound.advancements.tab.peak").getString();
        String vanLabel = Component.translatable("peaksound.advancements.tab.vanilla").getString();
        if (!peakRoots.isEmpty()) { int w = font.width(peakLabel) + 16; button(g, mx, my, tx, y0 + 3, w, peakLabel, peakTab, 1, 0); tx += w + 4; }
        if (!vanillaRoots.isEmpty()) { int w = font.width(vanLabel) + 16; button(g, mx, my, tx, y0 + 3, w, vanLabel, !peakTab, 1, 1); }

        // Reihe 2: Bereiche, Suche, Filter, Alles zeigen, Standard-Ansicht
        int ty = y0 + 28;
        tx = x0 + 6;
        for (int i = 0; i < rootsHere.size() && (rootsHere.size() > 1); i++) {
            AdvancementNode n = rootsHere.get(i);
            DisplayInfo d = n.advancement().display().get();
            int[] c = counts(n);
            String label = d.getTitle().getString() + " " + c[0] + "/" + c[1];
            int w = font.width(label) + 28;
            if (tx + w > x1 - 330) break;
            boolean sel = selectedRoot != null && n.holder().id().equals(selectedRoot.id());
            boolean hv = mx >= tx && mx < tx + w && my >= ty && my < ty + 18;
            g.fill(tx, ty, tx + w, ty + 18, sel ? ACCENT : hv ? 0xFF4A4A4A : 0xFF383838);
            g.item(d.getIcon().create(), tx + 2, ty + 1);
            g.text(font, label, tx + 22, ty + 5, 0xFFFFFFFF);
            hits.add(new int[] {tx, ty, tx + w, ty + 18, 2, i});
            tx += w + 3;
        }
        int rx = x1 - 6;
        String std = Component.translatable("peaksound.advancements.vanilla").getString();
        int sw = font.width(std) + 12;
        rx -= sw; button(g, mx, my, rx, ty, sw, std, false, 3, 0); rx -= 3;
        String all = Component.translatable("peaksound.advancements.fit").getString();
        int aw = font.width(all) + 12;
        rx -= aw; button(g, mx, my, rx, ty, aw, all, false, 4, 0); rx -= 3;
        String fl = Component.translatable("peaksound.advancements.filter." + filter).getString();
        int fw = font.width(fl) + 12;
        rx -= fw; button(g, mx, my, rx, ty, fw, fl, filter != 0, 5, 0); rx -= 3;
        search.setX(rx - search.getWidth());
        search.setY(ty);
        search.extractRenderState(g, mx, my, partial);

        // Baum
        g.enableScissor(cx0, cy0, cx1 - (selectedNode != null ? SIDE_W : 0), cy1);
        Map<Identifier, Placed> byId = new HashMap<>();
        for (Placed p : placed) byId.put(p.node.holder().id(), p);
        int lw = Math.max(1, Math.round(2 * zoom));
        for (Placed p : placed) {
            AdvancementNode par = p.node.parent();
            Placed q = par == null ? null : byId.get(par.holder().id());
            if (q == null) continue;
            int xa = Math.round(sx(PAD + q.depth * CW)), ya = Math.round(sy(PAD + q.row * CH));
            int xb = Math.round(sx(PAD + p.depth * CW)), yb = Math.round(sy(PAD + p.row * CH)), xm = (xa + xb) / 2;
            int col = done(p.node) ? LINE_ON : LINE_OFF;
            g.fill(xa, ya - lw / 2, xm + lw, ya - lw / 2 + lw, col);
            g.fill(xm, Math.min(ya, yb) - lw / 2, xm + lw, Math.max(ya, yb) + lw, col);
            g.fill(xm, yb - lw / 2, xb + 1, yb - lw / 2 + lw, col);
        }
        Placed hovered = null;
        boolean searching = !query.isEmpty();
        Placed firstMatch = null;
        int nodePx = Math.max(4, Math.round(NODE * zoom));
        for (Placed p : placed) {
            int cxp = Math.round(sx(PAD + p.depth * CW)), cyp = Math.round(sy(PAD + p.row * CH));
            int px = cxp - nodePx / 2, py = cyp - nodePx / 2;
            if (px > cx1 || px + nodePx < cx0 || py > cy1 || py + nodePx < cy0) continue;
            boolean ok = done(p.node);
            int fc = frameColor(p.info.getType(), ok);
            int b = Math.max(1, Math.round(2 * zoom));
            g.fill(px, py, px + nodePx, py + nodePx, fc);
            g.fill(px + b, py + b, px + nodePx - b, py + nodePx - b, ok ? 0xFF4A4A4A : 0xFF2A2A2A);
            if (p.info.getType() == AdvancementType.GOAL && nodePx >= 12) {
                g.fill(px, py, px + b, py + b, BG); g.fill(px + nodePx - b, py, px + nodePx, py + b, BG);
                g.fill(px, py + nodePx - b, px + b, py + nodePx, BG); g.fill(px + nodePx - b, py + nodePx - b, px + nodePx, py + nodePx, BG);
            }
            if (nodePx >= 12) {
                float s = (nodePx - 2f * b - 2) / 16f;
                var pose = g.pose();
                pose.pushMatrix();
                pose.translate(px + b + 1, py + b + 1);
                pose.scale(s, s);
                g.item(p.info.getIcon().create(), 0, 0);
                pose.popMatrix();
            }
            boolean match = matches(p.info);
            if (searching && match && firstMatch == null) firstMatch = p;
            boolean dim = !visibleByFilter(p.node) || (searching && !match) || (!ok && nodePx >= 12);
            if (dim) g.fill(px + b, py + b, px + nodePx - b, py + nodePx - b, !visibleByFilter(p.node) || (searching && !match) ? 0xCC000000 : 0x88000000);
            if (searching && match) g.outline(px - 2, py - 2, nodePx + 4, nodePx + 4, 0xFFFFE066);
            if (selectedNode != null && selectedNode.id().equals(p.node.holder().id())) g.outline(px - 2, py - 2, nodePx + 4, nodePx + 4, 0xFF5BC0EB);
            if (p.hidden > 0 && nodePx >= 12) g.text(font, "+" + p.hidden, px + nodePx - font.width("+" + p.hidden) - 1, py + nodePx - 9, 0xFFFFE066);
            if (zoom >= 0.9f) {
                String title = p.info.getTitle().getString();
                int maxW = (int) (CW * zoom) - 4;
                String shown = font.width(title) > maxW ? font.plainSubstrByWidth(title, maxW) : title;
                g.text(font, shown, cxp - font.width(shown) / 2, py + nodePx + 3, ok ? 0xFFFFFFFF : 0xFF909090);
            }
            if (mx >= px && mx < px + nodePx && my >= py && my < py + nodePx && mx >= cx0 && mx < cx1 - (selectedNode != null ? SIDE_W : 0) && my >= cy0 && my < cy1) {
                hovered = p;
                g.outline(px - 1, py - 1, nodePx + 2, nodePx + 2, 0xFFFFFFFF);
            }
        }
        g.disableScissor();
        this.firstMatch = firstMatch;

        if (selectedNode != null) drawSide(g, mx, my, x1);

        // Fusszeile
        g.text(font, Component.translatable("peaksound.advancements.hint"), x0 + 6, y1 - 12, 0xFF808080);
        String z = Math.round(zoom * 100) + " %";
        g.text(font, z, x1 - 8 - font.width(z), y1 - 12, 0xFF808080);

        if (hovered != null) tooltip(g, hovered, mx, my);
    }

    private Placed firstMatch;

    private void tooltip(GuiGraphicsExtractor g, Placed p, int mx, int my) {
        boolean ok = done(p.node);
        AdvancementType t = p.info.getType();
        AdvancementProgress pr = AdvData.progress(adv, p.node.holder());
        List<Component> tip = new ArrayList<>();
        tip.add(p.info.getTitle().copy().withStyle(t.getChatColor()));
        tip.add(p.info.getDescription());
        int[] parts = AdvData.parts(pr);
        Component status = ok ? Component.translatable("peaksound.advancements.done") : Component.translatable("peaksound.advancements.open");
        tip.add(Component.empty().append(t.getDisplayName()).append(" · ").append(status).append(parts[1] > 1 ? " · " + parts[0] + "/" + parts[1] : ""));
        if (!ok && parts[1] > 1) {
            List<String> missing = AdvData.remaining(pr);
            tip.add(Component.translatable("peaksound.advancements.missing").withStyle(net.minecraft.ChatFormatting.YELLOW));
            for (int i = 0; i < Math.min(8, missing.size()); i++) tip.add(Component.literal(" • " + AdvData.name(missing.get(i))).withStyle(net.minecraft.ChatFormatting.GRAY));
            if (missing.size() > 8) tip.add(Component.translatable("peaksound.advancements.more", missing.size() - 8).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        String reward = rewardText(p.node.advancement().rewards());
        if (!reward.isEmpty()) tip.add(Component.translatable("peaksound.advancements.reward", reward).withStyle(net.minecraft.ChatFormatting.GREEN));
        g.setComponentTooltipForNextFrame(font, tip, mx, my);
    }

    private static String rewardText(AdvancementRewards r) {
        List<String> l = new ArrayList<>();
        if (r.experience() > 0) l.add(r.experience() + " XP");
        if (!r.loot().isEmpty()) l.add(r.loot().size() + " Loot");
        if (!r.recipes().isEmpty()) l.add(r.recipes().size() + " " + Component.translatable("peaksound.advancements.recipes").getString());
        return String.join(", ", l);
    }

    private void drawSide(GuiGraphicsExtractor g, int mx, int my, int x1) {
        AdvancementNode n = adv.getTree().get(selectedNode);
        if (n == null || !shown(n)) { selectedNode = null; return; }
        DisplayInfo d = n.advancement().display().get();
        int sx0 = x1 - SIDE_W, sy0 = cy0, sy1 = cy1;
        g.fill(sx0, sy0, x1, sy1, PANEL);
        g.fill(sx0, sy0, sx0 + 1, sy1, 0xFF000000);
        int x = sx0 + 8, y = sy0 + 8, w = SIDE_W - 16;
        boolean ok = done(n);
        AdvancementProgress pr = AdvData.progress(adv, n.holder());
        g.item(d.getIcon().create(), x, y);
        g.text(font, d.getTitle().copy().withStyle(d.getType().getChatColor()), x + 22, y + 4, 0xFFFFFFFF);
        y += 24;
        for (FormattedCharSequence line : font.split(d.getDescription(), w)) { g.text(font, line, x, y, 0xFFC8C8C8); y += 10; }
        y += 4;
        int[] parts = AdvData.parts(pr);
        g.text(font, Component.empty().append(d.getType().getDisplayName()).append(" · ").append(ok ? Component.translatable("peaksound.advancements.done") : Component.translatable("peaksound.advancements.open")), x, y, ok ? 0xFF7EE06A : 0xFFFFAA00);
        y += 12;
        if (parts[1] > 1) {
            g.fill(x, y, x + w, y + 6, 0xFF3A3A3A);
            g.fill(x, y, x + Math.round(w * (float) parts[0] / parts[1]), y + 6, 0xFF5BC0EB);
            g.text(font, parts[0] + " / " + parts[1], x, y + 9, 0xFFA0A0A0);
            y += 22;
        }
        String reward = rewardText(n.advancement().rewards());
        if (!reward.isEmpty()) { g.text(font, Component.translatable("peaksound.advancements.reward", reward), x, y, 0xFF7EE06A); y += 12; }
        // Anpinnen
        String key = n.holder().id().toString();
        boolean pinned = ClientSettings.isPinned(key);
        String pinLabel = Component.translatable(pinned ? "peaksound.advancements.unpin" : "peaksound.advancements.pin").getString();
        if (!ok) {
            int bw = font.width(pinLabel) + 12;
            boolean hv = mx >= x && mx < x + bw && my >= y && my < y + 16;
            g.fill(x, y, x + bw, y + 16, pinned ? ACCENT : hv ? 0xFF4A4A4A : 0xFF383838);
            g.text(font, pinLabel, x + 6, y + 4, 0xFFFFFFFF);
            hits.add(new int[] {x, y, x + bw, y + 16, 6, 0});
            y += 22;
        }
        // Checkliste der Teilziele
        if (parts[1] > 1) {
            List<String> open = AdvData.remaining(pr), got = AdvData.completed(pr);
            g.text(font, Component.translatable("peaksound.advancements.checklist"), x, y, 0xFFFFFFFF);
            y += 12;
            int listTop = y, listBottom = sy1 - 4;
            g.enableScissor(sx0 + 1, listTop, x1, listBottom);
            int ly = listTop - sideScroll;
            for (String s : open) { g.text(font, "○ " + AdvData.name(s), x, ly, 0xFFE0E0E0); ly += 10; }
            for (String s : got) { g.text(font, "✓ " + AdvData.name(s), x, ly, 0xFF7EE06A); ly += 10; }
            g.disableScissor();
            sideMaxScroll = Math.max(0, (open.size() + got.size()) * 10 - (listBottom - listTop));
            sideScroll = Math.max(0, Math.min(sideScroll, sideMaxScroll));
        }
    }

    private int sideMaxScroll;

    // ---------- Eingabe ----------

    private Placed nodeAt(double mx, double my) {
        int nodePx = Math.max(4, Math.round(NODE * zoom));
        for (Placed p : placed) {
            int cxp = Math.round(sx(PAD + p.depth * CW)), cyp = Math.round(sy(PAD + p.row * CH));
            if (mx >= cxp - nodePx / 2.0 && mx < cxp + nodePx / 2.0 && my >= cyp - nodePx / 2.0 && my < cyp + nodePx / 2.0) return p;
        }
        return null;
    }

    private boolean inCanvas(double mx, double my) {
        return mx >= cx0 && mx < cx1 - (selectedNode != null ? SIDE_W : 0) && my >= cy0 && my < cy1;
    }

    private void switchTab(boolean peak) {
        peakTab = peak;
        selectedRoot = null;
        selectedNode = null;
        dirty = true;
        layout();
        fit();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        double x = e.x(), y = e.y();
        if (search != null && x >= search.getX() && x < search.getX() + search.getWidth() && y >= search.getY() && y < search.getY() + 18) {
            setFocused(search);
            search.setFocused(true);
            return search.mouseClicked(e, dbl);
        }
        if (search != null) search.setFocused(false);
        setFocused(null);
        if (e.button() == 0) {
            for (int[] b : hits) {
                if (x < b[0] || x >= b[2] || y < b[1] || y >= b[3]) continue;
                switch (b[4]) {
                    case 1 -> switchTab(b[5] == 0);
                    case 2 -> {
                        AdvancementNode n = (peakTab ? peakRoots : vanillaRoots).get(b[5]);
                        selectedRoot = n.holder();
                        selectedNode = null;
                        dirty = true;
                        layout();
                        fit();
                    }
                    case 3 -> {
                        ClientSettings.setPeakAdvancements(false);
                        minecraft.setScreenAndShow(new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(adv));
                    }
                    case 4 -> fit();
                    case 5 -> filter = (filter + 1) % 3;
                    case 6 -> { if (selectedNode != null) ClientSettings.togglePin(selectedNode.id().toString()); }
                    default -> { }
                }
                return true;
            }
        }
        if (inCanvas(x, y)) {
            Placed p = nodeAt(x, y);
            if (e.button() == 1) {
                if (p != null && (p.node.children().iterator().hasNext())) {
                    Identifier id = p.node.holder().id();
                    if (!collapsed.remove(id)) collapsed.add(id);
                    dirty = true;
                }
                return true;
            }
            if (e.button() == 0) {
                if (p != null) {
                    selectedNode = p.node.holder();
                    sideScroll = 0;
                    return true;
                }
                dragging = true;
                moved = false;
                return true;
            }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging) { offX += dx; offY += dy; moved = true; return true; }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragging && !moved) selectedNode = null;   // Klick auf leere Flaeche loest die Auswahl
        dragging = false;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (selectedNode != null && mx >= cx1 - SIDE_W) {
            sideScroll = Math.max(0, Math.min(sideMaxScroll, sideScroll - (int) (sy * 20)));
            return true;
        }
        if (inCanvas(mx, my)) {
            zoomAt(mx, my, sy > 0 ? 1.15f : 1 / 1.15f);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (search != null && search.isFocused()) {
            int k = e.key();
            if (k == 256) { search.setFocused(false); setFocused(null); return true; }
            if (k == 257 || k == 335) { if (firstMatch != null) centerOn(firstMatch); return true; }
            return super.keyPressed(e);
        }
        if (minecraft.options.keyAdvancements.matches(e)) { onClose(); return true; }
        int k = e.key();
        float cx = (cx0 + cx1) / 2f, cy = (cy0 + cy1) / 2f;
        if (k == 61 || k == 93 || k == 334) { zoomAt(cx, cy, 1.25f); return true; }   // + (auch deutsche Tastatur und Ziffernblock)
        if (k == 45 || k == 47 || k == 333) { zoomAt(cx, cy, 0.8f); return true; }    // -
        if (k == 48 || k == 320) { fit(); return true; }                               // 0 = alles zeigen
        return super.keyPressed(e);
    }
}
