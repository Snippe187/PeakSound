// Erzeugt Modelle (mit Vanilla-Texturen), Item-Definitionen der Mod.
// Aufruf: node fabric-mod/tools/gen_assets.js   (nur Node-Standardbibliothek)
const fs = require("fs"), path = require("path"), zlib = require("zlib");

const OUT = path.join(__dirname, "..", "src", "client", "resources", "assets");
const PS = path.join(OUT, "peaksound");

function write(file, content) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, content);
}
const json = (file, obj) => write(file, JSON.stringify(obj, null, 2) + "\n");

// ---------- PNG (nur fuer die selbst gezeichneten Ziffern-Overlays) ----------
const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = b => { let c = 0xffffffff; for (const v of b) c = crcTable[(c ^ v) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const t = Buffer.from(type), len = Buffer.alloc(4), crc = Buffer.alloc(4);
  len.writeUInt32BE(data.length); crc.writeUInt32BE(crc32(Buffer.concat([t, data])));
  return Buffer.concat([len, t, data, crc]);
}
function png(size, px) {
  const raw = Buffer.alloc(size * (size * 4 + 1));
  let i = 0;
  for (let y = 0; y < size; y++) {
    raw[i++] = 0;
    for (let x = 0; x < size; x++) {
      const c = px(x, y);
      if (c) { raw[i++] = c[0]; raw[i++] = c[1]; raw[i++] = c[2]; raw[i++] = 255; }
      else { raw[i++] = 0; raw[i++] = 0; raw[i++] = 0; raw[i++] = 0; }
    }
  }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(size, 0); ihdr.writeUInt32BE(size, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]);
}

// ---------- Texturen (selbst gezeichnet, siehe gen_textures.js) ----------
require("./gen_textures.js")(png, write, PS);

// ---------- Modelle ----------
const cube = (side, top) => ({
  parent: "minecraft:block/cube",
  textures: { particle: side, down: top, up: top, north: side, south: side, east: side, west: side }
});
json(path.join(PS, "models/block/speaker.json"), cube("peaksound:block/speaker_side", "peaksound:block/speaker_top"));
json(path.join(PS, "models/block/jukebox.json"), cube("peaksound:block/jukebox_side", "peaksound:block/jukebox_top"));
json(path.join(PS, "models/block/dj_desk.json"), cube("peaksound:block/dj_desk_side", "peaksound:block/dj_desk_top"));
json(path.join(PS, "models/block/microphone.json"), cube("peaksound:block/microphone_side", "peaksound:block/microphone_top"));
json(path.join(PS, "models/block/music_remote.json"), cube("peaksound:block/remote_side", "peaksound:block/remote_top"));
json(path.join(PS, "models/block/song_library.json"), cube("peaksound:block/library_side", "peaksound:block/library_top"));
json(path.join(PS, "models/block/spotlight.json"), cube("peaksound:block/spotlight_side", "peaksound:block/spotlight_top"));
json(path.join(PS, "models/block/display.json"), cube("peaksound:block/display_side", "peaksound:block/display_top"));
json(path.join(PS, "models/block/radio_receiver.json"), cube("peaksound:block/radio_receiver_side", "peaksound:block/radio_receiver_top"));
json(path.join(PS, "models/block/radio_transmitter.json"), cube("peaksound:block/radio_transmitter_side", "peaksound:block/radio_transmitter_top"));
// Items nutzen direkt vorhandene Vanilla-Texturen (es werden keine Mojang-Dateien kopiert)
const items = { blank_record: "peaksound:item/blank_record", written_record: "peaksound:item/written_record", peak_tuner: "peaksound:item/peak_tuner", pocket_radio: "peaksound:item/pocket_radio" };
for (const [id, tex] of Object.entries(items)) {
  json(path.join(PS, `models/item/${id}.json`), {
    parent: id === "peak_tuner" ? "minecraft:item/handheld" : "minecraft:item/generated",
    textures: { layer0: tex }
  });
}

// ---------- Item-Definitionen (nur unter peaksound:, KEINE Vanilla-Dateien: die Zuordnung Traeger-Item -> Modell macht PeakModels.java per Code) ----------
const itemModels = { blank_record: "peaksound:item/blank_record", written_record: "peaksound:item/written_record", peak_tuner: "peaksound:item/peak_tuner", pocket_radio: "peaksound:item/pocket_radio" };
for (const id of ["jukebox", "speaker", "dj_desk", "spotlight", "microphone", "song_library", "music_remote", "display", "radio_receiver", "radio_transmitter"]) itemModels[id] = `peaksound:block/${id}`;
for (const [id, model] of Object.entries(itemModels)) json(path.join(PS, "items", `${id}.json`), { model: { type: "minecraft:model", model } });
// ---------- Lautsprecher-Nummern 1-25: durchsichtige Ziffern-Overlays auf dem Vanilla-Notenblock ----------
const DIGITS = {
  0: ["111", "101", "101", "101", "111"], 1: ["010", "110", "010", "010", "111"], 2: ["111", "001", "111", "100", "111"],
  3: ["111", "001", "111", "001", "111"], 4: ["101", "101", "111", "001", "001"], 5: ["111", "100", "111", "001", "111"],
  6: ["111", "100", "111", "101", "111"], 7: ["111", "001", "001", "001", "001"], 8: ["111", "101", "111", "101", "111"],
  9: ["111", "101", "111", "001", "111"]
};
function numberTexture(n) {
  const text = String(n);
  const scale = 2, glyphW = 3 * scale, glyphH = 5 * scale, gap = 2;
  const totalW = text.length * glyphW + (text.length - 1) * gap;
  const x0 = Math.floor((16 - totalW) / 2), y0 = Math.floor((16 - glyphH) / 2);
  const lit = new Set();
  [...text].forEach((ch, k) => {
    DIGITS[ch].forEach((row, ry) => [...row].forEach((bit, rx) => {
      if (bit !== "1") return;
      for (let dy = 0; dy < scale; dy++) for (let dx = 0; dx < scale; dx++) lit.add((x0 + k * (glyphW + gap) + rx * scale + dx) + "," + (y0 + ry * scale + dy));
    }));
  });
  return png(16, (x, y) => {
    if (lit.has(x + "," + y)) return [255, 255, 255];
    for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) if (lit.has((x + dx) + "," + (y + dy))) return [20, 20, 20];
    return null;
  });
}
const MAX_SPEAKERS = 25;
const face = t => ({ texture: t });
// Das Overlay ragt minimal ueber den Block hinaus (gegen Z-Fighting); ohne feste UVs wuerde Minecraft die
// UV-Koordinaten aus der Elementgroesse ableiten und ausserhalb des Bildes landen (Modell wuerde nicht gebacken).
const overlayFace = t => ({ texture: t, uv: [0, 0, 16, 16] });
for (let n = 1; n <= MAX_SPEAKERS; n++) {
  write(path.join(PS, `textures/block/speaker_digit_${n}.png`), numberTexture(n));
  json(path.join(PS, `models/block/speaker_${n}.json`), {
    textures: { particle: "peaksound:block/speaker_side", base: "peaksound:block/speaker_side", top: "peaksound:block/speaker_top", num: `peaksound:block/speaker_digit_${n}` },
    elements: [
      { from: [0, 0, 0], to: [16, 16, 16], faces: { down: face("#top"), up: face("#top"), north: face("#base"), south: face("#base"), west: face("#base"), east: face("#base") } },
      { from: [-0.01, -0.01, -0.01], to: [16.01, 16.01, 16.01], faces: { north: overlayFace("#num"), south: overlayFace("#num"), west: overlayFace("#num"), east: overlayFace("#num") } }
    ]
  });
}
// Notenblock-Zustaende (Instrument/Ton -> Modell) ordnet PeakModels.java per Code zu; es gibt keine Blockstate-Datei der Mod mehr.
console.log("ok");
