package de.peaksound.mod.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Zeigt angepinnte Aufgaben mit Fortschritt am rechten Bildschirmrand; erledigte werden automatisch geloest. */
final class PinHud {
    private PinHud() {}

    static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("peaksound", "pinned_advancements"), PinHud::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        List<String> pins = ClientSettings.pins();
        if (pins.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientAdvancements adv = AdvData.advancements();
        if (adv == null) return;
        var font = mc.font;
        int w = mc.getWindow().getGuiScaledWidth();
        int y = 26;
        List<String> finished = new ArrayList<>();
        for (String pin : pins) {
            Identifier id = Identifier.tryParse(pin);
            AdvancementHolder holder = id == null ? null : adv.get(id);
            if (holder == null || holder.value().display().isEmpty()) continue;
            if (AdvData.done(adv, holder)) { finished.add(pin); continue; }
            DisplayInfo d = holder.value().display().get();
            AdvancementProgress p = AdvData.progress(adv, holder);
            int[] parts = AdvData.parts(p);
            Component title = d.getTitle();
            String count = parts[1] > 1 ? parts[0] + "/" + parts[1] : "";
            int boxW = Math.min(170, Math.max(110, font.width(title) + font.width(count) + 44));
            int x = w - boxW - 6;
            g.fill(x, y, x + boxW, y + 26, 0xB0000000);
            g.item(d.getIcon().create(), x + 3, y + 5);
            g.text(font, title, x + 23, y + 4, 0xFFFFFFFF);
            if (parts[1] > 1) {
                int bx = x + 23, bw = boxW - 29 - font.width(count);
                g.fill(bx, y + 17, bx + bw, y + 21, 0xFF3A3A3A);
                g.fill(bx, y + 17, bx + Math.round(bw * (float) parts[0] / parts[1]), y + 21, 0xFF5BC0EB);
                g.text(font, count, bx + bw + 4, y + 14, 0xFFA0A0A0);
            } else {
                g.text(font, Component.translatable("peaksound.advancements.open"), x + 23, y + 14, 0xFFA0A0A0);
            }
            y += 30;
        }
        for (String f : finished) ClientSettings.unpin(f);
    }
}
