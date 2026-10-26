package de.peaksound.plugin;

import java.util.List;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Diagnose-Befehle fuer Server-Betreiber (nur Operatoren): {@code eqtest} setzt einen Comparator auf einen Pegel
 * und meldet den Ausgang zurueck (prueft, ob der Equalizer auf diesem Server funktioniert, er nutzt Server-Interna);
 * {@code place} setzt einen registrierten PeakSound-Block; {@code play} spielt einen gespeicherten Song an einer Jukebox.
 * Damit lassen sich Netz und Equalizer ohne Spielerbedienung testen.
 */
final class PeakSoundCommand implements CommandExecutor, TabCompleter {
    private final EqualizerService equalizer;
    private final BlockRegistry registry;
    private final SongStore songs;
    private final PlaybackManager playback;
    private final PeakItems items;

    PeakSoundCommand(EqualizerService equalizer, BlockRegistry registry, SongStore songs, PlaybackManager playback, PeakItems items) {
        this.equalizer = equalizer;
        this.registry = registry;
        this.songs = songs;
        this.playback = playback;
        this.items = items;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("peaksound.admin")) {
            sender.sendMessage("Dafuer fehlt dir die Berechtigung / You do not have permission.");
            return true;
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("eqtest")) {
            World world = sender instanceof Player p ? p.getWorld() : org.bukkit.Bukkit.getWorlds().get(0);
            try {
                Block block = world.getBlockAt(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                int level = Integer.parseInt(args[4]);
                int result = equalizer.debugSet(block, level);
                sender.sendMessage(switch (result) {
                    case -2 -> "Equalizer ist auf diesem Server ausgeschaltet (Server-Interna nicht gefunden).";
                    case -1 -> "Dort ist kein Comparator.";
                    default -> "Comparator-Ausgang: " + result;
                });
            } catch (NumberFormatException e) {
                sender.sendMessage("Koordinaten und Pegel muessen Zahlen sein.");
            }
            return true;
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("place")) {
            Block block = blockAt(sender, args);
            if (block == null) return true;
            de.peaksound.protocol.PeakBlockType type = args[1].equalsIgnoreCase("speaker")
                    ? de.peaksound.protocol.PeakBlockType.SPEAKER : de.peaksound.protocol.PeakBlockType.JUKEBOX;
            PeakBlocks.install(block, type, registry);
            sender.sendMessage("PeakSound-Block gesetzt.");
            return true;
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("play")) {
            Block block = blockAt(sender, args);
            if (block == null) return true;
            java.util.List<Song> all = songs.all();
            try {
                Song song = all.get(Integer.parseInt(args[4]) - 1);
                playback.insert(block, items.createWritten(new PeakItems.Written(song.hash(), song.title(), song.artist(), song.uploaderName())), song);
                sender.sendMessage("Spielt: " + song.title());
            } catch (RuntimeException e) {
                sender.sendMessage("Song-Nummer 1-" + all.size() + " erwartet.");
            }
            return true;
        }
        sender.sendMessage("/peaksound eqtest <x> <y> <z> <pegel 0-15>");
        sender.sendMessage("/peaksound place <jukebox|speaker> <x> <y> <z>");
        sender.sendMessage("/peaksound play <x> <y> <z> <song-nummer>");
        return true;
    }

    /** Block aus den Argumenten {@code <befehl> <x> <y> <z>} (die letzten drei Zahlen vor einem optionalen Wert). */
    private Block blockAt(CommandSender sender, String[] args) {
        World world = sender instanceof Player p ? p.getWorld() : org.bukkit.Bukkit.getWorlds().get(0);
        try {
            int offset = args[0].equalsIgnoreCase("place") ? 2 : 1;
            return world.getBlockAt(Integer.parseInt(args[offset]), Integer.parseInt(args[offset + 1]), Integer.parseInt(args[offset + 2]));
        } catch (NumberFormatException e) {
            sender.sendMessage("Koordinaten muessen Zahlen sein.");
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("eqtest", "place", "play") : List.of();
    }
}
