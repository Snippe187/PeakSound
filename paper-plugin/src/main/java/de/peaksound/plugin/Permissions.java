package de.peaksound.plugin;

import org.bukkit.entity.Player;

/** Wer darf was. Die Song-Verwaltung ist Operatoren vorbehalten (Recht {@code peaksound.admin}, standardmaessig OP). */
final class Permissions {
    private Permissions() {}

    /** Wird bei JEDER Verwaltungs-Aktion neu geprueft, nie aus dem Client-Zustand abgeleitet. */
    static boolean admin(Player player) {
        return player.isOnline() && player.hasPermission("peaksound.admin");
    }
}
