package de.peaksound.mod.client;

import de.peaksound.mod.client.mixin.ClientAdvancementsAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Gemeinsame Hilfen fuer Fortschrittsansicht und Anzeige angepinnter Aufgaben. */
final class AdvData {
    static final String PEAK_NAMESPACE = "peakachievements";

    private AdvData() {}

    static ClientAdvancements advancements() {
        ClientPacketListener c = Minecraft.getInstance().getConnection();
        return c == null ? null : c.getAdvancements();
    }

    static AdvancementProgress progress(ClientAdvancements adv, AdvancementHolder holder) {
        return ((ClientAdvancementsAccessor) adv).peaksound$progress().get(holder);
    }

    static boolean done(ClientAdvancements adv, AdvancementHolder holder) {
        AdvancementProgress p = progress(adv, holder);
        return p != null && p.isDone();
    }

    /** {erledigte Teile, alle Teile}; {0, 0}, wenn es keine Teile gibt. */
    static int[] parts(AdvancementProgress p) {
        if (p == null) return new int[] {0, 0};
        int done = 0, open = 0;
        for (String ignored : p.getCompletedCriteria()) done++;
        for (String ignored : p.getRemainingCriteria()) open++;
        return new int[] {done, done + open};
    }

    static List<String> remaining(AdvancementProgress p) {
        List<String> l = new ArrayList<>();
        if (p != null) for (String s : p.getRemainingCriteria()) l.add(s);
        return l;
    }

    static List<String> completed(AdvancementProgress p) {
        List<String> l = new ArrayList<>();
        if (p != null) for (String s : p.getCompletedCriteria()) l.add(s);
        return l;
    }

    /** Lesbarer Name eines Teilziels, z. B. "minecraft:plains" -> "Ebene" (Biom, Mob, Item oder Block), sonst "Plains". */
    static String name(String criterion) {
        String c = criterion;
        Language lang = Language.getInstance();
        Identifier id = Identifier.tryParse(c);
        if (id != null) {
            for (String kind : new String[] {"biome", "entity", "item", "block"}) {
                String key = kind + "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
                if (lang.has(key)) return Component.translatable(key).getString();
            }
            c = id.getPath();
        }
        String[] words = c.replace('/', ' ').replace('_', ' ').trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
        }
        return sb.toString();
    }
}
