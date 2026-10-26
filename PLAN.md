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
## Phase 5 – Redstone-Verbund und Comparator-Equalizer
## Phase 6 – Lautsprecher und Surround
## Phase 7 – OP-Menü, Freigabe-System, Limits
## Phase 8 – Optimierungen und Feinschliff
## Phase 9 – DJ-Pult und Scheinwerfer
## Phase 10 – Simple Voice Chat und Mikrofon
