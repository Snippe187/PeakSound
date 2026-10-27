package de.peaksound.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.peaksound.protocol.PlaylistLogic;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Das eigene Inventar jeder Jukebox: {@link PlaylistLogic#INVENTORY_SLOTS} Slots fuer beschriebene Platten. Die Inventare
 * liegen nur auf dem Server (nie als Kiste geoeffnet); die Mod zeigt sie in ihrem Fenster. Gespeichert wird in
 * {@code jukebox-inventories.json} (je Jukebox eine Liste von Base64-Items).
 */
final class JukeboxStorage {
    /** Bukkit-Kisteninventare haben ein Vielfaches von 9 Slots; die letzten drei bleiben ungenutzt. */
    private static final int SIZE = 18;

    private final PeakSoundPlugin plugin;
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<BlockRegistry.Key, Inventory> inventories = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    JukeboxStorage(PeakSoundPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("jukebox-inventories.json");
    }

    private static String keyString(BlockRegistry.Key k) {
        return k.world() + ";" + k.x() + ";" + k.y() + ";" + k.z();
    }

    private static BlockRegistry.Key parse(String s) {
        String[] p = s.split(";");
        return new BlockRegistry.Key(UUID.fromString(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
    }

    /** Das Inventar dieser Jukebox (wird bei Bedarf leer angelegt). */
    Inventory inventory(BlockRegistry.Key key) {
        return inventories.computeIfAbsent(key, k -> Bukkit.createInventory(null, SIZE, Component.text("PeakSound")));
    }

    void markDirty() { dirty = true; }

    /** Erster freier Slot (0 bis 14) oder -1. */
    int freeSlot(BlockRegistry.Key key) {
        Inventory inv = inventory(key);
        for (int i = 0; i < PlaylistLogic.INVENTORY_SLOTS; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) return i;
        }
        return -1;
    }

    /** Entfernt das Inventar und gibt alle Platten zurueck (Jukebox wurde abgebaut). */
    List<ItemStack> removeAll(BlockRegistry.Key key) {
        Inventory inv = inventories.remove(key);
        List<ItemStack> out = new ArrayList<>();
        if (inv != null) {
            for (int i = 0; i < PlaylistLogic.INVENTORY_SLOTS; i++) {
                ItemStack s = inv.getItem(i);
                if (s != null && !s.getType().isAir()) out.add(s);
            }
            dirty = true;
        }
        return out;
    }

    void load() {
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file)) {
            Map<String, List<String>> raw = gson.fromJson(r, new TypeToken<Map<String, List<String>>>() {}.getType());
            if (raw == null) return;
            raw.forEach((k, list) -> {
                try {
                    Inventory inv = Bukkit.createInventory(null, SIZE, Component.text("PeakSound"));
                    for (int i = 0; i < Math.min(list.size(), PlaylistLogic.INVENTORY_SLOTS); i++) {
                        if (!list.get(i).isEmpty()) inv.setItem(i, ItemStack.deserializeBytes(Base64.getDecoder().decode(list.get(i))));
                    }
                    inventories.put(parse(k), inv);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Ungueltiges Jukebox-Inventar uebersprungen: " + k);
                }
            });
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "jukebox-inventories.json konnte nicht gelesen werden", e);
        }
    }

    void startAutosave() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) saveNow();
        }, 100L, 100L); // auf dem Hauptthread: Inventare duerfen nicht asynchron gelesen werden
    }

    synchronized void saveNow() {
        dirty = false;
        Map<String, List<String>> raw = new LinkedHashMap<>();
        inventories.forEach((k, inv) -> {
            List<String> list = new ArrayList<>(PlaylistLogic.INVENTORY_SLOTS);
            boolean any = false;
            for (int i = 0; i < PlaylistLogic.INVENTORY_SLOTS; i++) {
                ItemStack s = inv.getItem(i);
                if (s == null || s.getType().isAir()) {
                    list.add("");
                } else {
                    list.add(Base64.getEncoder().encodeToString(s.serializeAsBytes()));
                    any = true;
                }
            }
            if (any) raw.put(keyString(k), list);
        });
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(raw));
        } catch (IOException e) {
            dirty = true;
            plugin.getLogger().log(Level.SEVERE, "jukebox-inventories.json konnte nicht gespeichert werden", e);
        }
    }
}