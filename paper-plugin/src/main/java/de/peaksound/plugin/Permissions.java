package de.peaksound.plugin;

import org.bukkit.entity.Player;

/**
 * Wer darf was. Alle normalen Rechte stehen standardmaessig auf "erlaubt" (ohne Rechte-Plugin verhaelt sich der Server
 * wie bisher); die Song-Verwaltung ist Operatoren vorbehalten (Recht {@code peaksound.admin}, standardmaessig OP).
 * Rechte: peaksound.upload, peaksound.library, peaksound.library.manage.own, peaksound.library.manage.any,
 * peaksound.queue, peaksound.vote, peaksound.place, peaksound.radio, peaksound.limit.&lt;n&gt; / peaksound.limit.unlimited.
 */
final class Permissions {
    private Permissions() {}

    /** Wird bei JEDER Verwaltungs-Aktion neu geprueft, nie aus dem Client-Zustand abgeleitet. */
    static boolean admin(Player player) {
        return player.isOnline() && player.hasPermission("peaksound.admin");
    }

    /** Hat der Spieler dieses Recht? Fehlt es, bekommt er eine Meldung. */
    static boolean check(Player player, String node, String de, String en) {
        if (player.hasPermission(node)) return true;
        player.sendMessage(Messages.error(player, "Dafuer fehlt dir das Recht " + node + ". " + de, "You lack the permission " + node + ". " + en));
        return false;
    }

    /** Fremde Songs verwalten (umbenennen, loeschen): Operatoren oder Recht peaksound.library.manage.any. */
    static boolean manageAny(Player player) {
        return admin(player) || player.hasPermission("peaksound.library.manage.any");
    }
}