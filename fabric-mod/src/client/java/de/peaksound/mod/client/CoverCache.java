package de.peaksound.mod.client;

import com.mojang.blaze3d.platform.NativeImage;
import de.peaksound.protocol.Packet;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Cover-Bilder fuer den Platten-Tooltip. Ein Cover wird pro Sitzung einmal vom Server geholt, zu einer
 * Textur gemacht und danach aus dem Speicher gezeichnet. Ohne Cover bleibt das Standardbild.
 */
final class CoverCache {
    enum State { REQUESTED, READY, NONE }

    record Entry(State state, Identifier texture, int width, int height) {}

    private static final Map<String, Entry> entries = new HashMap<>();

    private CoverCache() {}

    /** Liefert den aktuellen Stand; beim ersten Aufruf wird das Cover angefragt. */
    static Entry get(String hash) {
        Entry e = entries.get(hash);
        if (e != null) return e;
        Entry requested = new Entry(State.REQUESTED, null, 0, 0);
        entries.put(hash, requested);
        ClientNetwork.send(new Packet.CoverRequest(hash));
        return requested;
    }

    static void onData(Packet.CoverData data) {
        if (!entries.containsKey(data.hash())) return; // nicht angefragt
        if (data.image().length == 0) {
            entries.put(data.hash(), new Entry(State.NONE, null, 0, 0));
            return;
        }
        try {
            NativeImage image = NativeImage.read(data.image()); // stb_image: PNG und JPEG
            Identifier id = Identifier.fromNamespaceAndPath("peaksound", "cover/" + data.hash().substring(0, 32));
            int w = image.getWidth();
            int h = image.getHeight();
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "peaksound cover", image));
            entries.put(data.hash(), new Entry(State.READY, id, w, h));
        } catch (IOException | RuntimeException e) {
            entries.put(data.hash(), new Entry(State.NONE, null, 0, 0)); // kaputtes Bild: Standardbild
        }
    }

    static void clear() {
        for (Entry e : entries.values()) {
            if (e.texture() != null) Minecraft.getInstance().getTextureManager().release(e.texture());
        }
        entries.clear();
    }
}
