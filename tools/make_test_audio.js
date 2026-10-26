// Erzeugt Test-Audiodateien fuer die Upload-Tests: node tools/make_test_audio.js
// - test_stereo.wav: 20 s Stereo, linker Kanal tiefer Ton, rechter hoeher, wechselnd (hoerbar im 3D-Raum)
// - test_6ch.wav:    10 s mit 6 Kanaelen (testet die Mehrkanal-Warnung und die Freigabe)
// - test_toolong.wav: 5 Minuten (muss mit "zu lang" abgelehnt werden)
const fs = require("fs"), path = require("path");
const OUT = path.join(__dirname, "..", "run", "test-audio");
fs.mkdirSync(OUT, { recursive: true });

function wav(file, channels, seconds, rate, sample) {
  const frames = Math.floor(seconds * rate);
  const dataLen = frames * channels * 2;
  const buf = Buffer.alloc(44 + dataLen);
  buf.write("RIFF", 0); buf.writeUInt32LE(36 + dataLen, 4); buf.write("WAVE", 8);
  buf.write("fmt ", 12); buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20); buf.writeUInt16LE(channels, 22);
  buf.writeUInt32LE(rate, 24); buf.writeUInt32LE(rate * channels * 2, 28); buf.writeUInt16LE(channels * 2, 32);
  buf.writeUInt16LE(16, 34); buf.write("data", 36); buf.writeUInt32LE(dataLen, 40);
  for (let i = 0; i < frames; i++) {
    for (let c = 0; c < channels; c++) {
      buf.writeInt16LE(Math.round(sample(i / rate, c) * 9000), 44 + (i * channels + c) * 2);
    }
  }
  fs.writeFileSync(path.join(OUT, file), buf);
}

// Zwei Toene im Wechsel (jede Sekunde), damit man Sync und Einstieg mitten im Song hoeren kann
const tone = (t, c) => Math.sin(2 * Math.PI * (c % 2 === 0 ? 220 : 330) * t) * (Math.floor(t) % 2 === c % 2 ? 1 : 0.35);
wav("test_stereo.wav", 2, 20, 44100, tone);
wav("test_6ch.wav", 6, 10, 48000, tone);
wav("test_toolong.wav", 1, 300, 8000, (t) => Math.sin(2 * Math.PI * 440 * t));
console.log("ok: " + OUT);
