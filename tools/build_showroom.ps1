# Baut die PeakSound-Showroom-Karte (Plaza mit Tutorial-Tafeln und vier Raeume mit Beispielbauten) per RCON
# in die Overworld des Testservers. Voraussetzung: Testserver laeuft, RCON an (Passwort peaktest, Port 25575),
# Plugin mit den Testbefehlen (/peaksound place|play|speaker|light) geladen, die drei Test-Songs Testbeat, Stereo breit und Surround-Demo (siehe tools/MakeTestSongs.java).
# Aufruf:  powershell -File tools\build_showroom.ps1 [-Player PeakDev]
param([string]$Player = "PeakDev", [string]$Password = "peaktest", [int]$Port = 25575)

$cmds = New-Object System.Collections.Generic.List[string]
function C([string]$s) { $script:cmds.Add($s) }

function Sign([int]$x, [int]$y, [int]$z, [string]$facing, [string[]]$lines) {
    $l = @($lines) + @("", "", "", "")
    $m = ($l[0..3] | ForEach-Object { '{text:"' + $_ + '"}' }) -join ","
    C "setblock $x $y $z oak_wall_sign[facing=$facing]{front_text:{messages:[$m]}}"
}
function Place([string]$type, [int]$x, [int]$y, [int]$z) { C "peaksound place $type $x $y $z" }
function Speaker([int]$x, [int]$y, [int]$z, [string]$channel) { Place speaker $x $y $z; C "peaksound speaker $x $y $z $channel" }
function Room([int]$x1, [int]$z1, [int]$x2, [int]$z2, [string]$wall, [string]$floor) {
    C "fill $x1 -61 $z1 $x2 -55 $z2 $wall hollow"
    C ("fill {0} -61 {1} {2} -61 {3} {4}" -f ($x1 + 1), ($z1 + 1), ($x2 - 1), ($z2 - 1), $floor)
}
function Lantern([int]$x, [int]$z) { C "setblock $x -55 $z sea_lantern" }

# ---------- Vorbereitung ----------
C "forceload add 205 205 275 275"
C "difficulty peaceful"
C "gamerule advance_time false"
C "time set noon"
C "weather clear"
C "fill 205 -60 205 275 -52 240 air"
C "fill 205 -60 241 275 -52 275 air"
C "fill 205 -61 205 275 -61 275 grass_block"

# ---------- Plaza ----------
C "fill 232 -61 232 248 -61 248 polished_andesite"
C "fill 233 -61 233 247 -61 247 smooth_stone"
C "fill 237 -60 240 243 -58 240 stone_bricks"
foreach ($x in 232, 248) { foreach ($z in 232, 248) { C "fill $x -60 $z $x -57 $z stone_bricks"; C "setblock $x -56 $z sea_lantern" } }
# Vorderseite der Tafelwand (nach Sueden): Grundlagen
Sign 237 -59 241 south @("WILLKOMMEN", "im PeakSound", "Showroom", "-> Tafeln lesen")
Sign 238 -59 241 south @("PEAK-TUNER", "Holzhacke +", "Redstone-Staub", "(Werkzeug)")
Sign 239 -59 241 south @("PEAKSOUND-", "JUKEBOX", "Diamant oben,", "Jukebox, Staub L+R")
Sign 240 -59 241 south @("LAUTSPRECHER", "Notenblock Mitte", "Eisen oben+unten", "Staub links+rechts")
Sign 241 -59 241 south @("LEERE PLATTEN", "Obsidian +", "Diamantschwert", "= 9 Platten")
Sign 242 -59 241 south @("MUSIK HOCHLADEN", "Leere Platte in", "der Hand, Shift+", "Klick auf Jukebox")
Sign 243 -59 241 south @("MENUES", "Peak-Tuner in", "der Hand:", "Rechtsklick Block")
# Rueckseite (nach Norden): Redstone
Sign 237 -59 239 north @("REDSTONE-STAUB", "verbindet Boxen", "mit der Jukebox", "= ein Netz")
Sign 238 -59 239 north @("EIN NETZ", "alle Jukeboxen", "spielen dasselbe", "zeitgleich")
Sign 239 -59 239 north @("COMPARATOR", "an der Jukebox:", "N=Bass O=Mitten", "S=Hoehen W=Alles")
Sign 240 -59 239 north @("DJ-PULT", "2 Jukeboxen +", "Redstone + Diamant", "Decks per Staub")
Sign 241 -59 239 north @("SCHEINWERFER", "per Staub an", "eine Jukebox,", "Farbe+Effekte")
Sign 242 -59 239 north @("MIKROFON", "Voice Chat noetig", "Stimme kommt aus", "den Lautsprechern")
Sign 243 -59 239 north @("PEAK-TUNER", "Shift+Klick:", "Einstellungen", "kopieren/einfuegen")
# Wegweiser an den Raumeingaengen (an der Raumwand, zur Plaza gewandt)
Sign 240 -57 232 south @("NORD", "Stereo-Anlage", "+ Mikrofon", "")
Sign 240 -57 248 north @("SUED", "DJ-Pult +", "Scheinwerfer", "")
Sign 232 -57 240 east @("WEST", "Lichtshow +", "Equalizer", "")
Sign 248 -57 240 west @("OST", "Surround", "7.1.4", "")

# Song-Bibliothek und Fernbedienung zum Ausprobieren auf der Plaza
Place library 236 -60 244
Sign 236 -59 245 south @("SONG-BIBLIOTHEK", "Leere Platte in", "der Hand, Rechts-", "klick: Song waehlen")
C "peaksound give $Player blank_record 8"

# ---------- Nord: Stereo-Anlage + Mikrofon ----------
Room 232 215 248 231 stone_bricks polished_diorite
C "fill 239 -60 231 241 -58 231 air"
foreach ($p in @(@(236, 220), @(244, 220), @(236, 226), @(244, 226))) { Lantern $p[0] $p[1] }
Place jukebox 240 -60 217
C "fill 234 -60 218 246 -60 218 redstone_wire"
Speaker 233 -60 218 FRONT_LEFT
Speaker 247 -60 218 FRONT_RIGHT
Place microphone 240 -60 219
C "peaksound play 240 -60 217 Stereo"
Place remote 242 -60 219
C "peaksound remote 242 -60 219 pause_toggle"
C "setblock 243 -60 219 lever[face=wall,facing=east,powered=false]"
Sign 242 -59 216 south @("FERNBEDIENUNG", "Hebel = Pause/", "Weiter. Menue:", "Aktion waehlen")
Sign 235 -59 216 south @("STEREO-ANLAGE", "1 Jukebox", "2 Staub-Linie", "3 Boxen L + R")
Sign 237 -59 216 south @("Jede Box hat", "ein Menue:", "Name, Position,", "Lautstaerke, Typ")
Sign 243 -59 216 south @("MIKROFON", "am Staub der", "Anlage: wer nah", "dran spricht...")
Sign 245 -59 216 south @("...wird ueber", "die Boxen", "uebertragen", "(Voice Chat)")

# ---------- Ost: Surround 7.1.4 ----------
Room 249 232 265 248 stone_bricks polished_andesite
C "fill 249 -60 239 249 -58 241 air"
foreach ($p in @(@(254, 236), @(254, 244), @(260, 236), @(260, 244))) { Lantern $p[0] $p[1] }
C "fill 251 -60 234 263 -60 234 redstone_wire"
C "fill 251 -60 246 263 -60 246 redstone_wire"
C "fill 251 -60 235 251 -60 245 redstone_wire"
C "fill 263 -60 235 263 -60 245 redstone_wire"
Place jukebox 257 -60 234
Speaker 264 -60 236 FRONT_LEFT
Speaker 264 -60 240 CENTER
Speaker 264 -60 244 FRONT_RIGHT
Speaker 264 -60 234 SUBWOOFER
Speaker 259 -60 233 SIDE_LEFT
Speaker 259 -60 247 SIDE_RIGHT
Speaker 250 -60 236 REAR_LEFT
Speaker 250 -60 244 REAR_RIGHT
# Hoehenlautsprecher: Treppe aus Bloecken mit Staub, oben der Lautsprecher auf einem Sockel
function Tower([int]$startX, [int]$z, [int]$dir, [string]$channel) {
    for ($i = 1; $i -le 3; $i++) {
        $x = $startX + $dir * $i
        C ("fill {0} -60 {1} {0} {2} {1} stone_bricks" -f $x, $z, (-61 + $i))
        C ("setblock {0} {1} {2} redstone_wire" -f $x, (-60 + $i), $z)
    }
    $sx = $startX + $dir * 4
    C ("fill {0} -60 {1} {0} -58 {1} stone_bricks" -f $sx, $z)
    Speaker $sx -57 $z $channel
}
Tower 263 235 -1 TOP_FRONT_LEFT
Tower 263 245 -1 TOP_FRONT_RIGHT
Tower 251 235 1 TOP_REAR_LEFT
Tower 251 245 1 TOP_REAR_RIGHT
C "peaksound play 257 -60 234 Surround"
Sign 252 -59 247 north @("SURROUND 7.1.4", "12 Boxen, jede", "mit eigener", "Position")
Sign 254 -59 247 north @("Stell dich in", "die Mitte: der", "Ton kommt aus", "allen Richtungen")
Sign 262 -59 247 north @("Position je Box", "im Box-Menue", "(< und >)", "waehlbar")
Sign 252 -59 233 south @("Stereo-Songs", "bleiben vorn;", "Upmix im Reiter", "Speakers")

# ---------- Sued: DJ-Pult + Scheinwerfer ----------
Room 232 249 248 265 stone_bricks polished_blackstone
C "fill 239 -60 249 241 -58 249 air"
Lantern 240 252
Place dj_desk 240 -60 258
C "fill 238 -60 258 239 -60 258 redstone_wire"
Place jukebox 237 -60 258
C "fill 235 -60 258 236 -60 258 redstone_wire"
Speaker 234 -60 258 FRONT_LEFT
C "fill 241 -60 258 242 -60 258 redstone_wire"
Place jukebox 243 -60 258
C "fill 244 -60 258 245 -60 258 redstone_wire"
Speaker 246 -60 258 FRONT_RIGHT
foreach ($p in @(@(236, 259), @(236, 257), @(244, 259), @(244, 257))) { Place spotlight $p[0] -60 $p[1] }
C "peaksound play 237 -60 258 Testbeat"
C "peaksound play 243 -60 258 Stereo"
C "peaksound light 236 -60 259 FF0000 beat none bass 0 70 5"
C "peaksound light 236 -60 257 FF8000 steady sweep bass 0 60 4"
C "peaksound light 244 -60 259 0000FF beat none mid 0 70 5"
C "peaksound light 244 -60 257 00FFFF steady sweep bass 0 60 4"
Sign 235 -59 264 north @("DJ-PULT", "Deck A links,", "Deck B rechts:", "je ein Staub-Strang")
Sign 237 -59 264 north @("Peak-Tuner auf", "das Pult: Song", "je Deck, Crossfader", "+ Ueberblendzeit")
Sign 243 -59 264 north @("SCHEINWERFER", "haengen am Staub", "der Decks und", "folgen der Musik")
Sign 245 -59 264 north @("Menue: Farben,", "Modus, Effekt,", "Band, Richtung", "(Strobo: Option)")

# ---------- West: Lichtshow + Equalizer (Ausgaenge je Seite im Jukebox-Menue unter Ausgaenge) ----------
Room 215 232 231 248 stone_bricks polished_blackstone
C "fill 231 -60 239 231 -58 241 air"
Lantern 230 240
# Equalizer: Jukebox mit vier Comparatoren (der Blockstate facing zeigt zum Eingang, also zur Jukebox)
Place jukebox 223 -60 236
C "peaksound play 223 -60 236 Testbeat"
C "setblock 223 -60 235 comparator[facing=south]"
C "setblock 223 -60 234 sticky_piston[facing=up]"
C "setblock 223 -59 234 slime_block"
C "setblock 224 -60 236 comparator[facing=west]"
C "setblock 225 -60 236 redstone_lamp"
C "setblock 223 -60 237 comparator[facing=north]"
C "setblock 223 -60 238 redstone_lamp"
C "setblock 222 -60 236 comparator[facing=east]"
C "setblock 221 -60 236 redstone_lamp"
Sign 220 -59 233 south @("EQUALIZER", "Comparator an", "der Jukebox:", "Pegel = Musik")
Sign 226 -59 233 south @("NORD: Bass", "(Kolben wummert)", "OST: Mitten", "SUED: Hoehen")
Sign 216 -59 236 east @("WEST: alles", "Lampe leuchtet", "im Takt. Baue", "eigene Effekte!")
# Lichtshow: Jukebox mit vier Scheinwerfern
Place jukebox 223 -60 244
C "fill 224 -60 244 229 -60 244 redstone_wire"
foreach ($p in @(@(225, 245), @(227, 245), @(225, 243), @(227, 243))) { Place spotlight $p[0] -60 $p[1] }
C "peaksound play 223 -60 244 Testbeat"
C "peaksound light 225 -60 245 FF0000 beat none bass 0 75 5"
C "peaksound light 227 -60 245 00FF00 beat sweep mid 90 60 4"
C "peaksound light 225 -60 243 0000FF beat none high 180 75 5"
C "peaksound light 227 -60 243 FF00FF,00FFFF steady color_cycle bass 270 60 6"
Sign 226 -59 247 north @("LICHTSHOW", "4 Scheinwerfer", "am Staub der", "Jukebox")
Sign 228 -59 247 north @("Rot: Bass-Takt", "Gruen: Mitten +", "Schwenken", "Blau: Hoehen")
Sign 220 -59 247 north @("Mehrfarbig mit", "Farbwechsel.", "Strobo gibt es", "auch (mit Warnung)")

# ---------- Nordost: Radio-Ecke ----------
C "fill 251 -60 218 263 -58 218 stone_bricks"
Place jukebox 252 -60 222
C "setblock 253 -60 222 redstone_wire"
Place transmitter 254 -60 222
C "peaksound radio 254 -60 222 1000"
C "peaksound play 252 -60 222 Testbeat"
Place receiver 258 -60 222
C "peaksound radio 258 -60 222 1000"
Place receiver 258 -60 226
C "peaksound radio 258 -60 226 950"
C "peaksound give $Player radio_receiver 2"
C "peaksound give $Player radio_transmitter 1"
C "peaksound give $Player pocket_radio 1"
Sign 252 -59 219 south @("RADIO-SENDER", "haengt am Staub", "der Jukebox und", "sendet ihren Song")
Sign 254 -59 219 south @("FREQUENZ", "Tuner-Rechtsklick:", "87,5-108,0 MHz", "(hier 100,0)")
Sign 258 -59 219 south @("RADIO", "gleiche Frequenz", "= Musik, sonst", "Rauschen (95,0)")
Sign 260 -59 219 south @("TASCHENRADIO", "Rechtsklick an/aus", "Shift: Menue", "ueberall hoerbar")

# ---------- Abschluss ----------
C "tp $Player 240 -60 236 0 0"
C "setworldspawn 240 -60 236"

# Senden (RCON), in Stuecken von 40 Befehlen; Antworten mit Fehlern werden angezeigt
$client = New-Object Net.Sockets.TcpClient("127.0.0.1", $Port)
$stream = $client.GetStream()
function Send($id, $type, $body) {
    $b = [Text.Encoding]::UTF8.GetBytes($body)
    $ms = New-Object IO.MemoryStream
    $w = New-Object IO.BinaryWriter($ms)
    $w.Write([int](10 + $b.Length)); $w.Write([int]$id); $w.Write([int]$type); $w.Write($b); $w.Write([byte]0); $w.Write([byte]0); $w.Flush()
    $a = $ms.ToArray()
    $stream.Write($a, 0, $a.Length)
    $r = New-Object IO.BinaryReader($stream)
    $len = $r.ReadInt32(); [void]$r.ReadInt32(); [void]$r.ReadInt32()
    $payload = $r.ReadBytes($len - 10); [void]$r.ReadBytes(2)
    [Text.Encoding]::UTF8.GetString($payload)
}
[void](Send 1 3 $Password)
$n = 0
foreach ($cmd in $cmds) {
    $reply = Send 2 2 $cmd
    $n++
    if ($reply -match "Unknown|Expected|Could not|Incorrect|error|Fehler|nicht" -and $cmd -notmatch "^fill") { Write-Host "[$n] $cmd`n    -> $reply" }
}
$client.Close()
Write-Host "Fertig: $n Befehle gesendet."
