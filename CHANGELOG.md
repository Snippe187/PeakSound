# Changelog

## 0.1.0
- PeakSound ist jetzt ein **eingebautes Modul von PeakCore** (All-in-One-Jar `PeakCore-<Version>.jar`, kein eigenes Plugin mehr; Schalter `modules.peaksound.enabled`), Anmeldung als Modul `peaksound`. Beim Update die alte `PeakSound`-Jar loeschen. Der alte Kanal `peaksound:main` (Protokoll 2) bleibt, damit Spieler mit der Alpha-Mod nach dem Update weiterspielen; die neue Mod spricht ueber den Kanal von PeakCore. Datenordner `plugins/PeakSound`, Daten, Rechte und Befehle sind unveraendert. Die Mod meldet sich unabhaengig von der Startreihenfolge der Mods bei der Core-Mod an.
- Der Hinweis fuer Spieler ohne Mod kommt von PeakCore; Spieler mit der alten Mod werden dort als kompatibel erkannt.
- Server-Resource-Packs: Die Mod ueberschreibt keine Vanilla-Dateien mehr (Notenblock-Blockstate, Item-Definitionen), sondern setzt ihre Modelle clientseitig per Code ein (PeakModels). Ein Server-Pack mit eigener note_block.json verdraengt unsere Optik dadurch nicht.
- Display-Block: zeigt Cover, Titel und Interpret des laufenden Songs; nebeneinander liegende Displays bilden eine Cover-Wand (Bilderrahmen mit Karten, serverseitig, auch ohne Mod).
- Aufraeumen: Verschwundene PeakSound-Bloecke (WorldEdit, /setblock) werden alle 30 s erkannt, ihre Wiedergabe und Einstellungen vergessen.
- Showroom-Karte mit Radio-Ecke, Rezept-PDF mit Radio-Rezepten.
- Audio: Verzoegerung (0-500 ms) und Raumhall (Zimmer, Halle, Hoehle, Bad; OpenAL-EFX) je Lautsprecher, Ueberblendung zwischen Playlist-Songs (aus-/einblenden, 0-12 s).
- Server: Rechte-Nodes (peaksound.upload, .library, .queue, .vote, .place, .radio, .library.manage.own/any, .limit.<n>), Statistik (/peaksound stats und Admin-Reiter), Sicherungen (/peaksound backup|backups|restore, taeglich automatisch mit Rotation).
- Anzeige: Now-Playing-Meldung (Cover, Titel, Interpret) und Songtext-Zeile aus MP3-Tags (zeitgenau mit LRC-Zeitstempeln, sonst gleichmaessig verteilt); /peaksoundhud, /peaksoundlyrics.
- Wunsch-Warteschlange an der Song-Bibliothek (Wuensche laufen nach dem aktuellen Song, danach die Playlist) mit Skip-Abstimmung /peaksoundvote.
- Radio: Radio-Sender (haengt im Redstone-Netz einer Jukebox, einstellbare Frequenz 87,5-108,0 MHz), Radio-Empfaenger (Frequenz, Reichweite, Lautstaerke, Rauschen ohne Sender) und Taschenradio (weltweit, Frequenz im Item). Radios sind Spielerkoepfe als Traeger.
- Song-Bibliothek: Cover-Vorschau je Song, Sortierung (Titel, Interpret, Neueste, Laengste, Beliebt), Filter (alle, Favoriten, meine), Favoriten, Beliebtheitszaehler (Wiedergaben und Beschreiben von Platten); eigene Songs umbenennen und loeschen (Operatoren auch fremde). Protokollversion 2: Mod und Plugin muessen beide aktualisiert werden.
- Jukebox mit eigenem Inventar (15 Platten-Slots), Hopper/Kisten nur ueber Seiten-Einstellung "Items rein/raus".
- Upload-Fenster (Shift-Klick mit leerer Platte): Knopf "Song vom Server auswaehlen" oeffnet die durchsuchbare Song-Liste; ein Klick schreibt den Song auf die Platte, ohne neuen Upload (Server prueft Upload-Ticket und leere Platte).

Erste Version für Minecraft 26.2 (Fabric-Mod + Paper-Plugin).

- Schallplatten per Upload (MP3, FLAC, WAV, OGG, bis 4 Minuten), Cover und Tags im Tooltip
- PeakSound-Jukebox mit Menü, Playlist über Hopper/Kisten, Redstone-Verbund mit synchroner Wiedergabe
- Comparator-Equalizer (Bass, Mitten, Höhen, gesamt)
- Lautsprecher mit bis zu 12 Positionen (7.1.4), Nummern, Bauart (Vollbereich, Tieftöner, Hochtöner, Subwoofer), Stereo-Upmix
- OP-Menü: Songs verwalten, Freigaben, Limits
- Streaming mit Client-Cache, eigener Lautstärkeregler, Verbindungsanzeige, Einstellungen kopieren
- DJ-Pult mit Crossfader (gleiche Gesamtleistung, synchron für alle Spieler)
- Scheinwerfer mit Farben, Modi, Effekten und Strobo-Schutz
- Mikrofon-Block über Simple Voice Chat (optional)
- Eigene Texturen, Rezeptbuch-Reiter 