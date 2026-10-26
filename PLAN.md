# PeakSound – Plan

Ziel: Minecraft 26.2, Fabric-Client-Mod + Paper-Plugin + gemeinsames Protokoll. Architektur: siehe ARCHITECTURE.md.

## Phase 1 – Grundgerüst, Items, Blöcke, Rezepte, Peak-Tuner
- [x] Gradle-Multiprojekt (protocol, paper-plugin, fabric-mod), Wrapper, MIT-Lizenz
- [x] Protokoll: PacketCodec, Hello/HelloAck, Item-/Block-Typen, Schwert-Rechnung + Unit-Tests
- [x] Plugin: Items (leere/beschriebene Platte, Jukebox, Lautsprecher, Peak-Tuner)
- [x] Plugin: Rezepte inkl. Rezeptbuch, Schwert mit halber Haltbarkeit (auch Shift-Klick)
- [x] Plugin: Block-Registry (Platzieren, Abbauen, Schutz), Peak-Tuner-Info
- [x] Plugin: Handshake, Versions-Check, Hinweis für Spieler ohne Mod
- [ ] Mod: Grundgerüst, Handshake, Creative-Tab, de/en Sprachdateien
- [ ] Mod: Item-/Block-Darstellung (Modelle, Texturen)
- [ ] Phase-1-Abnahme: Build, Server startet, Client verbindet, Checkliste

## Phase 2 – Upload, Speicherung, 3D-Audio im Multiplayer
## Phase 3 – Jukebox-Menü
## Phase 4 – Hopper-Playlist und Kisten-Erweiterung
## Phase 5 – Redstone-Verbund und Comparator-Equalizer
## Phase 6 – Lautsprecher und Surround
## Phase 7 – OP-Menü, Freigabe-System, Limits
## Phase 8 – Optimierungen und Feinschliff
## Phase 9 – DJ-Pult und Scheinwerfer
## Phase 10 – Simple Voice Chat und Mikrofon
