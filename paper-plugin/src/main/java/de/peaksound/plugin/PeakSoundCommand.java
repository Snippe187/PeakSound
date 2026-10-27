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
 * {@code place} setzt einen registrierten PeakSound-Block; {@code play} spielt einen gespeicherten Song an einer Jukebox;
 * {@code speaker} legt den Kanal eines Lautsprechers fest.
 * Damit lassen sich Netz und Equalizer ohne Spielerbedienung testen.
 */
final class PeakSoundCommand implements CommandExecutor, TabCompleter {
    private final EqualizerService equalizer;
    private final BlockRegistry registry;
    private final SongStore songs;
    private final PlaybackManager playback;
    private final PeakItems items;
    private final SpeakerService speakers;
    private final DjService dj;
    private final LightService lights;
    private final DebugService debug;
    private final LibraryService library;
    private final RadioService radio;
    private final StatsService stats;
    private final BackupService backup;
    private final RemoteService remote;
    private final JukeboxMenuService jukeboxMenu;
    private final JukeboxSettingsStore jukeboxSettings;

    PeakSoundCommand(EqualizerService equalizer, BlockRegistry registry, SongStore songs, PlaybackManager playback, PeakItems items,
                     SpeakerService speakers, DjService dj, LightService lights, DebugService debug, LibraryService library, RemoteService remote, JukeboxSettingsStore jukeboxSettings, JukeboxMenuService jukeboxMenu, RadioService radio,
                     StatsService stats, BackupService backup) {
        this.stats = stats;
        this.backup = backup;
        this.radio = radio;
        this.jukeboxMenu = jukeboxMenu;
        this.remote = remote;
        this.jukeboxSettings = jukeboxSettings;
        this.library = library;
        this.debug = debug;
        this.dj = dj;
        this.lights = lights;
        this.speakers = speakers;
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
        // /peaksound stats | backup | backups | restore <Name>: Statistik und Sicherungen (siehe docs/INSTALL.md)
        if (args.length == 1 && args[0].equalsIgnoreCase("stats")) {
            sender.sendMessage(stats.describe());
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("backup")) {
            sender.sendMessage("Sicherung wird erstellt ...");
            org.bukkit.Bukkit.getAsyncScheduler().runNow(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(PeakSoundCommand.class), task -> {
                try {
                    String name = backup.backupNow("manuell");
                    sender.sendMessage("Sicherung fertig: backups/" + name + " (" + backup.sizeOf(name) / 1048576 + " MB)");
                } catch (java.io.IOException | RuntimeException e) {
                    sender.sendMessage("Sicherung fehlgeschlagen: " + e.getMessage());
                }
            });
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("backups")) {
            var names = backup.list();
            sender.sendMessage(names.isEmpty() ? "Keine Sicherungen vorhanden." : "Sicherungen (neueste zuerst):");
            for (String n : names) sender.sendMessage("  " + n + " (" + backup.sizeOf(n) / 1048576 + " MB)");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("restore")) {
            try {
                backup.stageRestore(args[1]);
                sender.sendMessage("Bereit: " + args[1] + " wird beim naechsten Serverstart eingespielt (vorher entsteht eine Sicherung des jetzigen Stands). Server jetzt neu starten.");
            } catch (java.io.IOException e) {
                sender.sendMessage(e.getMessage());
            }
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
            de.peaksound.protocol.PeakBlockType type = switch (args[1].toLowerCase()) {
                case "speaker" -> de.peaksound.protocol.PeakBlockType.SPEAKER;
                case "dj_desk", "dj" -> de.peaksound.protocol.PeakBlockType.DJ_DESK;
                case "spotlight" -> de.peaksound.protocol.PeakBlockType.SPOTLIGHT;
                case "microphone", "mic" -> de.peaksound.protocol.PeakBlockType.MICROPHONE;
                case "library", "song_library" -> de.peaksound.protocol.PeakBlockType.SONG_LIBRARY;
                case "remote", "music_remote" -> de.peaksound.protocol.PeakBlockType.MUSIC_REMOTE;
                case "transmitter", "radio_transmitter" -> de.peaksound.protocol.PeakBlockType.RADIO_TRANSMITTER;
                case "receiver", "radio_receiver" -> de.peaksound.protocol.PeakBlockType.RADIO_RECEIVER;
                default -> de.peaksound.protocol.PeakBlockType.JUKEBOX;
            };
            PeakBlocks.install(block, type, registry);
            sender.sendMessage("PeakSound-Block gesetzt.");
            return true;
        }
        // /peaksound light X Y Z <rrggbb[,rrggbb...]> <steady|blink|beat> <none|color_cycle|strobe|sweep|pulse> <bass|mid|high> <yaw> <pitch> <speed>
        if (args.length == 11 && args[0].equalsIgnoreCase("light")) {
            Block block = blockAt(sender, args);
            if (block == null) return true;
            try {
                java.util.List<Integer> colors = new java.util.ArrayList<>();
                for (String hex : args[4].split(",")) colors.add(Integer.parseInt(hex, 16));
                var settings = new de.peaksound.protocol.LightSettings(colors, 100,
                        de.peaksound.protocol.LightSettings.LightMode.valueOf(args[5].toUpperCase()),
                        de.peaksound.protocol.LightSettings.LightEffect.valueOf(args[6].toUpperCase()),
                        de.peaksound.protocol.LightSettings.LightBand.valueOf(args[7].toUpperCase()),
                        Integer.parseInt(args[8]), Integer.parseInt(args[9]), Integer.parseInt(args[10])).sanitized();
                lights.apply(block, settings);
                sender.sendMessage("Scheinwerfer eingestellt.");
            } catch (IllegalArgumentException e) {
                sender.sendMessage("/peaksound light X Y Z <rrggbb,...> <steady|blink|beat> <none|color_cycle|strobe|sweep|pulse> <bass|mid|high> <yaw> <pitch> <speed>");
            }
            return true;
        }
        // /peaksound libopen <spieler> <x> <y> <z>: oeffnet die Bibliothek fuer einen Spieler (Test)
        if (args.length == 5 && args[0].equalsIgnoreCase("libopen")) {
            Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
            Block block = blockAt(sender, new String[] {"libopen", args[2], args[3], args[4]});
            if (target == null || block == null) return true;
            library.open(target, block);
            return true;
        }
        // /peaksound libwrite <spieler> <x> <y> <z> <titelteil>: schreibt einen Song wie ein Klick im Menue (Test)
        if (args.length == 6 && args[0].equalsIgnoreCase("libwrite")) {
            Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
            Block block = blockAt(sender, new String[] {"libwrite", args[2], args[3], args[4]});
            Song song = songs.all().stream().filter(s -> s.title().toLowerCase().contains(args[5].toLowerCase())).findFirst().orElse(null);
            if (target == null || block == null || song == null) { sender.sendMessage("Spieler, Block oder Song nicht gefunden."); return true; }
            library.onWrite(target, new de.peaksound.protocol.Packet.LibraryWrite(block.getX(), block.getY(), block.getZ(), song.hash()));
            sender.sendMessage("Geschrieben: " + song.title());
            return true;
        }
        // Tests: /peaksound remote X Y Z <aktion> | /peaksound jukebox X Y Z | /peaksound port X Y Z <n|e|s|w> <funktion>
        if (args.length == 5 && args[0].equalsIgnoreCase("remote")) {
            Block block = blockAt(sender, args);
            if (block == null || registry.get(block).filter(t -> t == de.peaksound.protocol.PeakBlockType.MUSIC_REMOTE).isEmpty()) {
                sender.sendMessage("Dort ist keine Fernbedienung.");
                return true;
            }
            try {
                remote.put(block, new de.peaksound.protocol.RemoteSettings(de.peaksound.protocol.RemoteAction.valueOf(args[4].toUpperCase()), false));
                sender.sendMessage("Fernbedienung eingestellt: " + args[4]);
            } catch (IllegalArgumentException e) {
                sender.sendMessage("Aktionen: " + java.util.Arrays.toString(de.peaksound.protocol.RemoteAction.values()));
            }
            return true;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("jukebox")) {
            Block block = blockAt(sender, args);
            if (block == null) return true;
            var cfg = jukeboxSettings.get(block);
            sender.sendMessage("spielt=" + playback.isPlaying(block) + " pausiert=" + playback.isPaused(block) + " Lautstaerke=" + cfg.volume()
                    + " Shuffle=" + cfg.shuffle() + " Wiederholen=" + cfg.loop() + " Ausgaenge(N,O,S,W)=" + cfg.ports());
            return true;
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("port")) {
            Block block = blockAt(sender, args);
            if (block == null) return true;
            try {
                int side = "neswud".indexOf(args[4].toLowerCase().charAt(0));
                if (side < 0) throw new IllegalArgumentException();
                playback.applySettings(block, jukeboxSettings.get(block).withPort(side, de.peaksound.protocol.PortFunction.valueOf(args[5].toUpperCase())));
                sender.sendMessage("Ausgang " + args[4] + " = " + args[5]);
            } catch (IllegalArgumentException e) {
                sender.sendMessage("Seite n/e/s/w/u/d, Funktion: " + java.util.Arrays.toString(de.peaksound.protocol.PortFunction.values()));
            }
            return true;
        }
        // /peaksound give <spieler> <item-id> [anzahl]: gibt ein PeakSound-Item (Test und Admin-Hilfe)
        if ((args.length == 3 || args.length == 4) && args[0].equalsIgnoreCase("give")) {
            Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
            var type = de.peaksound.protocol.PeakItemType.byId(args[2].toLowerCase());
            if (target == null || type.isEmpty()) {
                sender.sendMessage("Spieler oder Item nicht gefunden. Items: " + java.util.Arrays.stream(de.peaksound.protocol.PeakItemType.values()).map(de.peaksound.protocol.PeakItemType::id).toList());
                return true;
            }
            int amount = args.length == 4 ? Math.max(1, Math.min(64, Integer.parseInt(args[3]))) : 1;
            target.getInventory().addItem(items.create(type.get(), amount)).values().forEach(rest -> target.getWorld().dropItem(target.getLocation(), rest));
            sender.sendMessage(amount + "x " + type.get().id() + " an " + target.getName() + " gegeben.");
            return true;
        }
        // /peaksound menu <spieler> <jukebox|remote|dj> <x> <y> <z>: oeffnet ein Menue fuer einen Spieler (Test)
        if (args.length == 6 && args[0].equalsIgnoreCase("menu")) {
            Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
            Block block = blockAt(sender, new String[] {"menu", args[3], args[4], args[5]});
            if (target == null || block == null) return true;
            switch (args[2].toLowerCase()) {
                case "jukebox" -> jukeboxMenu.open(target, block);
                case "remote" -> remote.open(target, block);
                case "dj" -> dj.open(target, block);
                case "radio" -> registry.get(block).ifPresent(t -> radio.open(target, block, t));
                default -> sender.sendMessage("jukebox, remote, dj oder radio");
            }
            return true;
        }
        // /peaksound radio <x> <y> <z> <frequenz in Zehntel-MHz> [aus]: stellt einen Sender/Empfaenger ein (Test)
        if ((args.length == 5 || args.length == 6) && args[0].equalsIgnoreCase("radio")) {
            Block block = blockAt(sender, args);
            if (block == null) return true;
            var cfg = radio.settingsOf(block).withFrequency(Integer.parseInt(args[4])).withEnabled(args.length == 5);
            radio.put(block, cfg);
            sender.sendMessage("Radio " + cfg);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("debugdump")) { // Server-Bericht in der Konsole (ohne Spieler)
            sender.sendMessage(debug.dump());
            return true;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("dj")) {
            Block block = blockAt(sender, args);
            if (block == null) return true;
            sender.sendMessage(dj.describe(block));
            return true;
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("play")) {
            Block block = blockAt(sender, args);
            if (block == null) return true;
            java.util.List<Song> all = songs.all();
            try {
                // Zahl = Position in der Songliste, sonst Teil des Titels (die Reihenfolge der Liste ist nicht stabil)
                Song song = args[4].matches("\\d+") ? all.get(Integer.parseInt(args[4]) - 1)
                        : all.stream().filter(s -> s.title().toLowerCase().contains(args[4].toLowerCase())).findFirst().orElseThrow();
                playback.insert(block, items.createWritten(new PeakItems.Written(song.hash(), song.title(), song.artist(), song.uploaderName())), song);
                playback.setLoop(block, de.peaksound.protocol.LoopMode.ONE); // Test-Songs laufen in Dauerschleife
                sender.sendMessage("Spielt (Dauerschleife): " + song.title());
            } catch (RuntimeException e) {
                sender.sendMessage("Song-Nummer 1-" + all.size() + " erwartet.");
            }
            return true;
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("speaker")) {
            Block block = blockAt(sender, args);
            if (block == null) return true;
            if (registry.get(block).filter(t -> t == de.peaksound.protocol.PeakBlockType.SPEAKER).isEmpty()) {
                sender.sendMessage("Dort ist kein PeakSound-Lautsprecher.");
                return true;
            }
            try {
                de.peaksound.protocol.SpeakerChannel channel = de.peaksound.protocol.SpeakerChannel.valueOf(args[4].toUpperCase());
                speakers.update(block, new de.peaksound.protocol.SpeakerSettings(channel.shortName(), channel, 100));
                sender.sendMessage("Lautsprecher " + channel.shortName() + " gesetzt.");
            } catch (IllegalArgumentException e) {
                sender.sendMessage("Kanal: " + java.util.Arrays.toString(de.peaksound.protocol.SpeakerChannel.values()));
            }
            return true;
        }
        sender.sendMessage("/peaksound speaker <x> <y> <z> <kanal>");
        sender.sendMessage("/peaksound dj <x> <y> <z>   (zeigt Decks und Lautstaerken eines DJ-Pults)");
        sender.sendMessage("/peaksound eqtest <x> <y> <z> <pegel 0-15>");
        sender.sendMessage("/peaksound place <jukebox|speaker|dj_desk|spotlight> <x> <y> <z>");
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
        if (args.length == 2 && args[0].equalsIgnoreCase("restore")) return backup.list();
        return args.length == 1 ? List.of("eqtest", "place", "play", "speaker", "dj", "light", "remote", "jukebox", "port", "give", "radio", "stats", "backup", "backups", "restore") : List.of();
    }
}
