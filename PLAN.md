# PeakSound â€“ Plan

Ziel: Minecraft 26.2, Fabric-Client-Mod + Paper-Plugin + gemeinsames Protokoll. Architektur: siehe ARCHITECTURE.md.

## Phase 1 â€“ GrundgerÃ¼st, Items, BlÃ¶cke, Rezepte, Peak-Tuner
- [x] Gradle-Multiprojekt (protocol, paper-plugin, fabric-mod), Wrapper, MIT-Lizenz
- [x] Protokoll: PacketCodec, Hello/HelloAck, Item-/Block-Typen, Schwert-Rechnung + Unit-Tests
- [x] Plugin: Items (leere/beschriebene Platte, Jukebox, Lautsprecher, Peak-Tuner)
- [x] Plugin: Rezepte inkl. Rezeptbuch, Schwert mit halber Haltbarkeit (auch Shift-Klick)
- [x] Plugin: Block-Registry (Platzieren, Abbauen, Schutz), Peak-Tuner-Info
- [x] Plugin: Handshake, Versions-Check, Hinweis fÃ¼r Spieler ohne Mod
- [x] Mod: GrundgerÃ¼st, Handshake, Creative-Tab, de/en Sprachdateien
- [x] Mod: Item-/Block-Darstellung (Modelle mit Vanilla-Texturen, Notenblock-ZustÃ¤nde)
- [x] Phase-1-Abnahme: Build, Server startet, Client verbindet, Checkliste (manuell bestÃ¤tigt)

## Phase 2 â€“ Upload, Speicherung, 3D-Audio im Multiplayer
- [x] Protokoll: Upload-/Stream-/Wiedergabe-Pakete, Chunk-Assembler, RichtungsprÃ¼fung, Tests
- [x] audio-core: Tags/Cover/LÃ¤nge (jaudiotagger), MP3/FLAC/WAV-Decoder, FLAC-Encoder, ffmpeg-optional-Transcoder, Tests
- [x] Plugin: Song-Speicher (Hash), Upload mit Ticket/Rate-Limit/Quota/Speicher-Limit, Mehrkanal â†’ gesperrt
- [x] Plugin: Platten (beschreiben, einlegen, entnehmen, kopieren), Wiedergabe-Verwaltung, Chunk-Streaming
- [x] Mod: Upload-Fenster (natives Dateifenster + Drag-and-Drop), Cache mit Hash-PrÃ¼fung, 3D-Wiedergabe (OpenAL, synchron)
- [ ] Manuelle Abnahme (Upload, Wiedergabe, Einstieg mitten im Song, Mehrkanal-Sperre)
- Bewusst verschoben: Wiedergabe startet nach vollstÃ¤ndigem Download (progressives Streaming â†’ Phase 8);
  Cover im Platten-Tooltip â†’ Phase 3; Mehrkanal-Freigabe im OP-MenÃ¼ â†’ Phase 7; Lautsprecher/Surround â†’ Phase 6.
## Phase 3 â€“ Jukebox-MenÃ¼
- [x] Protokoll: JukeboxSettings (Grenzen, Bereinigung), ChainMath (Verkettung), MenÃ¼-/Cover-Pakete, Tests
- [x] audio-core: Ogg-Decoder (JOrbis), Analyse (Takt + 3 FrequenzbÃ¤nder, wird in Phase 5 fÃ¼r den Equalizer genutzt), Tests
- [x] Plugin: Einstellungen je Jukebox (Name, Reichweite, LautstÃ¤rke, Kette, Partikel, Jetzt-lÃ¤uft), serverseitig geprÃ¼ft
- [x] Plugin: Noten-Partikel im Takt aus der Analyse, "Jetzt lÃ¤uft: Titel â€“ Interpret" (Actionbar, auch ohne Mod)
- [x] Plugin: Cover-Auslieferung; Mod: Cover im Platten-Tooltip (Mixin), Standardbild ohne Cover
- [x] Mod: Jukebox-MenÃ¼ (Peak-Tuner Rechtsklick), Live-Ã„nderung von Reichweite/LautstÃ¤rke bei laufender Wiedergabe
- [ ] Manuelle Abnahme
- Verschoben: Playlist-Bereich im MenÃ¼ (Phase 4), Lautsprecher-Ãœbersicht im MenÃ¼ (Phase 6).
## Phase 4 â€“ Hopper-Playlist und Kisten-Erweiterung
## Phase 5 â€“ Redstone-Verbund und Comparator-Equalizer
## Phase 6 â€“ Lautsprecher und Surround
## Phase 7 â€“ OP-MenÃ¼, Freigabe-System, Limits
## Phase 8 â€“ Optimierungen und Feinschliff
## Phase 9 â€“ DJ-Pult und Scheinwerfer
## Phase 10 â€“ Simple Voice Chat und Mikrofon

