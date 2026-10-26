// Erzeugt das PeakSound-Pixel-Logo als SVG und PNG (nur Node-Standardbibliothek).
const fs = require("fs"), path = require("path"), zlib = require("zlib");

const N = 36, C = (N - 1) / 2;
const BLACK = "#0b0d12", GROOVE = "#2c3446", SHINE = "#3a4358";
const ORANGE = "#ff7a3d", ORANGE_D = "#d95a1f", TEAL = "#38d6c4", TEAL_D = "#1f9c8e";

function pixel(x, y) {
  const dx = x - C, dy = y - C, r = Math.hypot(dx, dy);
  const ang = Math.atan2(dy, dx) * 180 / Math.PI;
  if (Math.abs(ang) <= 38) {            // Schallwellen rechts der Platte
    if (r >= 14.2 && r < 15.4) return TEAL;
    if (r >= 16.0 && r < 17.2) return TEAL_D;
  }
  if (r < 1.6) return BLACK;            // Loch
  if (r < 5.6) return (dx + dy) > 2.5 ? ORANGE_D : ORANGE;   // Label
  if (r < 13.4) {
    if ((r >= 9.6 && r < 10.6) || (r >= 7.2 && r < 8.0) || (r >= 12.0 && r < 12.8)) return GROOVE;
    if ((dx - dy) > -5 && (dx - dy) < -1 && dx < -4 && dy < -4 && r > 8) return SHINE;
    return BLACK;
  }
  return null;
}

const grid = [];
for (let y = 0; y < N; y++) { grid.push([]); for (let x = 0; x < N; x++) grid[y].push(pixel(x, y)); }

let rects = "";
grid.forEach((row, y) => row.forEach((c, x) => { if (c) rects += `<rect x="${x}" y="${y}" width="1" height="1" fill="${c}"/>`; }));
fs.writeFileSync(path.join(__dirname, "peaksound_logo.svg"),
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${N} ${N}" shape-rendering="crispEdges">${rects}</svg>\n`);

const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = b => { let c = 0xffffffff; for (const v of b) c = crcTable[(c ^ v) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const t = Buffer.from(type), len = Buffer.alloc(4), crc = Buffer.alloc(4);
  len.writeUInt32BE(data.length); crc.writeUInt32BE(crc32(Buffer.concat([t, data])));
  return Buffer.concat([len, t, data, crc]);
}
function writePng(file, scale) {
  const w = N * scale, raw = Buffer.alloc(w * (w * 4 + 1));
  let i = 0;
  for (let y = 0; y < w; y++) {
    raw[i++] = 0;
    for (let x = 0; x < w; x++) {
      const c = grid[Math.floor(y / scale)][Math.floor(x / scale)];
      if (c) { raw[i++] = parseInt(c.slice(1, 3), 16); raw[i++] = parseInt(c.slice(3, 5), 16); raw[i++] = parseInt(c.slice(5, 7), 16); raw[i++] = 255; }
      else { raw[i++] = 0; raw[i++] = 0; raw[i++] = 0; raw[i++] = 0; }
    }
  }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(w, 4); ihdr[8] = 8; ihdr[9] = 6;
  fs.writeFileSync(path.join(__dirname, file), Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]));
}
writePng("icon_144.png", 4);
writePng("icon_576.png", 16);
console.log("ok");
