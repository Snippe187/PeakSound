package de.peaksound.mod.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.resources.Identifier;

/**
 * Fortschritts-Ansicht im Stil der PeakVerse-Web-Oberflaeche: dunkle Flaeche, Reiter je Bereich mit Zaehler, Fortschrittsbalken,
 * Baum mit Linien und eckigen Knoten (Ziele rund, Herausforderungen violett), Tooltip mit Titel und Beschreibung.
 * Ersetzt die Standard-Ansicht (Taste L), solange {@link ClientSettings#peakAdvancements()} an ist; der Knopf unten rechts schaltet um.
 */
final class PeakAdvancementsScreen extends Screen implements ClientAdvancements.Listener {
    private static final int CW = 44, CH = 36, PAD = 26, NODE = 26;
    private static final int BG = 0xFF1E1E1E, PANEL = 0xFF2B2B2B, LINE_OFF = 0xFF555555, LINE_ON = 0xFFE8E8E8;

    private record Placed(AdvancementNode node, DisplayInfo info, int depth, double row) {}

    private final ClientAdvancements advancements;
    private final Map<Identifier, AdvancementProgress> progress = new HashMap<>();
    private AdvancementHolder selected;
    private double offX, offY;
    private boolean dirty = true, dragging;
    private List<Placed> placed = List.of();
    private final List<int[]> tabBoxes = new ArrayList<>();   // x0, y0, x1, y1, Index der Wurzel
    private List<AdvancementNode> roots = List.of();
    private int vanillaX0, vanillaY0, vanillaX1, vanillaY1;

    PeakAdvancementsScreen(ClientAdvancements advancements) {
        super(Component.translatable("gui.advancements"));
        this.advancements = advancements;
    }

    // ---------- Daten ----------

    @Override
    protected void init() {
        advancements.setListener(this);
    }

    @Override
    public void removed() {
        advancements.setListener(null);
        ClientPacketListener conn = minecraft.getConnection();
        if (conn != null) conn.send(ServerboundSeenAdvancementsPacket.closedScreen());
    }

    @Override public void onAddAdvancementRoot(AdvancementNode node) { dirty = true; }
    @Override public void onRemoveAdvancementRoot(AdvancementNode node) { dirty = true; }
    @Override public void onAddAdvancementTask(AdvancementNode node) { dirty = true; }
    @Override public void onRemoveAdvancementTask(AdvancementNode node) { dirty = true; }
    @Override public void onAdvancementsCleared() { progress.clear(); dirty = true; }

    @Override
    public void onUpdateAdvancementProgress(AdvancementNode node, AdvancementProgress p) {
        progress.put(node.holder().id(), p);
    }

    @Override
    public void onSelectedTabChanged(AdvancementHolder holder) {
        if (holder != null && (selected == null || !selected.id().equals(holder.id()))) { selected = holder; offX = 0; offY = 0; dirty = true; }
    }

    private boolean done(AdvancementNode n) {
        AdvancementProgress p = progress.get(n.holder().id());
        return p != null && p.isDone();
    }

    private static boolean shown(AdvancementNode n) {
        return n.advancement().display().isPresent();
    }

    private void layout() {
        if (!dirty) return;
        dirty = false;
        List<AdvancementNode> r = new ArrayList<>();
        for (AdvancementNode n : advancements.getTree().roots()) if (shown(n)) r.add(n);
        roots = r;
        if (roots.isEmpty()) { placed = List.of(); return; }
        AdvancementNode root = roots.get(0);
        for (AdvancementNode n : roots) if (selected != null && n.holder().id().equals(selected.id())) root = n;
        selected = root.holder();
        List<Placed> out = new ArrayList<>();
        double[] next = {0};
        place(root, 0, out, next);
        placed = out;
    }

    private double place(AdvancementNode n, int depth, List<Placed> out, double[] next) {
        DisplayInfo info = n.advancement().display().orElse(null);
        List<AdvancementNode> kids = new ArrayList<>();
        for (AdvancementNode c : n.children()) if (shown(c) && (!c.advancement().display().get().isHidden() || done(c))) kids.add(c);
        double row;
        if (kids.isEmpty()) row = next[0]++;
        else {
            double first = 0, last = 0;
            for (int i = 0; i < kids.size(); i++) {
                double r = place(kids.get(i), depth + 1, out, next);
                if (i == 0) first = r;
                last = r;
            }
            row = (first + last) / 2;
        }
        out.add(new Placed(n, info, depth, row));
        return row;
    }

    private int[] counts(AdvancementNode root) {
        int[] c = {0, 0};
        count(root, c);
        return c;
    }

    private void count(AdvancementNode n, int[] c) {
        if (!shown(n)) return;
        c[1]++;
        if (done(n)) c[0]++;
        for (AdvancementNode k : n.children()) count(k, c);
    }

    // ---------- Zeichnen ----------

    @Override
    public boolean isPauseScreen() { return false; }

    private static int frameColor(AdvancementType t, boolean done) {
        if (!done) return t == AdvancementType.CHALLENGE ? 0xFF5E4180 : 0xFF6B6B6B;
        return t == AdvancementType.CHALLENGE ? 0xFFC77DFF : t == AdvancementType.GOAL ? 0xFFFFB454 : 0xFFE8E8E8;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractRenderState(g, mx, my, partial);
        layout();
        int x0 = 16, y0 = 16, x1 = width - 16, y1 = height - 16;
        g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0xFF000000);
        g.fill(x0, y0, x1, y1, BG);
        g.fill(x0, y0, x1, y0 + 30, PANEL);
        // Gesamtfortschritt
        int total = 0, done = 0;
        for (AdvancementNode n : roots) { int[] c = counts(n); done += c[0]; total += c[1]; }
        String sum = done + " / " + total;
        int barW = Math.min(120, (x1 - x0) / 5), barX = x1 - 10 - font.width(sum) - 8 - barW;
        g.fill(barX, y0 + 11, barX + barW, y0 + 19, 0xFF3A3A3A);
        if (total > 0) g.fill(barX, y0 + 11, barX + Math.round(barW * (float) done / total), y0 + 19, 0xFF5BC0EB);
        g.text(font, sum, x1 - 10 - font.width(sum), y0 + 11, 0xFFFFFFFF);
        // Reiter
        tabBoxes.clear();
        int tx = x0 + 6;
        for (int i = 0; i < roots.size(); i++) {
            AdvancementNode n = roots.get(i);
            DisplayInfo d = n.advancement().display().get();
            int[] c = counts(n);
            Component label = d.getTitle();
            String cnt = c[0] + "/" + c[1];
            int w = 6 + 16 + 4 + font.width(label) + 6 + font.width(cnt) + 6;
            if (tx + w > barX - 8) break;
            boolean sel = selected != null && n.holder().id().equals(selected.id());
            boolean hover = mx >= tx && mx < tx + w && my >= y0 + 4 && my < y0 + 26;
            g.fill(tx, y0 + 4, tx + w, y0 + 26, sel ? 0xFF4A6FA5 : hover ? 0xFF444444 : 0xFF383838);
            g.item(d.getIcon().create(), tx + 6, y0 + 7);
            g.text(font, label, tx + 26, y0 + 11, 0xFFFFFFFF);
            g.text(font, cnt, tx + 26 + font.width(label) + 6, y0 + 11, 0xFFA0A0A0);
            tabBoxes.add(new int[] {tx, y0 + 4, tx + w, y0 + 26, i});
            tx += w + 4;
        }
        // Baum
        int cx0 = x0 + 2, cy0 = y0 + 32, cx1 = x1 - 2, cy1 = y1 - 18;
        g.enableScissor(cx0, cy0, cx1, cy1);
        Map<Identifier, Placed> byId = new HashMap<>();
        for (Placed p : placed) byId.put(p.node.holder().id(), p);
        int ox = cx0 + PAD + (int) offX, oy = cy0 + PAD + (int) offY;
        for (Placed p : placed) {
            AdvancementNode par = p.node.parent();
            Placed q = par == null ? null : byId.get(par.holder().id());
            if (q == null) continue;
            int xa = ox + q.depth * CW, ya = oy + (int) (q.row * CH), xb = ox + p.depth * CW, yb = oy + (int) (p.row * CH), xm = (xa + xb) / 2;
            int col = done(p.node) ? LINE_ON : LINE_OFF;
            g.fill(xa, ya - 1, xm + 1, ya + 1, col);
            g.fill(xm - 1, Math.min(ya, yb) - 1, xm + 1, Math.max(ya, yb) + 1, col);
            g.fill(xm, yb - 1, xb + 1, yb + 1, col);
        }
        Placed hovered = null;
        for (Placed p : placed) {
            int px = ox + p.depth * CW - NODE / 2, py = oy + (int) (p.row * CH) - NODE / 2;
            boolean ok = done(p.node);
            int fc = frameColor(p.info.getType(), ok);
            g.fill(px, py, px + NODE, py + NODE, fc);
            g.fill(px + 2, py + 2, px + NODE - 2, py + NODE - 2, ok ? 0xFF4A4A4A : 0xFF2A2A2A);
            if (p.info.getType() == AdvancementType.GOAL) {   // abgerundete Ecken
                int bg = BG;
                g.fill(px, py, px + 2, py + 2, bg); g.fill(px + NODE - 2, py, px + NODE, py + 2, bg);
                g.fill(px, py + NODE - 2, px + 2, py + NODE, bg); g.fill(px + NODE - 2, py + NODE - 2, px + NODE, py + NODE, bg);
            }
            g.item(p.info.getIcon().create(), px + 5, py + 5);
            if (!ok) g.fill(px + 2, py + 2, px + NODE - 2, py + NODE - 2, 0x99000000);
            if (mx >= px && mx < px + NODE && my >= py && my < py + NODE && mx >= cx0 && mx < cx1 && my >= cy0 && my < cy1) {
                hovered = p;
                g.outline(px - 1, py - 1, NODE + 2, NODE + 2, 0xFFFFFFFF);
            }
        }
        g.disableScissor();
        // Fusszeile
        g.text(font, Component.translatable("peaksound.advancements.hint"), x0 + 6, y1 - 12, 0xFF808080);
        String vanilla = Component.translatable("peaksound.advancements.vanilla").getString();
        int vw = font.width(vanilla) + 12;
        vanillaX1 = x1 - 6; vanillaX0 = vanillaX1 - vw; vanillaY0 = y1 - 15; vanillaY1 = y1 - 3;
        boolean vh = mx >= vanillaX0 && mx < vanillaX1 && my >= vanillaY0 && my < vanillaY1;
        g.fill(vanillaX0, vanillaY0, vanillaX1, vanillaY1, vh ? 0xFF555555 : 0xFF383838);
        g.text(font, vanilla, vanillaX0 + 6, vanillaY0 + 2, 0xFFFFFFFF);
        if (hovered != null) {
            boolean ok = done(hovered.node);
            AdvancementType t = hovered.info.getType();
            List<Component> tip = new ArrayList<>();
            tip.add(hovered.info.getTitle().copy().withStyle(t.getChatColor()));
            tip.add(hovered.info.getDescription());
            AdvancementProgress p = progress.get(hovered.node.holder().id());
            Component status = ok ? Component.translatable("peaksound.advancements.done") : p != null && p.hasProgress() ? p.getProgressText() : Component.translatable("peaksound.advancements.open");
            tip.add(Component.empty().append(t.getDisplayName()).append(" · ").append(status));
            g.setComponentTooltipForNextFrame(font, tip, mx, my);
        }
    }

    // ---------- Eingabe ----------

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() == 0) {
            double x = e.x(), y = e.y();
            if (x >= vanillaX0 && x < vanillaX1 && y >= vanillaY0 && y < vanillaY1) {
                ClientSettings.setPeakAdvancements(false);
                minecraft.setScreenAndShow(new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(advancements));
                return true;
            }
            for (int[] b : tabBoxes) {
                if (x >= b[0] && x < b[2] && y >= b[1] && y < b[3]) {
                    AdvancementNode n = roots.get(b[4]);
                    advancements.setSelectedTab(n.holder(), true);
                    selected = n.holder(); offX = 0; offY = 0; dirty = true;
                    return true;
                }
            }
            dragging = true;
            return true;
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging) { offX += dx; offY += dy; return true; }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        dragging = false;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        offY += sy * 24;
        offX += sx * 24;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (minecraft.options.keyAdvancements.matches(e)) { onClose(); return true; }
        return super.keyPressed(e);
    }
}
