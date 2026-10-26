package de.peaksound.plugin;

import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PeakBlockType;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Oeffnet das Lautsprecher-Menue und uebernimmt dessen (serverseitig geprueften) Aenderungen. */
final class SpeakerMenuService {
    private final PeakSoundPlugin plugin;
    private final EditAccess access;
    private final SpeakerService speakers;
    private final JukeboxSettingsStore jukeboxSettings;

    SpeakerMenuService(PeakSoundPlugin plugin, EditAccess access, SpeakerService speakers, JukeboxSettingsStore jukeboxSettings) {
        this.plugin = plugin;
        this.access = access;
        this.speakers = speakers;
        this.jukeboxSettings = jukeboxSettings;
    }

    /** Menue oeffnen: Einstellungen, Nummer und Name der verbundenen Jukebox (leer/0, wenn nicht verbunden). */
    void open(Player player, Block block) {
        SpeakerService.Speaker speaker = speakers.speaker(block);
        int number = speaker == null ? 0 : speaker.number();
        String jukebox = "";
        if (speaker != null) {
            String name = jukeboxSettings.get(speaker.jukebox()).name();
            jukebox = name.isBlank() ? speaker.jukebox().x() + ", " + speaker.jukebox().y() + ", " + speaker.jukebox().z() : name;
        }
        Net.send(plugin, player, new Packet.OpenSpeakerMenu(block.getX(), block.getY(), block.getZ(),
                speaker != null ? speaker.settings() : speakers.settingsOf(block), number, jukebox));
    }

    void onUpdate(Player player, Packet.SpeakerUpdate update) {
        Block block = access.validated(player, update.x(), update.y(), update.z(), PeakBlockType.SPEAKER);
        if (block == null) return;
        speakers.update(block, update.settings().sanitized());
    }
}
