# Changelog

## 0.1.0
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