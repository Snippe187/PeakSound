# Selbst testen (eigener Server + Showroom-Karte)

So bekommst du PeakSound mit der fertigen Testkarte auf einen eigenen Server. Du brauchst Java 25, Minecraft 26.2
(Java Edition) und einen Rechner, auf dem der Server laufen darf (dein eigener PC reicht).

## 1. Dateien

Aus dem Ordner `dist/` des Projekts (oder selbst gebaut, siehe [BUILD.md](BUILD.md)):

| Datei | Wohin |
|---|---|
| `Server-Plugin-Paper/PeakSound-0.1.0.jar` | Server, Ordner `plugins/` |
| `Client-Mod-Fabric/PeakSound-0.1.0.jar` | Client, Ordner `.minecraft/mods/` |
| `PeakSound-Showroom-Map.zip` | Testkarte (Welt + Plugin-Daten), siehe Schritt 3 |

Zusätzlich:
- **Paper 26.2** (Build 135 oder neuer) von papermc.io/downloads/paper
- **Fabric Loader** (0.20.1 oder neuer) und **Fabric API für 26.2** (fabricmc.net, modrinth.com/mod/fabric-api)
- optional für das Mikrofon: **Simple Voice Chat** als Paper-Plugin (2.6.23 oder neuer) für den Server und als Fabric-Mod
  für jeden Spieler (modrinth.com/plugin/simple-voice-chat und modrinth.com/mod/simple-voice-chat)

## 2. Server aufsetzen

1. Neuen Ordner anlegen, `paper.jar` hineinlegen, dort `java -jar paper.jar nogui` ausführen. Beim ersten Mal bricht er
   ab: in `eula.txt` `eula=true` setzen und noch einmal starten. Danach mit `stop` beenden.
2. `Server-Plugin-Paper/PeakSound-0.1.0.jar` (und optional Simple Voice Chat) nach `plugins/` kopieren.
3. Für den Test in `server.properties` sinnvoll: `gamemode=creative`, `spawn-protection=0`.
   Spielst du nur lokal und ohne Mojang-Konto-Prüfung, brauchst du `online-mode=false`; sonst lass es auf `true`.

## 3. Showroom-Karte einspielen

Das Zip enthält zwei Ordner: `world/` und `plugins/PeakSound/`. Bei **gestopptem** Server:

1. Deinen Welt-Ordner (`world/`) umbenennen oder löschen und den `world/`-Ordner aus dem Zip an dessen Stelle legen.
2. Den Ordner `plugins/PeakSound/` aus dem Zip nach `plugins/` kopieren (er enthält die Daten aller PeakSound-Blöcke und
   drei Test-Songs. Ohne ihn wären die Blöcke der Karte nur normale Notenblöcke).
3. Server starten. Die Jukeboxen der Karte spielen dann selbst los.
4. Dich selbst zum Operator machen (Server-Konsole): `op DeinName`

## 4. Client

1. Fabric Loader für 26.2 installieren, `fabric-api-….jar` und `Client-Mod-Fabric/PeakSound-0.1.0.jar` in `.minecraft/mods/` legen
   (bei Lunar: im Reiter *Mods* per Drag-and-drop, siehe [INSTALL.md](INSTALL.md)).
2. Mit dem Fabric-Profil starten und dem Server beitreten (`localhost`, bei einem Server im Netzwerk die IP).
3. Teleport zum Showroom (als Operator): `/tp 240 -60 236`. Alternativ steht der Weltspawn dort.

## 5. Rundgang

- **Radio-Ecke** (Nordost, ab `/tp 255 -60 224`): Jukebox mit Radio-Sender (100,0 MHz), ein Radio auf 100,0 MHz (spielt den Song), eins auf 95,0 MHz (rauscht). Tafeln erklären Frequenz und Taschenradio.
- **Plaza** (Mitte): Tafelwand mit Rezepten, Menüs und den Redstone-Ausgängen. Südseite = Grundlagen, Nordseite = Redstone.
- **Nord:** Stereo-Anlage und Mikrofon. **Ost:** Surround 7.1.4. **Süd:** DJ-Pult mit Scheinwerfern.
  **West:** Lichtshow und Comparator-Equalizer.
- Mit dem **Peak-Tuner** (Creative-Tab *PeakSound*) Rechtsklick auf Jukeboxen, Lautsprecher, DJ-Pult, Scheinwerfer und
  Mikrofon: die Menüs öffnen sich. Shift-Rechtsklick kopiert und fügt Einstellungen ein.
- Lautstärke: *Optionen → Musik & Geräusche → PeakSound-Lautstärke*, dort auch der Schalter für Strobo-Effekte.
- Eigene Musik: leere Platte (Creative-Tab oder Obsidian + Diamantschwert) in die Hand, mit **Shift** auf eine Jukebox
  klicken, Datei wählen. Genaue Prüfpunkte pro Funktion stehen in [TESTS.md](TESTS.md).

## 6. Mikrofon mit Simple Voice Chat testen

1. Simple Voice Chat auf Server **und** bei allen Spielern installieren (zwei Spieler sind für den Test am besten;
   UDP-Port 24454 muss erreichbar sein). Im Server-Log steht beim Start: „Simple Voice Chat gefunden: Mikrofon-Bloecke sind aktiv.“
2. Das Mikrofon im Nordraum mit dem Peak-Tuner öffnen: an, Reichweite, Lautstärke, Ducking.
3. Spieler A spricht nahe am Mikrofon. Spieler B steht bei den Lautsprechern und muss die Stimme von dort hören.
   A hört sich nicht selbst. Mit Ducking wird die Musik währenddessen leiser.

## 7. Wenn etwas nicht geht

| Problem | Ursache |
|---|---|
| Blöcke der Karte sind normale Notenblöcke | `plugins/PeakSound/` aus dem Zip fehlt oder die Mod ist nicht installiert |
| Kein Ton | Mod fehlt, *Plattenspieler*-Lautstärke oder PeakSound-Lautstärke auf 0, Song noch nicht geladen |
| „Mod und Plugin passen nicht zusammen“ im Chat | Mod und Plugin sind nicht aus demselben Build |
| Mikrofon-Menü sagt „Simple Voice Chat nicht installiert“ | Voice-Chat-Plugin fehlt auf dem Server |
| Equalizer-Lampen bleiben dunkel | Paper-Interna haben sich geändert: der Log meldet „Comparator-Equalizer ausgeschaltet“ |

Die Karte neu aufbauen (z. B. nach eigenen Änderungen): `powershell -File tools\build_showroom.ps1` auf dem Testserver
des Projekts.

## Admin-Menü

Operator werden (`op DeinName` in der Server-Konsole), dann im Spiel entweder im Pausenmenü (Esc) unten rechts auf **PeakSound: Songs** klicken oder den Befehl `/peaksoundadmin` im Chat eingeben (der zweite Weg geht auch, wenn ein Launcher das Pausenmenü verändert).

## Songlaenge

Standard sind 4 Minuten. Fuer laengere Songs in `plugins/PeakSound/config.yml` `upload.max-song-seconds` erhoehen (z. B. `400` fuer gut 6 Minuten, hoechstens 3600) und den Server neu starten. Die Dateigroesse ist auf 64 MB begrenzt (eine WAV-Datei von 6 Minuten ist zu gross, MP3, OGG oder FLAC passen).
