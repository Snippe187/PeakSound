# Testlisten

Kurze Prüflisten je Phase. Voraussetzung: Server mit Plugin, Client mit Mod, Spieler im Kreativmodus.
Testdateien erzeugst du mit `node tools/make_test_audio.js` (`run/test-audio/`).

## Phase 1 – Items, Blöcke, Rezepte, Peak-Tuner
- [ ] Creative-Tab „PeakSound“ enthält leere Platte, Peak-Tuner, Jukebox, Lautsprecher (deutsch und englisch).
- [ ] Obsidian + Diamantschwert ergibt 9 leere Platten, das Schwert kommt mit halber Haltbarkeit zurück, auch bei Shift-Klick (höchstens 11 Crafts, dann bricht es).
- [ ] Rezepte für Jukebox, Lautsprecher und Peak-Tuner erscheinen im Rezeptbuch. Eine fertige PeakSound-Jukebox lässt sich nicht als Zutat benutzen.
- [ ] Blöcke platzieren und abbauen (dropt das PeakSound-Item). Explosionen und Kolben verschieben sie nicht.
- [ ] Spieler ohne Mod: Hinweis im Chat, kein Kick.

## Phase 2 – Upload, Speicherung, 3D-Audio
- [ ] Shift-Rechtsklick mit leerer Platte auf die Jukebox öffnet das Upload-Fenster (Dateifenster und Drag-and-drop).
- [ ] Upload von `test_stereo.wav`: Platte wird beschrieben, Ton kommt aus der Jukebox mit Richtung und Dämpfung.
- [ ] Späterer Einstieg: Wer in Reichweite läuft, steigt an der richtigen Stelle ein.
- [ ] `test_6ch.wav`: Warnung vor dem Upload, danach Meldung „muss vom Server-Owner freigegeben werden“.
- [ ] `test_toolong.wav`: „zu lang (maximal 4 Minuten)“. Zweiter Upload sofort danach: Cooldown-Meldung.
- [ ] Platte kopieren (Shift-Rechtsklick mit beschriebener Platte), entnehmen (leere Hand).

## Phase 3 – Jukebox-Menü
- [ ] Peak-Tuner-Rechtsklick öffnet das Menü. Name, Reichweite, Lautstärke wirken live und bleiben nach Neustart.
- [ ] Ketten-Schalter: 16 Blöcke / 50 % und Reichweite 24 ergibt 75 %; an den Grenzen begrenzt.
- [ ] Noten-Partikel im Takt (Menge, Dichte, Höhe, bunt/einfarbig), „Jetzt läuft“ an/aus.
- [ ] Cover im Tooltip der Platte (MP3 mit Cover), sonst Standardbild.

## Phase 4 – Playlist
- [ ] Hopper an der Jukebox mit Platten: Reiter „Playlist“ zeigt sie. Auswahl, Skip, Stopp, Reihenfolge (▲▼).
- [ ] Loop (Aus/Playlist/Song) und Zufall. Kiste am Hopper erweitert die Liste.

## Phase 5 – Verbund und Equalizer
- [ ] Zwei Jukeboxen mit Staub verbunden spielen synchron. Staub trennen: die zweite wird still.
- [ ] Comparator mit Rückseite an der Jukebox: Nord Bass, Ost Mitten, Süd Höhen, West gesamt (0–15).

## Phase 6 – Lautsprecher und Surround
- [ ] Lautsprecher mit Staub an die Jukebox: Nummer erscheint auf den Seiten, Jukebox schweigt.
- [ ] Peak-Tuner auf Lautsprecher: Name, Position (Kanal), Lautstärke, Nummer, verbundene Jukebox.
- [ ] Reiter „Lautsprecher“ im Jukebox-Menü zeigt alle verbundenen Lautsprecher mit Kanal.
- [ ] Surround-Rundgang (7.1-Testsong): jeder Kanal einzeln am richtigen Ort, danach kreisender Ping.

## Phase 7 – OP-Menü
- [ ] Button „PeakSound: Songs“ im Escape-Menü nur für Operatoren (folgt /op und /deop).
- [ ] Songs vorhören, löschen (zwei Klicks). Gesperrte Dateien freigeben oder ablehnen. Limits ändern und neu starten: sie bleiben.

## Phase 8 – Optimierungen und Feinschliff
- [ ] Neuer, ungecachter Song: Die Wiedergabe beginnt nach wenigen Sekunden, nicht erst nach dem kompletten Download.
- [ ] Optionen → Musik & Geräusche: „PeakSound-Lautstärke“ wirkt (0 = Musik aus).
- [ ] Peak-Tuner in der Hand: leuchtende Linien von der Jukebox zu den Lautsprechern (türkis) und weiteren Jukeboxen (orange).
- [ ] Shift-Rechtsklick mit Tuner auf Jukebox/Lautsprecher kopiert, Shift-Rechtsklick auf einen anderen Block gleicher Art fügt ein. Shift-Rechtsklick in die Luft leert.

## Phase 9 – DJ-Pult und Scheinwerfer
Testanlage im Spiel mit Befehl: `/peaksound place dj_desk|spotlight|jukebox X Y Z`, `/peaksound play X Y Z <Song>`, `/peaksound dj X Y Z` (zeigt Decks und Faktoren).
- [ ] Rezeptbuch zeigt DJ-Pult (2 PeakSound-Jukeboxen links/rechts, Redstone oben, Eisen unten, Diamant Mitte) und Scheinwerfer (2 Stück). Normale Notenblöcke statt PeakSound-Jukeboxen ergeben nichts.
- [ ] DJ-Pult mit Peak-Tuner anklicken: Menü mit zwei Decks. Je ein Redstone-Staub-Strang vom Pult zu einer Jukebox-Gruppe (Deck A/B); „Decks tauschen“ vertauscht sie.
- [ ] Song pro Deck aus der Playlist der Jukebox wählen, Skip und Stop. Crossfader bewegen: Deck A wird leiser, Deck B lauter über die eingestellte Überblendzeit; mit zwei Spielern gleichzeitig hört man dasselbe.
- [ ] Decklautstärken wirken sofort. Pult abbauen: Lautstärken der Jukeboxen sind wieder normal.
- [ ] Scheinwerfer per Staub an eine Jukebox anschließen, Peak-Tuner → Menü. Strahl aus farbigen Partikeln in der eingestellten Richtung; echtes Licht im Strahl.
- [ ] Farben: mehrere Farben wechseln sich ab; Effekt „Farbwechsel“ blendet weich über. Modi Dauerhaft / Blinkend / Im Takt (Bass, Mitten, Höhen).
- [ ] Effekt Strobo: einmalige Warnung im Chat; Optionen → Musik & Geräusche → „PeakSound-Strobo-Effekte: Aus“ macht den Strobo gleichmäßig hell.
- [ ] Spielt die Jukebox nichts, ist der Scheinwerfer im Modus „Im Takt“ aus. Server neu starten: Einstellungen bleiben, keine Lichtblöcke bleiben zurück.

## Phase 10 – Mikrofon (Simple Voice Chat)
Voraussetzung: Simple Voice Chat (Paper-Plugin, Version 2.6.23 oder neuer) im Ordner `plugins` des Servers und die Mod bei jedem Spieler. Ohne das Plugin steht im Server-Log „Simple Voice Chat nicht installiert“.
- [ ] Rezeptbuch zeigt das Mikrofon (Eisenbarren oben/unten, Redstone-Staub links/rechts, Stock in der Mitte).
- [ ] Mikrofon per Staub an Lautsprecher oder Jukebox anschließen, Peak-Tuner → Menü (an/aus, Reichweite, Lautstärke, Ducking).
- [ ] Spieler A spricht nahe am Mikrofon: Spieler B hört die Stimme räumlich aus den Lautsprechern (nicht aus A). A hört sich nicht selbst verzögert.
- [ ] Lautstärke 200 % / 50 % wirkt. Mikrofon aus: nichts wird übertragen. Weiter als die Sprechreichweite weg: nichts.
- [ ] Ducking an: Die Musik wird beim Sprechen leiser (Pegel einstellbar) und blendet danach wieder auf.
- [ ] Ohne Voice-Chat-Plugin: Mikrofon-Menü zeigt den Hinweis, sonst keine Fehler.
