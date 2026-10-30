# Bauen

Alles baut mit einem Gradle-Wrapper aus dem Projektordner. Gebraucht wird nur **Java 25** (JDK). Gradle lädt der Wrapper selbst.

## Beide Teile bauen

```bash
./gradlew build
```

Unter Windows: `gradlew.bat build`. Das Ergebnis:

| Datei | Zweck |
|---|---|
| `paper-plugin/build/libs/PeakSound-<version>.jar` | Server-Plugin |
| `fabric-mod/build/libs/fabric-mod-<version>.jar` | Client-Mod (mit eingebetteten Bibliotheken, Jar-in-Jar) |

`./gradlew build` führt auch alle Unit-Tests aus.

## Nur ein Teil

```bash
./gradlew :paper-plugin:build     # Plugin (baut protocol und audio-core mit)
./gradlew :fabric-mod:build       # Mod
./gradlew :protocol:test          # Protokoll- und Logik-Tests
./gradlew :audio-core:test        # Audio-Tests
```

## Entwickeln und testen

Ein lokaler Testserver und ein Dev-Client:

```bash
./gradlew :fabric-mod:runClient   # startet Minecraft mit der Mod und verbindet sich mit localhost:25565
```

Der Dev-Client verbindet sich mit dem Spieler `PeakDev` (siehe `fabric-mod/build.gradle.kts`). Als Server taugt ein
Paper-26.2-Server mit `online-mode=false` und dem gebauten Plugin in `plugins/`. Im PeakVerse-Verbund liegt er unter
`../run/server` (gemeinsamer Test-Server aller Module); `../tools/build-all.ps1` baut und kopiert die Jars dorthin.

Hilfsmittel (brauchen Node.js beziehungsweise Java):

| Befehl | Zweck |
|---|---|
| `node fabric-mod/tools/gen_assets.js` | erzeugt Modelle, Item-Definitionen und Zahlen-Overlays (Notenblock-Zustaende ordnet `PeakModels` per Code zu) |
| `node tools/make_test_audio.js` | erzeugt Test-WAV-Dateien in `run/test-audio/` |
| `node branding/gen_logo.js` | erzeugt das Logo als SVG und PNG |
| `java tools/MakeTestSongs.java ...` | legt Test-Songs direkt im Song-Speicher des Testservers an |

## Bibliotheken und Lizenzen

Die Audio-Bibliotheken stehen unter der LGPL und werden **nicht** in unser Jar eingeschmolzen: Das Plugin lädt sie
beim Start (`libraries:` in `plugin.yml`), die Mod bindet sie als eigene Jars ein. Details: [THIRD-PARTY-LICENSES.md](../THIRD-PARTY-LICENSES.md).
