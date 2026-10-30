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

## Phase 11 – Song-Bibliothek, Fernbedienung, Ausgänge
Testbefehle: `/peaksound give <Spieler> <item> [n]`, `/peaksound menu <Spieler> jukebox|remote|dj X Y Z`, `/peaksound remote X Y Z <Aktion>`, `/peaksound port X Y Z <n|e|s|w> <Funktion>`, `/peaksound jukebox X Y Z`.
- [ ] Rezeptbuch (Inventar, Survival): Reiter „PeakSound“ mit allen Rezepten (auch Song-Bibliothek und Fernbedienung).
- [ ] Song-Bibliothek: Leere Platte in die Hand, Rechtsklick auf den Block. Suche nach Titel/Interpret/Hochlader, Anhören (▶), „Auf Platte“ schreibt den Song; ohne leere Platte kommt ein Hinweis.
- [ ] Musik-Fernbedienung per Staub an eine Jukebox, Hebel daneben: Aktion im Menü wählen (Pause/Weiter, Skip, Zurück, Shuffle, Wiederholen, Play/Stopp, Song nach Stärke, Lautstärke nach Stärke) und prüfen. „Signal umdrehen“ testen.
- [ ] Jukebox-Menü → Reiter Ausgänge: Würfel drehen, Seite anklicken, Funktion wählen. Mit Comparator + Lampe prüfen: Bass/Mitten/Höhen/gesamt, „Läuft“, „Song-Ende“ (Impuls), „Takt“ (Impuls).
- [ ] Lautsprecher-Menü: „Redstone-Steuerung: An“ → Box spielt nur mit Signal. Mikrofon und DJ-Pult genauso (DJ: kein Signal = Deck A, Signal = Deck B mit Überblendzeit).

## Phase 12 – Jukebox-Inventar
- [ ] Jukebox mit Peak-Tuner öffnen → Reiter Playlist: 15 Slots. Beschriebene Platte im eigenen Inventar anklicken legt sie ein, Rechtsklick auf einen Slot nimmt sie heraus; Abspielen, Stopp, Skip, ▲/▼, Wiederholen und Shuffle funktionieren. Nicht beschriebene Items lassen sich nicht einlegen.
- [ ] Reiter Ausgänge: auch Oben und Unten wählbar (Aus, Items rein, Items raus). Eine Kiste oder ein Hopper an einer Seite mit „Items raus“ bekommt die Platten, mit „Items rein“ werden Platten daraus ins Inventar geholt (eine Platte alle 8 Ticks).
- [ ] Jukebox abbauen: Alle Platten im Inventar fallen heraus. Neustart: Inhalt bleibt (jukebox-inventories.json).
- [ ] Hinweis: Alte Hopper-/Kisten-Playlisten werden nicht mehr gelesen; Platten manuell ins Inventar legen oder eine Seite auf „Items rein“ stellen.

## Phase 13 – Radio

Vorbereitung: eine Jukebox mit Platte(n), ein Stück Redstone-Staub daneben und daran der **Radio-Sender** (Creative-Tab
*PeakSound*, Rezept im Rezeptbuch). Weiter weg ein oder zwei **Radios** (Empfänger) und das **Taschenradio**.

- [ ] Sender-Menü (Peak-Tuner, Rechtsklick): Frequenz mit −1 / −0.1 / +0.1 / +1 einstellbar (87.5–108.0 MHz), Strom an/aus; Anzeige „Auf Sendung: Titel“, solange die Jukebox spielt, sonst „Sendet nichts“.
- [ ] Empfänger auf dieselbe Frequenz stellen: Der Song kommt aus dem Radio-Block, gleichzeitig mit der Jukebox (gleiche Position im Lied), 3D und mit Reichweite (Regler 1–32).
- [ ] Empfänger auf eine andere Frequenz: Rauschen, in der Nähe lauter, mit Abstand leiser; Menü zeigt „Kein Sender – Rauschen“.
- [ ] Frequenz im offenen Menü auf die des Senders drehen: Zeile wechselt zu „Empfang: Titel“, das Rauschen verschwindet.
- [ ] Sender ausschalten oder Jukebox stoppen: Empfänger rauscht (bzw. schweigt, wenn aus).
- [ ] Zwei Empfänger auf derselben Frequenz spielen synchron.
- [ ] Taschenradio: Rechtsklick schaltet an/aus (Chat-Zeile über der Hotbar), Shift+Rechtsklick öffnet das Menü (Frequenz, Lautstärke). Der Sender ist überall auf dem Server hörbar, direkt am Ohr; ohne Sender rauscht es.
- [ ] Block abbauen: Item fällt, Einstellungen sind weg; Server-Neustart behält Frequenzen (`plugins/PeakSound/radio.json`).
- [ ] `/peaksound debug`: Abschnitt [Radio] listet Sender und Empfänger mit Empfangszustand.

Bekannte Grenzen: Der Sender muss in einem geladenen Chunk stehen. Bei gleicher Frequenz gewinnt der erste Sender. Der Radio-Kopf
sieht ohne Mod wie ein normaler Spielerkopf aus.
## Phase 14 – Anzeige, Songtext, Wünsche

- [ ] Now-Playing: Betritt man die Reichweite einer spielenden Jukebox oder startet ein neuer Song, erscheint oben in der Mitte ca. 6 s lang Cover, Titel und Interpret (ohne Cover das Standardbild). `/peaksoundhud` schaltet es aus und wieder an.
- [ ] Songtext: Eine MP3 mit Songtext in den Tags (USLT) hochladen. Beim Abspielen steht unten über der Hotbar die aktuelle Zeile, darunter die nächste. Mit Zeitstempeln `[mm:ss.xx]` läuft sie zeitgenau, sonst gleichmäßig verteilt und mit „~“ markiert. `/peaksoundlyrics` schaltet sie aus und an.
- [ ] Songs ohne Text zeigen keine Zeile; schon vorhandene (früher hochgeladene) Songs haben keinen Text und müssten neu hochgeladen werden.
- [ ] Wünsche: Song-Bibliothek per Redstone-Staub an eine Jukebox mit Playlist anschließen. In der Bibliothek „Modus: Song wünschen“ wählen, bei einem Song auf „Wünschen“ klicken: Chat zeigt den Platz, Zuhörer in der Nähe sehen die Meldung mit klickbarer „[Skip-Abstimmung]“. Der Wunsch läuft nach dem aktuellen Song, danach geht die Playlist an der alten Stelle weiter.
- [ ] Grenzen: höchstens 10 Wünsche gesamt, 3 je Spieler, derselbe Song nicht doppelt (`config.yml`, Abschnitt `queue`). Steht die Jukebox still, startet der Wunsch sofort. Mit einer von Hand eingelegten Platte werden Wünsche abgelehnt (die Platte darf nie verloren gehen).
- [ ] `/peaksoundvote`: Mehrheit der Zuhörer in Reichweite (Hälfte + 1) überspringt den Song; die Chat-Zeile zeigt den Stand.

- [ ] **Display-Block**: Per Redstone-Staub an das Netz einer spielenden Jukebox hängen. Der Bildschirm zeigt Cover, Titel und Interpret des Songs (Songs ohne Cover: Farbverlauf). Er zeigt zum Spieler, der ihn setzt; Peak-Tuner-Rechtsklick dreht ihn um 90°.
- [ ] Mehrere Displays in einer Ebene (volles Rechteck, bis 8×8) ergeben eine zusammenhängende Cover-Wand; ein Block der Wand am Staub genügt. Bei L-Formen oder Lücken zeigt jeder Block sein eigenes Bild.
- [ ] Wird auch ohne Mod gesehen (unsichtbare leuchtende Bilderrahmen mit Karten). Steht ein Block vor dem Bildschirm, fehlt dort der Rahmen. Nach Songwechsel erscheint das neue Bild nach höchstens etwa einer Sekunde.

Grenzen: Die Warteschlange liegt nur im Speicher (nach einem Neustart leer). Karten haben nur wenige Farben (Vanilla-Palette); jeder Display-Block belegt eine Karten-ID. Auf einem Server ohne Schriften fehlt der Titeltext.
## Phase 15 – Laufzeit, Raumhall, Überblendung

- [ ] Lautsprecher-Menü (Peak-Tuner): Regler **Verzögerung** (0–500 ms) und Knopf **Raumhall** (Aus, Zimmer, Halle, Höhle, Badezimmer).
- [ ] Verzögerung: Zwei Lautsprecher mit demselben Kanal, einen auf 200 ms stellen: man hört ein Echo/den Versatz; zurück auf 0 ms: wieder deckungsgleich. Spät dazukommende Spieler hören dieselbe Verzögerung.
- [ ] Raumhall: „Halle“ oder „Höhle“ am Lautsprecher: hörbare Nachhallfahne; „Aus“ klingt wieder trocken. Ohne EFX-Unterstützung des Audiogeräts bleibt der Hall still (kein Fehler).
- [ ] Jukebox-Menü, Reiter Einstellungen: Regler **Überblendung** (0–12 s). Bei einer Playlist blendet der Song in den letzten Sekunden aus und der nächste dann ein (Aus-/Einblenden, **kein** echtes Übereinanderlegen). Mit 0 s unverändert.
- [ ] Nach dem Ausblenden bleibt nichts leise: Nächste von Hand eingelegte Platte und Neustart der Wiedergabe sind in voller Lautstärke.

## Phase 16 – Rechte, Statistik, Sicherungen

- [ ] Rechte (mit LuckPerms o. Ä. testen, Standard = alle erlaubt): `peaksound.upload`, `peaksound.library`, `peaksound.library.manage.own`, `peaksound.queue`, `peaksound.vote`, `peaksound.place`, `peaksound.radio` einzeln auf *false* stellen: die jeweilige Aktion wird mit Meldung verweigert. `peaksound.library.manage.any` erlaubt fremde Songs zu verwalten.
- [ ] Songlimit per Recht: `peaksound.limit.20` gibt 20 Uploads, `peaksound.limit.unlimited` unbegrenzt; ein vom Operator gesetztes Spielerlimit hat Vorrang.
- [ ] `/peaksound stats` und Admin-Menü → Reiter **Statistik**: Songs, Speicher, Abspielungen, Top-Songs, größte Hochlader, Blöcke, Spieler online.
- [ ] `/peaksound backup` erzeugt `plugins/PeakSound/backups/manuell-*.zip`; täglich entsteht automatisch `auto-*.zip` (die ältesten über `backup.keep` werden gelöscht).
- [ ] `/peaksound restore <Name>` (Tab-Vervollständigung) meldet „bereit“; nach dem Neustart steht im Log „Sicherung eingespielt“ und `vor-restore-*.zip` enthält den vorherigen Stand.

## Server-Resource-Pack

- [ ] Alle PeakSound-Bloecke und -Items (Jukebox, Lautsprecher mit Nummer, DJ-Pult, Scheinwerfer, Mikrofon, Bibliothek, Fernbedienung, Display, Radio-Sender/-Empfaenger, Taschenradio, Tuner, Platten) sehen mit der Mod aus wie vorher - platziert, im Inventar und in der Hand.
- [ ] Dasselbe mit einem Server-Resource-Pack, das eine eigene ssets/minecraft/blockstates/note_block.json und items/note_block.json mitbringt: unsere Bloecke und Items behalten ihre Optik, normale Notenbloecke zeigen das Modell des Packs.
- [ ] Echte Spielerkoepfe und Vanilla-Items (Notenblock, Trial Key) sind unveraendert.

## Phase 6 - PeakSound als Modul von PeakCore (Kompatibilitaet mit der Alpha-Version)

Automatisch (`./gradlew build`):
- [x] `PluginYmlTest` (seit Phase 6b im Projekt `Bundle`): Befehle `peaksound` und `peaksoundvote`, alle Rechte der Alpha-Version, `softdepend: [voicechat]`, gueltiges YAML
- [x] `TransportsTest`: Weg je Spieler (alter Kanal oder PeakCore), Sendeweg nach Groesse, ohne Hello wird nichts gesendet, Core-Weg hat wegen des Umschlags etwas weniger Platz

Auf dem Testserver mit PeakCore + PeakSound (Alpha-Serverdaten: 73 Bloecke, 7 Songs, Radios, Displays, Bibliothek) und der **alten Mod** (aktuelle PeakSound-Mod mit Protokoll 2, Dev-Client):
- [x] PeakSound laedt mit den vorhandenen Daten, Log: "Modul angemeldet: PeakSound 0.1.0 (peaksound, Plugin PeakSound)"; `/peakcore modules` listet es
- [x] Alte Mod meldet sich: "Mod meldet sich (Protokoll 2, Server 2, Operator: true, Weg: alter Kanal)"
- [x] `/peaksound stats`: "13 Jukeboxen (11 spielen), 35 Lautsprecher, 1 Sender, 2 Radios, 1 Bibliotheken; Spieler online: 1 (1 mit Mod)" - alle Anlagen sind geladen
- [x] Wiedergabe: nach `/peaksound play` laedt der Client die Songs ueber den alten Kanal herunter (Cache-Dateien mit exakt denselben Groessen wie auf dem Server)
- [x] Diagnose der alten Mod (Auto-Debug): Handshake beantwortet, Operator ja, Server-Bericht erhalten; Empfangen: HelloAck, PlaybackStart 7, LightStart 16, AnalysisData 2, CoverData 1, LyricsData 2, SongInfo 7; 0 ungueltige Pakete
- [x] Spieler mit der alten Mod bekommt keinen Hinweis "keine Mod gefunden" von PeakCore (`/peakcore players` zeigt ihn als "keine Mod gemeldet", er ist aber als kompatibel markiert)
- [x] Beim Abschalten meldet PeakCore das Modul ab
- [x] **Neue Mod ueber den PeakCore-Kanal** (Weg "PeakCore"): Handshake ACTIVE, Wiedergabe laeuft (Phase 7 und 6b, Auto-Debug)
- [ ] Update einer echten Alpha-Installation (Daten vom Freunde-Server): nicht moeglich, es gibt kein Backup; nur mit den eigenen Testdaten geprueft
- [ ] Mit Simple Voice Chat, Upload per Mod und Mikrofon: nicht erneut getestet (der Code dort ist unveraendert)
## Phase 6b - Eingebautes Modul im All-in-One-Jar

Siehe `PeakCore/docs/TESTS.md` (Abschnitt Phase 6b) fuer die Pruefungen des Jars, des Modul-Schalters und des doppelten Alt-Jars. Fuer PeakSound selbst:
- [x] Daten, Befehle, Rechte, Item-Markierungen und der Datenordner `plugins/PeakSound` sind unveraendert (Plugin-Name steckt nirgends mehr im Namensraum: Schluessel nutzen `PeakIds.NAMESPACE`)
- [x] Mod startet unabhaengig von der Reihenfolge der Mods (`PeakCoreSession.whenReady`); vorher stuerzte der Dev-Client ab, wenn PeakSound vor der Core-Mod initialisiert wurde
- [ ] Voice Chat, Upload per Mod und Mikrofon im All-in-One-Jar: nicht erneut getestet (Code mechanisch umgestellt)
