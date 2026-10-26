# PeakSound

Eigene Musik auf Schallplatten brennen und im Multiplayer in echtem 3D bis hin zu 7.1.4-Surround abspielen.
Für Minecraft **26.2**: eine **Fabric-Client-Mod**, ein **Paper-Server-Plugin** und ein gemeinsames Protokoll.

![Logo](branding/peaksound_logo.svg)

## Was kann es?

- **Schallplatten:** Eine leere Platte (1 Obsidianblock + Diamantschwert ergibt 9, das Schwert kommt mit halber
  Haltbarkeit zurück) wird per Upload beschrieben. MP3, FLAC, WAV und OGG, bis 4 Minuten. Titel, Interpret und
  Cover kommen aus den Tags. Der Tooltip der Platte zeigt Cover, Titel, Interpret und den Uploader.
- **PeakSound-Jukebox** mit Menü: Name, Reichweite (1–30), Lautstärke, Ketten-Schalter, Noten-Partikel im Takt,
  „Jetzt läuft“-Anzeige, Playlist (Hopper als CD-Wechsler, Kisten erweitern ihn) und Lautsprecher-Übersicht.
- **Redstone-Verbund:** Jukeboxen, die über Redstone-Staub verbunden sind, spielen synchron denselben Song.
- **Comparator-Equalizer:** Comparator an der Jukebox gibt live 0–15 aus (Norden Bass, Osten Mitten, Süden Höhen, Westen gesamt).
- **Lautsprecher und Surround:** bis zu 25 Lautsprecher je System, jeder eine eigene 3D-Schallquelle mit Kanal
  (7.1.4-Positionen), Nummer auf der Textur, Upmix von Stereo, Mehrkanal direkt.
- **Streaming und Cache:** Die Wiedergabe startet, während der Song noch lädt. Geladene Songs liegen im Client-Cache
  (Hash als Kennung).
- **OP-Menü:** Song-Verwaltung im Escape-Menü: alle Songs, vorhören, löschen, gesperrte Mehrkanal-Dateien
  freigeben oder ablehnen, Upload-Limits und Speicher-Limit einstellen.
- **Peak-Tuner:** öffnet die Menüs, zeigt Verbindungslinien, kopiert und fügt Einstellungen ein (Shift-Rechtsklick).
- Deutsch und Englisch.

Spieler ohne Mod werden nie gekickt: Sie sehen normale Blöcke und bekommen einen Hinweis im Chat.

## Aufbau

| Ordner | Inhalt |
|---|---|
| `protocol/` | Netzwerkprotokoll, Limits, Playlist-, Netz-, Sync- und Kanal-Logik (ohne Minecraft, mit Unit-Tests) |
| `audio-core/` | Decoder (MP3, FLAC, WAV, OGG), Tags, Analyse (Takt, Bänder), FLAC-Encoder, ffmpeg-Anbindung |
| `paper-plugin/` | Server-Plugin (die Server ist immer die Autorität) |
| `fabric-mod/` | Client-Mod (Menüs, Audio, Rendering) |
| `docs/` | [Installation](docs/INSTALL.md), [Build](docs/BUILD.md), [Testlisten](docs/TESTS.md) |
| `tools/`, `branding/` | Testsound-Generatoren, Logo |

Die Architektur steht in [ARCHITECTURE.md](ARCHITECTURE.md), der Fortschritt in [PLAN.md](PLAN.md).

## Wichtige Grenzen

- **Quelldateien haben höchstens 8 Kanäle (7.1).** FLAC und Vorbis erlauben nicht mehr. Die vier Decken-Lautsprecher
  von 7.1.4 bekommen abgeleitete Signale.
- **Kein Dolby Atmos** (proprietär).
- Der Comparator-Equalizer nutzt Paper-Interna per Reflection. Fehlen sie, schaltet sich nur er ab.
- Die Blöcke sind für Spieler ohne Mod Notenblöcke (Plugin-Lösung ohne Mod auf dem Server).

## Lizenz

MIT, siehe [LICENSE](LICENSE). Die verwendeten Bibliotheken stehen in [THIRD-PARTY-LICENSES.md](THIRD-PARTY-LICENSES.md).
