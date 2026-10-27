// Zeichnet die PeakSound-Texturen (16x16, Pixel-Look) programmatisch. Wird von gen_assets.js aufgerufen.
// Jeder Block und jedes Item hat eine eigene, auf den ersten Blick unterscheidbare Optik:
//   Jukebox      violettes Gehaeuse mit Neon-Streifen, Schallplatte im Fenster; oben ein Plattenteller mit Tonarm
//   Lautsprecher dunkles Gehaeuse mit grossem Tiefton-Konus und kleinem Hochtoener
//   DJ-Pult      schwarz mit LED-Pegel und Fadern; oben zwei Plattenteller und der Crossfader
//   Scheinwerfer Metallgehaeuse mit Gefahrenstreifen; oben die leuchtende Linse
//   Mikrofon     Metallgitter-Kopf mit Goldring und Griff
//   Items        leere Platte (grau), beschriebene Platte (bunt), Peak-Tuner (Stimmgabel)
const path = require("path");

const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];

function canvas() {
  const p = Array.from({ length: 16 }, () => Array(16).fill(null));
  const c = {
    p,
    set(x, y, col) { x = Math.round(x); y = Math.round(y); if (x >= 0 && x < 16 && y >= 0 && y < 16) p[y][x] = typeof col === "string" ? hex(col) : col; return c; },
    fill(col) { for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) c.set(x, y, col); return c; },
    rect(x0, y0, x1, y1, col) { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) c.set(x, y, col); return c; },
    disc(cx, cy, r, col) { for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r) c.set(x, y, col); return c; },
    ring(cx, cy, r0, r1, col) { for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const d = (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2; if (d <= r1 * r1 && d >= r0 * r0) c.set(x, y, col); } return c; },
    border(col, inner) { for (let i = 0; i < 16; i++) { c.set(i, 0, col); c.set(i, 15, col); c.set(0, i, col); c.set(15, i, col); } if (inner) { for (let i = 1; i < 15; i++) { c.set(i, 1, inner); c.set(1, i, inner); } } return c; },
    corners(col) { [[1, 1], [14, 1], [1, 14], [14, 14]].forEach(([x, y]) => c.set(x, y, col)); return c; },
    line(x0, y0, x1, y1, col) { const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)); for (let i = 0; i <= n; i++) c.set(x0 + (x1 - x0) * i / (n || 1), y0 + (y1 - y0) * i / (n || 1), col); return c; }
  };
  return c;
}

module.exports = function (png, write, PS) {
  const save = (name, cv) => write(path.join(PS, name + ".png"), png(16, (x, y) => cv.p[y][x]));

  // ---------- Lautsprecher ----------
  {
    const side = canvas().fill("#4a4a54").border("#2b2b31", "#62626e").corners("#b8b8c2");
    side.disc(8, 3.6, 2.3, "#aeaeb8").disc(8, 3.6, 1.8, "#1c1c22").disc(8, 3.6, 0.9, "#7c7c88");        // Hochtoener
    side.disc(8, 10, 4.8, "#aeaeb8").disc(8, 10, 4.1, "#1c1c22").disc(8, 10, 3.0, "#3a3a44").disc(8, 10, 1.5, "#8a8a96");
    side.set(6, 8, "#6a6a76").set(7, 7, "#6a6a76");
    save("textures/block/speaker_side", side);
    const top = canvas().fill("#4a4a54").border("#2b2b31", "#62626e").corners("#b8b8c2");
    for (const y of [5, 7, 9, 11]) top.rect(4, y, 11, y, "#1c1c22");
    save("textures/block/speaker_top", top);
  }
  // ---------- Jukebox ----------
  {
    const side = canvas().fill("#2a2145").border("#5a4b8c", "#38305c");
    side.rect(2, 2, 13, 3, "#3fd0ee").rect(2, 2, 13, 2, "#8cecff");                                    // Neon-Streifen
    side.rect(3, 6, 12, 12, "#0b0b12");                                                                // Fenster
    side.disc(8, 9.5, 3.3, "#262633").disc(8, 9.5, 2.5, "#1a1a24").disc(8, 9.5, 1.4, "#ff9e2c").set(8, 9, "#0b0b12");
    for (let x = 2; x <= 13; x++) side.set(x, 14, x % 2 ? "#ff3c6e" : "#3fd0ee");
    save("textures/block/jukebox_side", side);
    const top = canvas().fill("#2a2145").border("#5a4b8c", "#38305c");
    top.disc(8, 8, 6.2, "#101018").ring(8, 8, 5.0, 5.3, "#242434").ring(8, 8, 3.6, 3.9, "#1d1d2a").disc(8, 8, 2.2, "#3fd0ee").set(8, 8, "#0b0b12");
    top.line(13, 2, 9, 6, "#c9c9d4").set(13, 2, "#8c8ca0").set(14, 2, "#8c8ca0").set(13, 3, "#8c8ca0");   // Tonarm
    save("textures/block/jukebox_top", top);
  }

  // ---------- DJ-Pult ----------
  {
    const side = canvas().fill("#1b1b22").border("#34343f", "#252530");
    side.rect(2, 2, 13, 3, "#0b0b10");
    const led = ["#39e06a", "#39e06a", "#e0e039", "#e08a39", "#e03939"];
    for (let i = 0; i < 6; i++) for (let k = 0; k < 2; k++) side.set(2 + i * 2 + k, 4 + 0, led[Math.min(i, 4)]);
    for (let i = 0; i < 6; i++) side.set(2 + i * 2, 5, led[Math.min(i, 4)]);
    [3, 6, 9, 12].forEach((x, i) => { side.rect(x, 8, x, 13, "#0a0a0e"); const y = [10, 9, 12, 10][i]; side.rect(x - 1, y, x + 1, y + 1, "#d9d9e3"); });
    save("textures/block/dj_desk_side", side);
    const top = canvas().fill("#1b1b22").border("#34343f", "#252530");
    top.disc(4.5, 6.5, 3.6, "#0e0e14").ring(4.5, 6.5, 2.6, 2.9, "#3a3a48").disc(4.5, 6.5, 1.3, "#ff4b6e");
    top.disc(11.5, 6.5, 3.6, "#0e0e14").ring(11.5, 6.5, 2.6, 2.9, "#3a3a48").disc(11.5, 6.5, 1.3, "#3fd0ee");
    top.rect(3, 12, 12, 13, "#0a0a0e").rect(7, 11, 8, 14, "#d9d9e3");                                  // Crossfader
    save("textures/block/dj_desk_top", top);
  }

  // ---------- Scheinwerfer ----------
  {
    const side = canvas().fill("#6c7078").border("#3a3d44", "#7d828b");
    for (let y = 2; y < 13; y += 2) for (let x = 2; x < 14; x++) side.set(x, y, "#5f636b");
    for (const y of [4, 6, 8]) side.rect(4, y, 11, y, "#2b2d32");
    for (let x = 1; x < 15; x++) for (const y of [13, 14]) side.set(x, y, (x + y) % 4 < 2 ? "#f2c230" : "#26272b");
    side.corners("#2b2d32");
    save("textures/block/spotlight_side", side);
    const top = canvas().fill("#6c7078").border("#3a3d44", "#7d828b");
    top.disc(8, 8, 6.2, "#2b2d32").disc(8, 8, 5.2, "#ffd24a").disc(8, 8, 3.9, "#ffe88a").disc(8, 8, 2.3, "#fff6c8").disc(8, 8, 0.9, "#ffffff");
    top.corners("#2b2d32");
    save("textures/block/spotlight_top", top);
  }

  // ---------- Mikrofon ----------
  {
    const side = canvas().fill("#1d1d22");
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if ((x + 0.5 - 8) ** 2 + (y + 0.5 - 5.2) ** 2 <= 5.4 * 5.4) side.set(x, y, (x + y) % 2 ? "#9aa0aa" : "#6e747e");
    side.ring(8, 5.2, 5.0, 5.5, "#c9ced6");
    side.rect(4, 10, 11, 10, "#d4a62a").rect(4, 11, 11, 11, "#a67c12");                                // Goldring
    side.rect(6, 12, 9, 15, "#2b2b31").rect(6, 12, 6, 15, "#44444c");
    save("textures/block/microphone_side", side);
    const top = canvas().fill("#1d1d22");
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if ((x + 0.5 - 8) ** 2 + (y + 0.5 - 8) ** 2 <= 6.6 * 6.6) top.set(x, y, (x + y) % 2 ? "#9aa0aa" : "#6e747e");
    top.ring(8, 8, 6.2, 6.7, "#c9ced6");
    save("textures/block/microphone_top", top);
  }

  // ---------- Song-Bibliothek ----------
  {
    const side = canvas().fill("#27243a").border("#d4a62a", "#3a3556");                                  // Goldrahmen
    const sleeve = ["#3fd0ee", "#ff4b9a", "#ff9e2c", "#7ee06a", "#b48cff", "#f2e04a"];
    for (const [y0, y1] of [[3, 7], [9, 13]]) {                                                         // zwei Regalboeden mit Plattenhuellen
      side.rect(2, y1 + 1, 13, y1 + 1, "#15131f");
      for (let x = 2, i = (y0 * 7) % 6; x <= 13; x += 2, i++) {
        side.rect(x, y0, x, y1, sleeve[i % 6]).rect(x + 1, y0, x + 1, y1, "#1d1b2c");
      }
    }
    save("textures/block/library_side", side);
    const top = canvas().fill("#27243a").border("#d4a62a", "#3a3556");
    top.disc(8, 8, 5.6, "#101018").ring(8, 8, 4.4, 4.7, "#262633").disc(8, 8, 2.2, "#ff4b9a").set(8, 8, "#0b0b12");
    top.rect(3, 3, 4, 4, "#3fd0ee").rect(11, 11, 12, 12, "#7ee06a");
    save("textures/block/library_top", top);
  }

  // ---------- Musik-Fernbedienung ----------
  {
    const side = canvas().fill("#303845").border("#1a1f27", "#455063");
    side.rect(3, 3, 12, 6, "#10131a");                                                                   // Display
    side.rect(4, 4, 6, 5, "#39e06a").rect(8, 4, 8, 5, "#39e06a").rect(10, 4, 11, 5, "#e0e039");
    [[3, 9], [6, 9], [9, 9], [12, 9]].forEach(([x, y], i) => { side.rect(x, y, x + 1, y + 1, ["#ff4b4b", "#3fd0ee", "#ff9e2c", "#7ee06a"][i]); });  // Tasten
    side.rect(3, 12, 12, 13, "#232a35").rect(4, 12, 5, 13, "#d9d9e3");                                   // Regler
    save("textures/block/remote_side", side);
    const top = canvas().fill("#303845").border("#1a1f27", "#455063");
    top.disc(8, 8, 4.8, "#10131a").ring(8, 8, 3.2, 3.6, "#455063").disc(8, 8, 1.6, "#ff4b4b");           // Signal-Knopf
    top.rect(2, 2, 3, 3, "#39e06a").rect(12, 2, 13, 3, "#3fd0ee");
    save("textures/block/remote_top", top);
  }

  // ---------- Radio-Empfaenger: Holzradio mit Lautsprecher-Gitter, Skala und Drehknoepfen ----------
  {
    const side = canvas().fill("#8a5a2e").border("#4d3118", "#a8743c");
    side.rect(2, 2, 13, 5, "#1a1208").rect(3, 3, 12, 4, "#e8d9a8");                                      // Frequenz-Skala
    for (let x = 3; x <= 12; x += 3) side.set(x, 3, "#4d3118");
    side.rect(9, 2, 9, 5, "#e03939");                                                                     // Zeiger
    for (let y = 7; y <= 13; y += 2) side.rect(2, y, 8, y, "#241608");                                    // Lautsprecher-Gitter
    side.disc(11.5, 9, 2.0, "#3a2512").disc(11.5, 9, 1.4, "#d9c27a").set(11, 8, "#4d3118");               // Knopf 1
    side.disc(11.5, 13, 1.4, "#3a2512").disc(11.5, 13, 0.9, "#d9c27a");                                   // Knopf 2
    save("textures/block/radio_receiver_side", side);
    const top = canvas().fill("#8a5a2e").border("#4d3118", "#a8743c");
    top.disc(8, 8, 3.2, "#3a2512").disc(8, 8, 2.2, "#b0b4bc").disc(8, 8, 1.0, "#e8ebf0");                  // Antennen-Fuss
    top.line(8, 8, 13, 3, "#e8ebf0");
    save("textures/block/radio_receiver_top", top);
  }
  // ---------- Radio-Sender: dunkles Metallgehaeuse mit Funkwellen und "ON AIR"-Lampe ----------
  {
    const side = canvas().fill("#22304a").border("#101826", "#33466a");
    side.rect(3, 2, 12, 4, "#0a0f1a").rect(4, 3, 5, 3, "#e03939").rect(7, 3, 7, 3, "#e03939").rect(9, 3, 11, 3, "#e03939"); // ON AIR
    side.disc(8, 10, 1.4, "#e8ebf0");                                                                       // Sendemast-Fuss
    side.ring(8, 10, 2.6, 3.1, "#3fd0ee").ring(8, 10, 4.2, 4.7, "#3fd0ee").ring(8, 10, 5.8, 6.3, "#3fd0ee");
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (y < 7 && !(x > 1 && x < 14)) side.set(x, y, "#22304a");
    side.rect(2, 2, 13, 2, "#10182a").rect(3, 3, 12, 4, "#0a0f1a").rect(4, 3, 5, 3, "#e03939").rect(7, 3, 7, 3, "#e03939").rect(9, 3, 11, 3, "#e03939");
    save("textures/block/radio_transmitter_side", side);
    const top = canvas().fill("#22304a").border("#101826", "#33466a");
    top.disc(8, 8, 5.4, "#0a0f1a").ring(8, 8, 3.6, 4.0, "#3fd0ee").ring(8, 8, 1.8, 2.2, "#3fd0ee").disc(8, 8, 0.9, "#e8ebf0");
    save("textures/block/radio_transmitter_top", top);
  }

  // ---------- Items ----------
  function record(label) {
    const r = canvas();
    r.disc(8, 8, 7.4, "#0b0b0e").disc(8, 8, 7.0, "#1a1a1f").ring(8, 8, 5.7, 6.0, "#26262d").ring(8, 8, 4.6, 4.9, "#26262d");
    label(r);
    r.set(8, 8, "#0b0b0e");
    r.set(4, 4, "#3d3d48").set(5, 3, "#3d3d48").set(3, 5, "#3d3d48").set(11, 3, "#2e2e38").set(12, 4, "#2e2e38");       // Glanz
    return r;
  }
  save("textures/item/blank_record", record(r => r.disc(8, 8, 2.9, "#cfd0d6").disc(8, 8, 2.0, "#e8e9ee")));
  save("textures/item/written_record", record(r => {
    r.disc(8, 8, 2.9, "#3fd0ee");
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if ((x + 0.5 - 8) ** 2 + (y + 0.5 - 8) ** 2 <= 2.9 * 2.9 && y >= 8) r.set(x, y, "#ff4b9a");
    r.set(8, 6, "#ffffff").set(9, 6, "#ffffff").set(9, 7, "#ffffff").set(7, 9, "#ffffff").set(8, 9, "#ffffff");
  }));
  {
    const t = canvas();
    // Stimmgabel schraeg von unten links (Griff) nach oben rechts (Zinken)
    [[2, 14], [3, 13]].forEach(([x, y]) => t.set(x, y, "#3b2a1a"));
    [[4, 12], [5, 11], [6, 10], [7, 9]].forEach(([x, y]) => { t.set(x, y, "#e8b83a"); t.set(x + 1, y, "#b8860b"); });
    [[8, 8], [9, 9]].forEach(([x, y]) => t.set(x, y, "#e8b83a"));
    [[8, 7], [9, 6], [10, 5], [11, 4], [12, 3]].forEach(([x, y], i) => { t.set(x, y, "#e6e9f0"); t.set(x, y + 1, "#9aa0aa"); if (i === 4) t.set(x, y, "#8cecff"); });
    [[9, 8], [10, 7], [11, 6], [12, 5], [13, 4]].forEach(([x, y], i) => { t.set(x, y, "#e6e9f0"); t.set(x + 1, y, "#9aa0aa"); if (i === 4) t.set(x, y, "#8cecff"); });
    t.set(14, 3, "#3fd0ee").set(13, 2, "#3fd0ee");                                                      // Schwingung
    save("textures/item/peak_tuner", t);
  }
};
