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
