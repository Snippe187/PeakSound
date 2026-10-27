package de.peaksound.plugin;

import de.peaksound.protocol.Packet.UploadReject;
import de.peaksound.protocol.PeakBlockType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Chat-Texte fuer Spieler. Spieler ohne Mod koennen keine Uebersetzungsschluessel aufloesen,
 * deshalb waehlt das Plugin Deutsch oder Englisch anhand der Client-Sprache.
 */
final class Messages {
    private Messages() {}

    private static boolean german(Player p) {
        return p.locale().getLanguage().equals("de");
    }

    private static String t(Player p, String de, String en) {
        return german(p) ? de : en;
    }

    static Component prefix(Component body) {
        return Component.text("[PeakSound] ", NamedTextColor.GOLD).append(body.colorIfAbsent(NamedTextColor.GRAY));
    }

    static Component info(Player p, String de, String en) {
        return prefix(Component.text(t(p, de, en)));
    }

    static Component error(Player p, String de, String en) {
        return prefix(Component.text(t(p, de, en), NamedTextColor.RED));
    }

    static Component nonModHint(Player p) {
        return info(p,
                "PeakSound-Bloecke und -Items brauchen die PeakSound-Mod (Fabric), damit Musik und 3D-Sound funktionieren. Ohne Mod siehst du nur normale Bloecke.",
                "PeakSound blocks and items need the PeakSound mod (Fabric) for music and 3D sound. Without the mod you only see regular blocks.");
    }

    static Component versionMismatch(Player p, int serverVersion, int clientVersion) {
        return error(p,
                "Mod und Plugin passen nicht zusammen (Plugin-Protokoll " + serverVersion + ", Mod-Protokoll " + clientVersion + "). Bitte aktualisiere die Mod.",
                "Mod and plugin do not match (plugin protocol " + serverVersion + ", mod protocol " + clientVersion + "). Please update the mod.");
    }

    static Component tunerInfo(Player p, String blockName, int x, int y, int z) {
        return Component.text(blockName, NamedTextColor.GOLD)
                .append(Component.text(" (" + x + ", " + y + ", " + z + ")", NamedTextColor.GRAY));
    }

    static String blockName(Player p, PeakBlockType type) {
        return switch (type) {
            case JUKEBOX -> "PeakSound-Jukebox";
            case SPEAKER -> t(p, "Lautsprecher", "Speaker");
            case DJ_DESK -> t(p, "DJ-Pult", "DJ desk");
            case SPOTLIGHT -> t(p, "Scheinwerfer", "Spotlight");
            case MICROPHONE -> t(p, "Mikrofon", "Microphone");
            case SONG_LIBRARY -> t(p, "Song-Bibliothek", "Song library");
            case MUSIC_REMOTE -> t(p, "Musik-Fernbedienung", "Music remote");
            case RADIO_TRANSMITTER -> t(p, "Radio-Sender", "Radio transmitter");
            case RADIO_RECEIVER -> t(p, "Radio", "Radio");
        };
    }

    static Component needMod(Player p) {
        return error(p, "Zum Hochladen von Musik brauchst du die PeakSound-Mod.",
                "You need the PeakSound mod to upload music.");
    }

    static Component rejected(Player p, UploadReject.Reason reason, int arg) {
        return switch (reason) {
            case TOO_LONG -> error(p, "Der Song ist zu lang. Maximal " + arg / 60 + " Minuten sind erlaubt.",
                    "The song is too long. The maximum is " + arg / 60 + " minutes.");
            case TOO_LARGE -> error(p, "Die Datei ist zu gross.", "The file is too large.");
            case UNSUPPORTED_FORMAT -> error(p, "Dieses Dateiformat wird nicht unterstuetzt (erlaubt: MP3, FLAC, WAV, OGG).",
                    "This file format is not supported (allowed: MP3, FLAC, WAV, OGG).");
            case RATE_LIMIT -> error(p, "Bitte warte noch " + arg + " Sekunden bis zum naechsten Upload.",
                    "Please wait " + arg + " more seconds before the next upload.");
            case QUOTA_REACHED -> error(p, "Du hast dein Limit von " + arg + " Songs erreicht.",
                    "You have reached your limit of " + arg + " songs.");
            case STORAGE_FULL -> error(p, "Der Speicher des Servers ist voll.", "The server storage is full.");
            case NO_TICKET -> error(p, "Der Upload ist abgelaufen. Halte eine leere Platte in der Hand und klicke die Jukebox mit Shift erneut an.",
                    "The upload expired. Hold a blank record and shift-click the jukebox again.");
            case BUSY -> error(p, "Der Server verarbeitet gerade zu viele Uploads. Versuche es gleich noch einmal.",
                    "The server is busy with other uploads. Please try again shortly.");
            case CORRUPT -> error(p, "Die Datei ist beschaedigt oder konnte nicht gelesen werden.",
                    "The file is damaged or could not be read.");
            case INTERNAL -> error(p, "Interner Fehler beim Upload. Bitte informiere einen Admin.",
                    "Internal error during upload. Please tell an admin.");
        };
    }

    static Component needsApproval(Player p) {
        return info(p, "Diese Datei muss vom Server-Owner freigegeben werden. Versuche es später noch einmal.",
                "This file has to be approved by the server owner. Please try again later.");
    }

    static Component uploaded(Player p, String title) {
        return info(p, "Platte beschrieben: " + title, "Record written: " + title);
    }
}
