# Bibliotheken von Dritten

PeakSound selbst steht unter der MIT-Lizenz. Diese Bibliotheken werden benutzt:

| Bibliothek | Zweck | Lizenz |
|---|---|---|
| [jaudiotagger](https://bitbucket.org/ijabz/jaudiotagger) 3.0.1 | Tags, Cover, Länge | LGPL 2.1 oder neuer |
| [JLayer](https://github.com/umjammer/jlayer) 1.0.1.4 | MP3-Decoder | LGPL |
| [JOrbis](http://www.jcraft.com/jorbis/) 0.0.17 | Ogg-Vorbis-Decoder | LGPL |
| [jFLAC](https://sourceforge.net/projects/jflac/) 1.5.2 | FLAC-Decoder | LGPL 2.1 |
| [java-flac-encoder](https://sourceforge.net/projects/flacenc/) 0.3.7 | FLAC-Encoder | LGPL 2.1 |
| LWJGL (OpenAL, STB, tinyfd) | Audio und Dateifenster, mit Minecraft geliefert | BSD |
| Fabric API / Loader | Mod-Plattform | Apache 2.0 |
| Paper API | Server-Plattform | MIT |
| Gson | JSON (von Paper mitgeliefert) | Apache 2.0 |

**Zur LGPL:** Die LGPL-Bibliotheken liegen unverändert und als eigene Jar-Dateien vor, nicht eingeschmolzen: auf dem
Server werden sie beim Start von Maven Central geladen, in der Mod stecken sie als eingebettete Jars (Jar-in-Jar).
Du kannst sie durch eine eigene Version derselben Bibliothek ersetzen. Die Lizenztexte stehen in den jeweiligen
Projekten und in den Jar-Dateien.

Die Minecraft-Texturen (Notenblock, Jukebox, Schallplatten) werden **nicht kopiert**: Die Modelle der Mod verweisen
zur Laufzeit auf die vorhandenen Dateien des Spiels. Nur die Zahlen-Overlays der Lautsprecher und das Logo sind selbst gezeichnet.
