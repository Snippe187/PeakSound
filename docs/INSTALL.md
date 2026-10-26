# Installation

Du brauchst auf dem **Server** das Plugin und bei **jedem Spieler** die Mod. Ohne Mod kann man spielen, hört aber
keine Musik und sieht nur normale Blöcke.

## Server (Paper 26.2)

1. Paper 26.2 installieren (Build 135 oder neuer) und einmal starten.
2. `PeakSound-Paper-<version>.jar` in den Ordner `plugins/` legen.
3. Server starten. Beim **ersten Start** lädt Paper die Audio-Bibliotheken von Maven Central
   (jaudiotagger, JLayer, JOrbis, jFLAC, java-flac-encoder). Der Server braucht dafür einmal Internet.
4. Im Ordner `plugins/PeakSound/` entsteht die `config.yml` (siehe unten) und beim Betrieb `songs/`, `songs.json`,
   `blocks.yml`, `playback.yml`, `jukebox-settings.json`, `speakers.json` und `limits.json`.
   Diese Dateien bei Backups mitsichern.
5. **Optional ffmpeg:** Wenn `ffmpeg` im PATH liegt (oder in der Config als `upload.ffmpeg-path` steht), werden
   Uploads zu Ogg Vorbis komprimiert (ca. 3–4 MB pro Song statt 15–25 MB). Ohne ffmpeg bleiben OGG, FLAC und MP3
   unverändert und WAV wird zu FLAC. Beim Start steht im Log, was erkannt wurde.
6. Rechte: Die Song-Verwaltung ist Operatoren vorbehalten (Recht `peaksound.admin`, Standard OP). Jeder Spieler darf
   hochladen.

### Mikrofon-Block (optional)

Der Mikrofon-Block braucht [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) (Paper-Plugin ab Version 2.6.23 für Minecraft 26.2) im Ordner `plugins` und die passende Mod bei jedem Spieler. Ohne das Plugin läuft PeakSound unverändert, der Mikrofon-Block tut dann nichts (der Server-Log meldet das beim Start). Der Port von Simple Voice Chat (UDP 24454) muss erreichbar sein.

### Port und Firmen-Hoster

Es ist **kein zusätzlicher Port** nötig. Uploads und Downloads laufen über den normalen Spielport (Plugin-Messaging).

### Beispiel-Config

Die kommentierte Beispiel-Datei liegt als [config.example.yml](../config.example.yml) im Projekt. Wichtige Werte:

| Schlüssel | Bedeutung |
|---|---|
| `upload.cooldown-seconds` | Mindestabstand zwischen zwei Uploads eines Spielers (0 = aus) |
| `upload.max-songs-per-player` | Standard-Songlimit pro Spieler (0 = unbegrenzt) |
| `upload.max-storage-mb` | Serverweites Speicher-Limit in MB (0 = unbegrenzt) |
| `upload.max-concurrent` | Wie viele Uploads gleichzeitig verarbeitet werden |
| `network.max-dust` / `max-devices` | Obergrenze für Redstone-Netze (Schutz vor Lag) |

Limits, die ein Operator im Spiel ändert, stehen in `limits.json` und haben **Vorrang** vor der Config.

## Client (Fabric)

Voraussetzungen: Minecraft **26.2**, **Fabric Loader 0.20.1 oder neuer**, **Fabric API** für 26.2, Java 25.

### Mit dem Fabric-Installer oder dem normalen Launcher

1. Fabric Loader für 26.2 installieren (fabricmc.net).
2. `fabric-api-<version>+26.2.jar` und `PeakSound-<version>.jar` in `.minecraft/mods/` legen.
3. Mit dem Fabric-Profil starten.

### Mit Lunar Client

Lunar Client unterstützt eigene Fabric-Mods (nur ab Version 1.16.5, also auch für 26.2):

1. Im Launcher die Version 26.2 wählen und den Reiter **Mods** öffnen (Schaltfläche unten rechts).
2. Die Jar-Dateien von **Fabric API** und **PeakSound** per Drag-and-drop in den Reiter ziehen.
3. Sicherstellen, dass beide aktiviert sind, und starten.

Ich habe die Mod nur mit dem normalen Fabric-Client getestet, nicht mit Lunar. Falls Lunar sie nicht lädt, ist der
normale Fabric-Client der verlässliche Weg.

### Erster Start

- Im Creative-Tab **PeakSound** liegen Jukebox, Lautsprecher, leere Platte und Peak-Tuner.
- Die Mod meldet sich beim Beitritt automatisch beim Plugin. Gibt es einen Versionskonflikt, steht eine klare Meldung
  im Chat.
- Die eigene Lautstärke stellst du unter **Optionen → Musik & Geräusche → PeakSound-Lautstärke** ein (0 = Musik für
  dich aus).
- Der Client-Cache liegt in `.minecraft/peaksound/cache/` und darf jederzeit gelöscht werden.

## Fehlersuche

| Problem | Ursache |
|---|---|
| „Mod und Plugin passen nicht zusammen“ | Mod und Plugin haben verschiedene Protokollversionen: beide aktualisieren |
| Kein Ton | Sound in Minecraft an? PeakSound-Lautstärke auf 0? Platte eingelegt? Genug nah an der Jukebox (Reichweite)? |
| Equalizer reagiert nicht | Log prüfen („Comparator-Equalizer ausgeschaltet“): Paper-Version hat andere Interna |
| Upload abgelehnt | Meldung im Chat nennt den Grund (zu lang, Limit, Cooldown, Speicher voll) |
| Spieler ohne Mod sieht Notenblöcke | Gewollt: Er bekommt einen Hinweis im Chat |
