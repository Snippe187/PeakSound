package de.peaksound.plugin;

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

    static Component prefix(Component body) {
        return Component.text("[PeakSound] ", NamedTextColor.GOLD).append(body.colorIfAbsent(NamedTextColor.GRAY));
    }

    static Component nonModHint(Player p) {
        return prefix(Component.text(german(p)
                ? "PeakSound-Bloecke und -Items brauchen die PeakSound-Mod (Fabric), damit Musik und 3D-Sound funktionieren. Ohne Mod siehst du nur normale Bloecke."
                : "PeakSound blocks and items need the PeakSound mod (Fabric) for music and 3D sound. Without the mod you only see regular blocks."));
    }

    static Component versionMismatch(Player p, int serverVersion, int clientVersion) {
        return prefix(Component.text(german(p)
                ? "Mod und Plugin passen nicht zusammen (Plugin-Protokoll " + serverVersion + ", Mod-Protokoll " + clientVersion + "). Bitte aktualisiere die Mod."
                : "Mod and plugin do not match (plugin protocol " + serverVersion + ", mod protocol " + clientVersion + "). Please update the mod."));
    }

    static Component tunerInfo(Player p, String blockName, int x, int y, int z) {
        return Component.text(blockName, NamedTextColor.GOLD)
                .append(Component.text(" (" + x + ", " + y + ", " + z + ")", NamedTextColor.GRAY));
    }

    static String blockName(Player p, de.peaksound.protocol.PeakBlockType type) {
        boolean de = german(p);
        return switch (type) {
            case JUKEBOX -> "PeakSound-Jukebox";
            case SPEAKER -> de ? "Lautsprecher" : "Speaker";
        };
    }
}
