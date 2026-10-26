# PeakSound – Architekturvorschlag (zur Freigabe)

Stand: Entwurf, noch kein Code. APIs für Minecraft 26.2 werden in Phase 1 gegen die echten Jars geprüft.

## 0. Versionshinweis
Mojang nutzt seit 2026 das Schema `26.x` (kein `1.` mehr). Gemeint ist daher vermutlich **Minecraft 26.2**.
Gefunden (Drittquellen, vor Phase 1 gegen fabricmc.net / modrinth / papermc.io prüfen): Fabric API `0.16x.x+26.2`, Fabric Loader 0.19.x, Paper 26.2 (Beta-Builds).
Simple Voice Chat: letzte gefundene Paper-Version 2.6.11 listet nur bis 1.21.11 → **Kompatibilität mit 26.2 ist offen** (siehe Phase 10).

## 1. Ordnerstruktur (Gradle Multi-Projekt, Java 25, MIT)
```
PeakSound/
  PLAN.md  ARCHITECTURE.md  README.md  LICENSE
  settings.gradle.kts  build.gradle.kts  gradle.properties
  branding/                      Logo + Generator
  protocol/                      reine Java-Bibliothek, KEINE MC-Abhängigkeit
    Packets, Codec, ChunkAssembler, Limits, Playlist, ChainMath,
    SyncClock, NetworkGraph, SpeakerLayout, BandAnalysis (+ Unit-Tests)
  audio-core/                    Decoding/Analyse, KEINE MC-Abhängigkeit
    Decoder (MP3/FLAC/WAV/OGG), Metadata, Upmixer, BandAnalyzer, Transcoder (ffmpeg optional)
  fabric-mod/                    Client + gemeinsame Registrierung
    common/ (Blöcke, Items, BlockEntities, Rezepte, Payloads)
    client/ (Screens, Renderer, OpenAL-Engine, Cache, Upload-UI, Drag&Drop)
  paper-plugin/                  Server-Autorität
    storage/ upload/ network/ playback/ security/ config/ svc/ (optional)
```
Die Logik (Verkettung, Playlist, Limits, Netz, Sync) liegt in `protocol`/`audio-core` und ist ohne Minecraft testbar.

## 2. Wichtigste Architekturentscheidung: Wer hat die Blöcke?
Der Server ist Paper (Plugin), die Blöcke sind aber Mod-Blöcke. Ein Paper-Plugin kann keine neuen Block-IDs registrieren, ein Vanilla-Client würde sie nicht kennen.
**Vorschlag:** Plugin-Blöcke bestehen serverseitig aus **Vanilla-Trägerblöcken** (Jukebox → `JUKEBOX`, Lautsprecher → `NOTE_BLOCK`, Scheinwerfer → `REDSTONE_LAMP`, Pult/Mikro → `LECTERN`/`LIGHTNING_ROD`) plus Markierung in `PersistentDataContainer` (Block-Position → Typ/Config im Plugin-Speicher). Items sind Vanilla-Items mit Custom-Model-Data/`item_model` und Components.
Die Mod rendert die Blöcke per Client-seitigem Modell-/Textur-Austausch anhand der vom Plugin gemeldeten Positionen. Spieler ohne Mod sehen Notenblock/Jukebox und bekommen einen Chat-Hinweis.
Folge: Rezepte laufen im Plugin (Paper-Recipe-API, Rezeptbuch via `discoverRecipe`), die Mod-Jar braucht keine eigenen Registry-Einträge auf dem Server. Das ist die einzige Variante, die „Paper-Server + Fabric-Client + Spieler ohne Mod" erfüllt.
Konsequenz für das Diamantschwert-Rezept: eigener Recipe-Handler im Plugin (CraftItemEvent + PrepareItemCraftEvent), inkl. Shift-Klick.

## 3. Audio-Wiedergabe (Empfehlung zu Phase 10-Prüfauftrag)
Eigenes Client-Audio-System (OpenAL über LWJGL, bereits in Minecraft) statt Simple-Voice-Chat-Kanäle:
- SVC-Kanäle sind Mono/Opus 48 kHz, kein 7.1.4, begrenzte Bitrate, nur Spieler mit SVC-Mod, Position pro Kanal begrenzt → für Surround ungeeignet.
- Eigenes System: ein OpenAL-Source pro Lautsprecher, exakte Sync über Server-Zeitstempel, funktioniert ohne SVC.
SVC wird **nur für das Mikrofon** (Phase 10) optional genutzt: Plugin hört per `MicrophonePacketEvent` zu und leitet Frames als Locational-Audio an Spieler weiter. Fehlt SVC oder ist es inkompatibel, ist das Mikrofon-Menü ausgegraut.

## 4. Upload & Streaming
- Plugin-Messaging in Chunks (Client→Server max. 32 KiB pro Payload → 16-KiB-Chunks, Server→Client 1 MiB-Limit → 64-KiB-Chunks), mit Sequenznummer, Hash (SHA-256) und Backpressure (Server schickt ACK-Fenster).
- Decoding und Metadaten beim Client zur Vorabprüfung (Länge ≤ 4 min, Mehrkanal-Warnung), **Server prüft alles erneut** und transkodiert.
- Speicherformat: ffmpeg vorhanden → Opus/Ogg (Mehrkanal bleibt), sonst FLAC (Java-Encoder). Dateiname = Hash.
- Client-Cache `.minecraft/peaksound/cache/<hash>`; Wiedergabe startet nach den ersten Chunks (Stream-Decoder), Rest lädt nach.

## 5. Pakettypen (Kanal `peaksound:main`, Version im Handshake)
| Richtung | Paket | Inhalt |
|---|---|---|
| C→S | `Hello` | Mod-/Protokollversion |
| S→C | `HelloAck` | Protokollversion, OP-Flag, Limits, Features (ffmpeg, SVC) |
| C→S | `UploadStart` | Dateiname, Größe, Hash, Kanalzahl, Dauer, Titel/Interpret, Cover-Bytes (komprimiert, max. 256 KiB) |
| S→C | `UploadAccept` / `UploadReject` | Upload-ID, Fenster / Fehlercode (Länge, Limit, Sperre, Spam-Sperre, Speicher voll) |
| C→S | `UploadChunk` | Upload-ID, Index, Bytes |
| S→C | `UploadAck` / `UploadDone` | Fenster / Song-ID, Status (OK / WARTET_AUF_FREIGABE) |
| S→C | `SongMeta` | Song-ID, Hash, Titel, Interpret, Uploader, Kanäle, Dauer, Cover-Hash |
| C→S | `SongRequest` | Hash, ab Byte (Resume) |
| S→C | `SongChunk` | Hash, Index, Bytes, Ende-Flag |
| C→S | `CoverRequest` / S→C `CoverData` | für Tooltip |
| S→C | `PlaybackStart/Stop/Seek` | Jukebox-ID, Song, Startzeit (Server-Uhr), Position |
| S→C | `SpeakerLayout` | Jukebox-ID, Lautsprecher (Position, Kanal, Lautstärke), Reichweite, Volume |
| S→C | `BandLevels` | Jukebox-ID, Bass/Mitte/Höhe (nur für Client-Effekte; Redstone läuft serverseitig) |
| C→S | `OpenMenuRequest` / S→C `MenuState` | Block-Position, vollständiger Zustand |
| C→S | `MenuUpdate` | Block-Position, geänderte Felder (Server validiert + begrenzt) |
| C→S | `PlaylistAction` | Auswählen, Skip, Reihenfolge, Loop, Shuffle |
| C→S | `CopySettings` / `PasteSettings` | Block-Position |
| C→S | `AdminListSongs` / S→C `AdminSongList` | Songs, Status, Größen, Limits |
| C→S | `AdminAction` | Löschen, Freigeben, Ablehnen, Limit setzen; Server prüft OP erneut |
| C→S | `AdminPreview` / S→C `SongChunk` | Anhören gesperrter Dateien |
| S→C | `DjState`, `LightState` | Phase 9 |
| S→C | `Notice` | Chat-/Toast-Meldungen mit Übersetzungsschlüssel |

Alle Paketgrößen, Stringlängen und Listen werden im Codec hart begrenzt.

## 6. Datenmodelle (Plugin, JSON/SQLite im Datenordner)
- `Song { hash, title, artist, uploaderUuid, uploaderName, channels, durationMs, sizeBytes, status(OK|PENDING|REJECTED), coverHash, uploadedAt }`
- `PlayerQuota { uuid, maxSongs, lastUploadAt }`
- `JukeboxState { pos, name, range(1-30), volume(0-100), chainLinked, particles{on,amount,density,…}, showNowPlaying, loop, shuffle, playlistIndex, currentSong, startedAtMs }`
- `SpeakerState { pos, name, channel, volume, jukeboxRef, index }`
- `Network { id, members[], cached }` (Redstone-Staub-Graph, Cache, Neuberechnung nur bei Blockänderung, Max-Größe aus Config)
- Speicher: SQLite (eingebettet, eine Datei) für Metadaten, Audiodateien als `<hash>.opus|flac`.

## 7. Netz, Sync, Equalizer
- Synchronisation: Server sendet `startedAtServerNanos` + Songposition; Clients gleichen Uhr per Ping-Offset ab; spätere Spieler steigen bei `now - startedAt` ein.
- Equalizer: beim Upload vorberechnet (FFT, 3 Bänder, 20 Werte/s als 0-15), Plugin liest pro Tick aus dem Array → kein Rechnen im Tick-Loop.
- Redstone-Verbund: Union-Find/Flood-Fill über Redstone-Staub, gecacht je Netz, invalidiert bei `BlockPhysics`/Place/Break, Limit per Config.

## 8. Test-/Build-Strategie
JUnit 5 in `protocol` und `audio-core` (Codec, Limits, Chain-Rechnung, Playlist, Sync, Netz-Graph, Upmixing). Gradle-Task `build` baut Mod-Jar und Plugin-Jar; Testserver-Skript startet Paper lokal.

## 9. Offene Risiken (ehrlich)
1. Plugin-Blöcke als Trägerblöcke: Mod muss Models/Texturen clientseitig austauschen. Machbar, aber der Lautsprecher-Nummern-Renderer läuft nur bei Mod-Spielern.
2. Paper 26.2 ist laut Fund noch Beta, SVC-Kompatibilität unklar.
3. Cover im Item-Tooltip: Vanilla-Tooltips können keine Bilder. Mod-seitig per `TooltipComponent`-Rendering lösbar, Nicht-Mod-Spieler sehen nur Text.
4. Lunar Client: Fabric-Mods nur über dessen Mod-Loader; Funktion kann eingeschränkt sein und wird nicht von mir getestet.
5. Dolby Atmos: ausgeschlossen. 7.1.4 mit 12 Kanälen (z. B. FLAC/Opus Mehrkanal) wird unterstützt.
