# PeakSound – Plan

Ziel: Minecraft 26.2, Fabric-Client-Mod + Paper-Plugin + gemeinsames Protokoll. Architektur: siehe ARCHITECTURE.md.

## Phase 1 – Grundgerüst, Items, Blöcke, Rezepte, Peak-Tuner
- [x] Gradle-Multiprojekt (protocol, paper-plugin, fabric-mod), Wrapper, MIT-Lizenz
- [x] Protokoll: PacketCodec, Hello/HelloAck, Item-/Block-Typen, Schwert-Rechnung + Unit-Tests
- [x] Plugin: Items (leere/beschriebene Platte, Jukebox, Lautsprecher, Peak-Tuner)
- [x] Plugin: Rezepte inkl. Rezeptbuch, Schwert mit halber Haltbarkeit (auch Shift-Klick)
- [x] Plugin: Block-Registry (Platzieren, Abbauen, Schutz), Peak-Tuner-Info
- [x] Plugin: Handshake, Versions-Check, Hinweis für Spieler ohne Mod
- [x] Mod: Grundgerüst, Handshake, Creative-Tab, de/en Sprachdateien
- [x] Mod: Item-/Block-Darstellung (Modelle mit Vanilla-Texturen, Notenblock-Zustände)
- [x] Phase-1-Abnahme: Build, Server startet, Client verbindet, Checkliste (manuell bestätigt)

## Phase 2 – Upload, Speicherung, 3D-Audio im Multiplayer
- [x] Protokoll: Upload-/Stream-/Wiedergabe-Pakete, Chunk-Assembler, Richtungsprüfung, Tests
- [x] audio-core: Tags/Cover/Länge (jaudiotagger), MP3/FLAC/WAV-Decoder, FLAC-Encoder, ffmpeg-optional-Transcoder, Tests
- [x] Plugin: Song-Speicher (Hash), Upload mit Ticket/Rate-Limit/Quota/Speicher-Limit, Mehrkanal → gesperrt
- [x] Plugin: Platten (beschreiben, einlegen, entnehmen, kopieren), Wiedergabe-Verwaltung, Chunk-Streaming
- [x] Mod: Upload-Fenster (natives Dateifenster + Drag-and-Drop), Cache mit Hash-Prüfung, 3D-Wiedergabe (OpenAL, synchron)
- [ ] Manuelle Abnahme (Upload, Wiedergabe, Einstieg mitten im Song, Mehrkanal-Sperre)
- Bewusst verschoben: Wiedergabe startet nach vollständigem Download (progressives Streaming → Phase 8);
  Cover im Platten-Tooltip → Phase 3; Mehrkanal-Freigabe im OP-Menü → Phase 7; Lautsprecher/Surround → Phase 6.
## Phase 3 – Jukebox-Menü
- [x] Protokoll: JukeboxSettings (Grenzen, Bereinigung), ChainMath (Verkettung), Menü-/Cover-Pakete, Tests
- [x] audio-core: Ogg-Decoder (JOrbis), Analyse (Takt + 3 Frequenzbänder, wird in Phase 5 für den Equalizer genutzt), Tests
- [x] Plugin: Einstellungen je Jukebox (Name, Reichweite, Lautstärke, Kette, Partikel, Jetzt-läuft), serverseitig geprüft
- [x] Plugin: Noten-Partikel im Takt aus der Analyse, "Jetzt läuft: Titel – Interpret" (Actionbar, auch ohne Mod)
- [x] Plugin: Cover-Auslieferung; Mod: Cover im Platten-Tooltip (Mixin), Standardbild ohne Cover
- [x] Mod: Jukebox-Menü (Peak-Tuner Rechtsklick), Live-Änderung von Reichweite/Lautstärke bei laufender Wiedergabe
- [ ] Manuelle Abnahme
- Verschoben: Playlist-Bereich im Menü (Phase 4), Lautsprecher-Übersicht im Menü (Phase 6).
## Phase 4 – Hopper-Playlist und Kisten-Erweiterung
- [x] Protokoll: LoopMode, PlaylistLogic (next/skip/Shuffle/Reihenfolge), Playlist-Pakete, Tests
- [x] Plugin: Playlist aus Hopper an der Jukebox + Behälter am Hopper (Kisten, Doppelkisten, Fässer), Reihenfolge = Slots
- [x] Plugin: Song wählen, Skip, Stopp, Reihenfolge ändern (Platten tauschen), Loop (Aus/Playlist/Song), Shuffle
- [x] Mod: Playlist-Reiter im Jukebox-Menü (Seiten, Live-Aktualisierung)
- [ ] Manuelle Abnahme
- Hinweis: Eine von Hand eingelegte Platte hat Vorrang vor der Playlist.
## Phase 5 – Redstone-Verbund und Comparator-Equalizer
- [x] Protokoll: DustNetwork (Netz-Suche mit Limits, Steigungen), SyncMath (Uhr, Position, Anführer), Tests
- [x] Plugin: Netz-Cache (nur bei Blockänderungen neu, Stichprobe alle 10 s), Limits in der Config
- [x] Plugin: Jukeboxen eines Netzes spielen synchron den Song des Anführers (jede mit eigener Reichweite/Lautstärke)
- [x] Plugin: Comparator-Equalizer (N Bass, O Mitten, S Höhen, W Gesamt) aus der Upload-Analyse, per Server-Interna
- [x] Diagnose-Befehle für Operatoren (`/peaksound eqtest|place|play`), Equalizer und Netz am Testserver geprüft
- [ ] Manuelle Abnahme
- Hinweis: Der Comparator-Ausgang nutzt Paper-Interna (Reflection). Fehlen sie, schaltet sich nur der Equalizer ab.
## Phase 6 – Lautsprecher und Surround
- [x] Protokoll: 12 Kanäle (7.1.4), Upmix und Mehrkanal-Zuordnung (ChannelLayout, WAV- und Vorbis-Reihenfolge), ChannelMixer, Lautsprecher-Pakete, Tests
- [x] Plugin: Lautsprecher am Redstone-Netz, Nummern je System (auf der Textur), Standard-Kanäle, eigene Einstellungen
- [x] Plugin: jeder Lautsprecher eine eigene Schallquelle (Jukebox schweigt, wenn Lautsprecher im Netz sind)
- [x] Mod: Zahlen-Overlays auf dem Notenblock (25 Nummern), Lautsprecher-Menü, Reiter „Lautsprecher“ im Jukebox-Menü
- [x] Mod: Mehrquellen-Wiedergabe (ein Decoder, je Lautsprecher eine OpenAL-Quelle mit eigener Kanalmischung)
- [ ] Manuelle Abnahme
- Grenze: Quelldateien haben höchstens 8 Kanäle (7.1), weil FLAC und Vorbis nicht mehr zulassen. Die Decken-Lautsprecher bekommen abgeleitete Signale.
## Phase 7 – OP-Menü, Freigabe-System, Limits
- [x] Protokoll: Admin-Pakete (Liste, Aktionen, Operator-Status), LimitLogic, Tests (Richtung, Bereiche, Paketgröße)
- [x] Plugin: AdminService mit Rechteprüfung bei jeder Aktion (`peaksound.admin`, Standard OP), Audit-Log, Rate-Limit
- [x] Plugin: Songs löschen, gesperrte Mehrkanal-Dateien freigeben oder ablehnen (löscht), Uploader wird benachrichtigt
- [x] Plugin: Limits pro Spieler, Standard-Limit und Speicher-Limit im Spiel einstellbar (`limits.json`, Vorrang vor der Config)
- [x] Plugin: Operator-Status folgt /op und /deop, gesperrte Songs streamen nur an Admins
- [x] Mod: Button im Escape-Menü (nur Operatoren), OP-Menü mit Reitern Songs, Freigabe, Limits, Vorhören
- [ ] Manuelle Abnahme
## Phase 8 – Optimierungen und Feinschliff
- [x] Streaming: Wiedergabe startet nach den ersten Chunks (Decoder lesen von einem wachsenden Strom, alle Formate über JOrbis/JLayer/jFLAC)
- [x] Client-Cache mit Hash-Prüfung auch beim Lesen (kaputte Dateien werden verworfen und neu geladen)
- [x] Server-Song-Cache im Arbeitsspeicher (LRU 64 MB) statt Festplattenzugriff je Chunk
- [x] PeakSound-Lautstärkeregler in den Minecraft-Soundeinstellungen (Mixin), pro Spieler gespeichert
- [x] Verbindungsanzeige: Partikel-Linien Jukebox ↔ Lautsprecher/Jukeboxen mit dem Peak-Tuner in der Hand
- [x] Einstellungen kopieren und einfügen (Shift-Rechtsklick mit Peak-Tuner, Jukebox und Lautsprecher)
- [x] Doku: README, Installation (Server, Client, Lunar), Build, Testlisten, Lizenzen, kommentierte Beispiel-Config
- [ ] Manuelle Abnahme
- Offen für Phase 9: Strobo-Option liegt schon in den Client-Einstellungen (`peaksound-client.json`), das Menü dafür kommt mit den Scheinwerfern.

## Phase 9 – DJ-Pult und Scheinwerfer
- [x] Protokoll: LightSettings, DjSettings, DjMath (Equal-Power-Crossfader), 11 neue Pakete, DustNetwork (Pult verbindet keine Netze), Tests
- [x] Audio-Kern: LightLogic (Modi, Effekte, Farbwechsel, Lichtstufe) aus derselben Analyse wie der Equalizer, Tests
- [x] Plugin: DJ-Pult (Rezept mit 2 PeakSound-Jukeboxen, zwei Decks über Staub, Crossfader, Überblendzeit, Decklautstärken, Song je Deck aus der Playlist)
- [x] Plugin: Überblenden synchron für alle Spieler (ein Paket mit Dauer, der Client rampt selbst)
- [x] Plugin: Scheinwerfer (Rezept ergibt 2, an Jukebox-Netz, echtes Licht per Lichtblock, Aufräumen nach Absturz)
- [x] Mod: DJ-Menü, Scheinwerfer-Menü (Farben, Modus, Effekt, Band, Richtung), Strahl aus Partikeln, Strobo-Warnung und Option in den Soundeinstellungen
- [ ] Manuelle Abnahme

## Phase 10 – Simple Voice Chat und Mikrofon
- [x] Kompatibilität geprüft: Simple Voice Chat (Bukkit/Paper) 2.6.23 und neuer unterstützt Minecraft 26.2; API 2.6.25 (Maven maven.maxhenkel.de)
- [x] Optionale Abhängigkeit (softdepend): ohne Simple Voice Chat läuft alles andere unverändert, der Mikrofon-Block tut dann nichts (Menü weist darauf hin)
- [x] Protokoll: MicSettings, Pakete OpenMicMenu/MicUpdate, Tests
- [x] Plugin: Mikrofon-Block (Rezept, Redstone-Netz), Zuordnung Spieler → Mikrofone auf dem Hauptthread, Stimme über angeschlossene Lautsprecher (3D, je Lautsprecher ein ortsgebundener Kanal), Lautstärke, Ducking der Musik
- [x] Mod: Mikrofon-Menü (an/aus, Sprechreichweite, Lautstärke, Ducking)
- [ ] Manuelle Abnahme (braucht Simple Voice Chat auf Server und Client)

## Phase 11 – Song-Bibliothek, Fernbedienung, Ausgänge
- [x] Texturen, Rezeptbuch-Reiter, Peak-Tuner-Träger, einstellbare Songlänge, Debug-Bericht, Handshake-Fix
- [x] Song-Bibliothek (Block, Suche, Schreiben auf leere Platten, Server prüft alles)
- [x] Musik-Fernbedienung, Pause/Zurück, Redstone-Steuerung für Lautsprecher, Mikrofon, DJ-Pult
- [x] Jukebox-Ausgänge pro Seite (Bass/Mitten/Höhen/gesamt/läuft/Song-Ende/Takt) mit 3D-Würfel im Menü
- [ ] Manuelle Abnahme
