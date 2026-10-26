// Erzeugt Modelle (mit Vanilla-Texturen), Item-Definitionen und den Notenblock-Blockstate der Mod.
// Aufruf: node fabric-mod/tools/gen_assets.js   (nur Node-Standardbibliothek)
const fs = require("fs"), path = require("path"), zlib = require("zlib");

const OUT = path.join(__dirname, "..", "src", "client", "resources", "assets");
const PS = path.join(OUT, "peaksound");
const MC = path.join(OUT, "minecraft");

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

// ---------- Modelle ----------
const cube = (side, top) => ({
  parent: "minecraft:block/cube",
  textures: { particle: side, down: top, up: top, north: side, south: side, east: side, west: side }
});
json(path.join(PS, "models/block/speaker.json"), cube("minecraft:block/note_block", "minecraft:block/note_block"));
json(path.join(PS, "models/block/jukebox.json"), cube("minecraft:block/jukebox_side", "minecraft:block/jukebox_top"));
// Items nutzen direkt vorhandene Vanilla-Texturen (es werden keine Mojang-Dateien kopiert)
const items = { blank_record: "minecraft:item/music_disc_11", written_record: "minecraft:item/music_disc_cat", peak_tuner: "minecraft:item/breeze_rod" };
for (const [id, tex] of Object.entries(items)) {
  json(path.join(PS, `models/item/${id}.json`), {
    parent: id === "peak_tuner" ? "minecraft:item/handheld" : "minecraft:item/generated",
    textures: { layer0: tex }
  });
}

// ---------- Item-Definitionen (ersetzen die Vanilla-Definitionen, Fallback = Vanilla-Modell) ----------
function itemDef(file, vanillaModel, cases) {
  json(path.join(MC, "items", file), {
    model: {
      type: "minecraft:select",
      property: "minecraft:custom_model_data",
      index: 0,
      cases: Object.entries(cases).map(([when, model]) => ({ when, model: { type: "minecraft:model", model } })),
      fallback: { type: "minecraft:model", model: vanillaModel }
    }
  });
}
itemDef("disc_fragment_5.json", "minecraft:item/disc_fragment_5",
  { blank_record: "peaksound:item/blank_record", written_record: "peaksound:item/written_record" });
itemDef("breeze_rod.json", "minecraft:item/breeze_rod", { peak_tuner: "peaksound:item/peak_tuner" });
itemDef("note_block.json", "minecraft:block/note_block",
  { jukebox: "peaksound:block/jukebox", speaker: "peaksound:block/speaker" });


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
    textures: { particle: "minecraft:block/note_block", base: "minecraft:block/note_block", num: `peaksound:block/speaker_digit_${n}` },
    elements: [
      { from: [0, 0, 0], to: [16, 16, 16], faces: { down: face("#base"), up: face("#base"), north: face("#base"), south: face("#base"), west: face("#base"), east: face("#base") } },
      { from: [-0.01, -0.01, -0.01], to: [16.01, 16.01, 16.01], faces: { north: overlayFace("#num"), south: overlayFace("#num"), west: overlayFace("#num"), east: overlayFace("#num") } }
    ]
  });
}
// ---------- Notenblock-Blockstate: alle 1150 Zustaende, PeakSound-Zustaende mit eigenem Modell ----------
const instruments = ["harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar", "chime", "xylophone",
  "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling", "zombie", "skeleton", "creeper",
  "dragon", "wither_skeleton", "piglin", "custom_head"];
const special = { "bit/23/true": "peaksound:block/jukebox", "bit/24/true": "peaksound:block/speaker" };
const variants = {};
for (const inst of instruments) {
  for (let note = 0; note <= 24; note++) {
    for (const powered of ["false", "true"]) {
      let model = special[`${inst}/${note}/${powered}`] || "minecraft:block/note_block";
      // Lautsprecher mit Nummer: custom_head + powered, Ton = Nummer - 1
      if (inst === "custom_head" && powered === "true" && note < MAX_SPEAKERS) model = `peaksound:block/speaker_${note + 1}`;
      variants[`instrument=${inst},note=${note},powered=${powered}`] = { model };
    }
  }
}
json(path.join(MC, "blockstates", "note_block.json"), { variants });
console.log("ok");
