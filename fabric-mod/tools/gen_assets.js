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

// ---------- Notenblock-Blockstate: alle 1150 Zustaende, PeakSound-Zustaende mit eigenem Modell ----------
const instruments = ["harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar", "chime", "xylophone",
  "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling", "zombie", "skeleton", "creeper",
  "dragon", "wither_skeleton", "piglin", "custom_head"];
const special = { "bit/23/true": "peaksound:block/jukebox", "bit/24/true": "peaksound:block/speaker" };
const variants = {};
for (const inst of instruments) {
  for (let note = 0; note <= 24; note++) {
    for (const powered of ["false", "true"]) {
      const model = special[`${inst}/${note}/${powered}`] || "minecraft:block/note_block";
      variants[`instrument=${inst},note=${note},powered=${powered}`] = { model };
    }
  }
}
json(path.join(MC, "blockstates", "note_block.json"), { variants });
console.log("ok");
