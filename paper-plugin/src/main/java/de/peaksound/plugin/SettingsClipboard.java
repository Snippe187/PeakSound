package de.peaksound.plugin;

import de.peaksound.protocol.DjSettings;
import de.peaksound.protocol.JukeboxSettings;
import de.peaksound.protocol.LightSettings;
import de.peaksound.protocol.MicSettings;
import de.peaksound.protocol.PeakBlockType;
import de.peaksound.protocol.PeakItemType;
import de.peaksound.protocol.SpeakerSettings;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Einstellungen kopieren: Shift-Rechtsklick mit dem Peak-Tuner auf einen Block kopiert seine Einstellungen,
 * Shift-Rechtsklick auf einen gleichartigen anderen Block fuegt sie ein (der Name bleibt am Ziel). Shift-Rechtsklick
 * mit dem Tuner in die Luft leert die Zwischenablage; ein erneuter Shift-Klick auf den Quellblock kopiert neu.
 */
final class SettingsClipboard implements Listener {
    /** Was ein Spieler gerade kopiert hat. */
    private record Clip(PeakBlockType type, BlockRegistry.Key source, JukeboxSettings jukebox, SpeakerSettings speaker,
                LightSettings light, DjSettings dj, MicSettings microphone) {}

    private final PeakItems items;
    private final JukeboxSettingsStore jukeboxSettings;
    private final PlaybackManager playback;
    private final SpeakerService speakers;
    private final LightService lights;
    private final DjService dj;
    private final MicService mic;
    private final Map<UUID, Clip> clips = new ConcurrentHashMap<>();

    SettingsClipboard(PeakItems items, JukeboxSettingsStore jukeboxSettings, PlaybackManager playback, SpeakerService speakers,
                      LightService lights, DjService dj, MicService mic) {
        this.mic = mic;
        this.lights = lights;
        this.dj = dj;
        this.items = items;
        this.jukeboxSettings = jukeboxSettings;
        this.playback = playback;
        this.speakers = speakers;
    }

    /** Shift-Rechtsklick mit dem Tuner auf einen PeakSound-Block. */
    void use(Player player, Block block, PeakBlockType type) {
        BlockRegistry.Key key = BlockRegistry.Key.of(block);
        Clip clip = clips.get(player.getUniqueId());
        boolean paste = clip != null && clip.type() == type && !clip.source().equals(key);
        if (paste) {
            switch (type) {
                case JUKEBOX -> playback.applySettings(block, clip.jukebox().withName(jukeboxSettings.get(block).name()));
                case SPEAKER -> speakers.update(block, clip.speaker().withName(speakers.settingsOf(block).name()));
                case SPOTLIGHT -> lights.apply(block, clip.light());
                case DJ_DESK -> dj.put(block, clip.dj());
                case MICROPHONE -> mic.put(block, clip.microphone());
            }
            player.sendActionBar(Messages.info(player, "Einstellungen eingefuegt.", "Settings pasted."));
            return;
        }
        Clip copied = switch (type) {
            case JUKEBOX -> new Clip(type, key, jukeboxSettings.get(block), null, null, null, null);
            case SPEAKER -> new Clip(type, key, null, speakers.settingsOf(block), null, null, null);
            case SPOTLIGHT -> new Clip(type, key, null, null, lights.settingsOf(block), null, null);
            case DJ_DESK -> new Clip(type, key, null, null, null, dj.settingsOf(block), null);
            case MICROPHONE -> new Clip(type, key, null, null, null, null, mic.settingsOf(block));
        };
        clips.put(player.getUniqueId(), copied);
        player.sendActionBar(Messages.info(player,
                "Einstellungen kopiert. Shift-Rechtsklick auf einen anderen Block gleicher Art fuegt sie ein.",
                "Settings copied. Shift-right-click another block of the same kind to paste them."));
    }

    /** Shift-Rechtsklick mit dem Tuner in die Luft leert die Zwischenablage. */
    @EventHandler
    public void onAir(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR || event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!player.isSneaking() || items.typeOf(player.getInventory().getItemInMainHand())
                .filter(t -> t == PeakItemType.PEAK_TUNER).isEmpty()) return;
        if (clips.remove(player.getUniqueId()) != null) {
            player.sendActionBar(Messages.info(player, "Zwischenablage geleert.", "Clipboard cleared."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clips.remove(event.getPlayer().getUniqueId());
    }
}
