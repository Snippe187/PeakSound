// Erzeugt dist/PeakSound-Rezepte.html und .pdf (Edge headless). Aufruf: node tools/build_recipes_pdf.js
const fs = require('fs'), path = require('path'), cp = require('child_process');
const root = path.join(__dirname, '..');
const tex = path.join(root, 'fabric-mod/src/client/resources/assets/peaksound/textures');
const b64 = f => 'data:image/png;base64,' + fs.readFileSync(path.join(tex, f)).toString('base64');

// Zutaten: [Name, Farbe oben, Farbe unten, Textfarbe]
const ING = {
  obsidian: ['Obsidian', '#3b2a5c', '#1a1030', '#fff'],
  dsword: ['Diamant&shy;schwert', '#8ff3f0', '#2fb5b8', '#032'],
  diamond: ['Diamant', '#9ff7f2', '#27b9c4', '#032'],
  redstone: ['Redstone-Staub', '#e5493a', '#8d1b12', '#fff'],
  jukebox: ['Jukebox', '#9a6a3a', '#5c3a1c', '#fff'],
  peakjuke: ['PeakSound-Jukebox', '#b78a52', '#6b4520', '#fff'],
  note: ['Notenblock', '#b07a45', '#6e4524', '#fff'],
  iron: ['Eisenbarren', '#f0f0f0', '#a9a9a9', '#222'],
  gold: ['Goldbarren', '#ffe46b', '#d09a14', '#432'],
  glass: ['Glas', '#e9f8ff', '#a5d4e6', '#134'],
  pane: ['Glasscheibe', '#eefaff', '#b8dcea', '#134'],
  lamp: ['Redstone-Lampe', '#f0c878', '#a8742a', '#321'],
  glow: ['Glowstone-Staub', '#ffe987', '#d9a82e', '#432'],
  stick: ['Stock', '#c19a62', '#8a6636', '#fff'],
  comparator: ['Komparator', '#d9d4cc', '#8d867c', '#222'],
  lever: ['Hebel', '#a8a8a8', '#6a6a6a', '#fff'],
  rod: ['Blitz&shy;ableiter', '#e7a572', '#a8582c', '#fff'],
  amethyst: ['Amethyst-Splitter', '#d7a8ff', '#8a4fd1', '#fff'],
  frame: ['Item-Rahmen', '#d4894a', '#8f5524', '#fff'],
  hoe: ['Holzhacke', '#c9a066', '#8a6a3a', '#fff'],
};

// Rezepte: Form (9 Zeichen oder Liste), Legende, Ergebnis, Hinweis
const R = [
  { t: 'Leere Platte', tag: 'ungeformt', icon: ['item', 'item/blank_record.png'], res: '9× Leere Platte',
    cells: [null, 'obsidian', null, 'dsword', null, null, null, null, null], shapeless: ['obsidian', 'dsword'],
    note: '1 Obsidianblock + 1 Diamantschwert, Anordnung egal. Das Schwert kommt mit halber Haltbarkeit zurück. Auch per Shift-Klick.' },
  { t: 'PeakSound-Jukebox', icon: ['cube', 'jukebox'], res: '1× PeakSound-Jukebox',
    grid: [' D ', 'RJR', '   '], key: { D: 'diamond', R: 'redstone', J: 'jukebox' },
    note: 'Mitte: normale Jukebox. Für Spieler ohne Mod sieht das Ergebnis wie ein Notenblock aus.' },
  { t: 'Lautsprecher', icon: ['cube', 'speaker'], res: '1× Lautsprecher',
    grid: [' I ', 'RNR', ' I '], key: { I: 'iron', R: 'redstone', N: 'note' }, note: 'Mitte: normaler Notenblock.' },
  { t: 'DJ-Pult', icon: ['cube', 'dj_desk'], res: '1× DJ-Pult',
    grid: [' R ', 'JDJ', ' I '], key: { R: 'redstone', J: 'peakjuke', D: 'diamond', I: 'iron' },
    note: 'Links und rechts je eine <b>PeakSound-Jukebox</b> – ein normaler Notenblock reicht nicht.' },
  { t: 'Scheinwerfer', icon: ['cube', 'spotlight'], res: '2× Scheinwerfer',
    grid: [' G ', 'ILI', 'NIN'], key: { G: 'glass', I: 'iron', L: 'lamp', N: 'glow' }, note: 'Ergibt zwei Scheinwerfer auf einmal.' },
  { t: 'Mikrofon', icon: ['cube', 'microphone'], res: '1× Mikrofon',
    grid: [' I ', 'RSR', ' I '], key: { I: 'iron', R: 'redstone', S: 'stick' }, note: 'Spieler sprechen live ins Mikrofon.' },
  { t: 'Song-Bibliothek', icon: ['cube', 'library'], res: '1× Song-Bibliothek',
    grid: [' D ', 'OJO', 'III'], key: { D: 'diamond', O: 'obsidian', J: 'jukebox', I: 'iron' }, note: 'Durchsuchbare Liste aller Server-Songs mit Cover, Favoriten und Sortierung.' },
  { t: 'Musik-Fernbedienung', icon: ['cube', 'remote'], res: '1× Fernbedienung',
    grid: [' R ', 'ICI', ' L '], key: { R: 'redstone', I: 'iron', C: 'comparator', L: 'lever' }, note: 'Fernbedienung für die Musikanlage.' },
  { t: 'Radio-Empfänger', icon: ['cube', 'radio_receiver'], res: '1× Radio-Empfänger',
    grid: [' L ', 'INI', 'IRI'], key: { L: 'rod', I: 'iron', N: 'note', R: 'redstone' }, note: 'Stellbare Frequenz – zwischen Sendern hörst du Rauschen.' },
  { t: 'Radio-Sender', icon: ['cube', 'radio_transmitter'], res: '1× Radio-Sender',
    grid: [' L ', 'GNG', 'ARA'], key: { L: 'rod', G: 'gold', N: 'note', A: 'amethyst', R: 'redstone' }, note: 'Sendet die Playlist der Jukebox auf einer Frequenz mit einstellbarer Reichweite.' },
  { t: 'Display', icon: ['cube', 'display'], res: '1× Display',
    grid: ['PPP', 'IFI', ' R '], key: { P: 'pane', I: 'iron', F: 'frame', R: 'redstone' }, note: 'Zeigt das Cover des laufenden Songs; mehrere Displays bilden eine Cover-Wand.' },
  { t: 'Taschenradio', icon: ['item', 'item/pocket_radio.png'], res: '1× Taschenradio',
    grid: [' L ', 'INI', ' R '], key: { L: 'rod', I: 'iron', N: 'note', R: 'redstone' }, note: 'Radio für unterwegs: empfängt überall auf dem Server.' },
  { t: 'Peak-Tuner', tag: 'ungeformt', icon: ['item', 'item/peak_tuner.png'], res: '1× Peak-Tuner',
    shapeless: ['hoe', 'redstone'], note: 'Werkzeug zum Einstellen von PeakSound-Blöcken (Holzhacke + Redstone-Staub).' },
];

function cube(name, size = 84) {
  const top = b64(`block/${name}_top.png`), side = b64(`block/${name}_side.png`);
  const u = size / 28, T = 14 * u;           // Würfelbreite = 27.7u
  const w = 2 * 13.856 * u, h = 32 * u;
  return `<svg width="${w.toFixed(1)}" height="${h.toFixed(1)}" viewBox="0 0 ${w.toFixed(1)} ${h.toFixed(1)}" class="cube">
  <defs><filter id="px"><feComponentTransfer/></filter></defs>
  <image href="${side}" width="16" height="16" style="image-rendering:pixelated" transform="matrix(${(.866 * u).toFixed(3)},${(.5 * u).toFixed(3)},0,${u.toFixed(3)},0,${(8 * u).toFixed(2)})"/>
  <image href="${side}" width="16" height="16" style="image-rendering:pixelated" transform="matrix(${(.866 * u).toFixed(3)},${(-.5 * u).toFixed(3)},0,${u.toFixed(3)},${(13.856 * u).toFixed(2)},${(16 * u).toFixed(2)})"/>
  <polygon points="${13.856 * u},${16 * u} ${2 * 13.856 * u},${8 * u} ${2 * 13.856 * u},${24 * u} ${13.856 * u},${32 * u}" fill="#000" opacity=".28"/>
  <image href="${top}" width="16" height="16" style="image-rendering:pixelated" transform="matrix(${(.866 * u).toFixed(3)},${(.5 * u).toFixed(3)},${(-.866 * u).toFixed(3)},${(.5 * u).toFixed(3)},${(13.856 * u).toFixed(2)},0)"/>
</svg>`;
}
const icon = ic => ic[0] === 'cube' ? cube(ic[1]) : `<img class="item" src="${b64(ic[1])}">`;
const slot = k => {
  if (!k) return '<div class="slot empty"></div>';
  const [n, a, b, c] = ING[k];
  return `<div class="slot" style="background:linear-gradient(145deg,${a},${b});color:${c}"><span>${n}</span></div>`;
};
function grid(r) {
  let cells;
  if (r.shapeless) cells = [...r.shapeless.map(slot)];
  else cells = r.grid.join('').split('').map(ch => ch === ' ' ? slot(null) : slot(r.key[ch]));
  const cols = r.shapeless ? r.shapeless.length : 3;
  return `<div class="grid" style="grid-template-columns:repeat(${cols},62px)">${cells.join('')}</div>`;
}
function legend(r) {
  const keys = r.shapeless || [...new Set(Object.values(r.key))];
  return '';
}
const cards = R.map(r => `<section class="card">
  <header><div class="ico">${icon(r.icon)}</div><div><h2>${r.t}</h2>${r.shapeless ? '<em>ungeformtes Rezept</em>' : '<em>Werkbank 3×3</em>'}</div></header>
  <div class="row">${grid(r)}<div class="arrow">➜</div><div class="res"><div class="resico">${icon(r.icon)}</div><b>${r.res}</b></div></div>
  <p>${r.note}</p></section>`);

const pages = [];
const tunerCard = cards.pop();
for (let i = 0; i < cards.length; i += 6) pages.push(`<div class="page"><div class="pghead"><span>PeakSound</span><span>Crafting-Rezepte</span></div><div class="cards">${cards.slice(i, i + 6).join('')}</div></div>`);

const showcase = ['jukebox', 'speaker', 'dj_desk', 'spotlight', 'library', 'radio_receiver', 'radio_transmitter', 'display'].map(n => cube(n, 72)).join('');

const html = `<!doctype html><html lang="de"><head><meta charset="utf-8"><title>PeakSound – Crafting-Rezepte</title><style>
@page{size:A4;margin:0}
*{box-sizing:border-box}
body{margin:0;font-family:'Segoe UI',Arial,sans-serif;color:#1d1830;-webkit-print-color-adjust:exact;print-color-adjust:exact}
.page{width:210mm;height:297mm;padding:16mm 14mm 12mm;page-break-after:always;position:relative;background:#f6f3fb;overflow:hidden}
.cover{background:radial-gradient(circle at 20% 15%,#6a3df0 0,#2a1668 45%,#120b30 100%);color:#fff;display:flex;flex-direction:column;justify-content:center;align-items:center;text-align:center}
.cover h1{font-size:64px;margin:6px 0 0;letter-spacing:2px;font-weight:800}
.cover h1 b{color:#ffcf4d}
.cover .sub{font-size:20px;letter-spacing:6px;text-transform:uppercase;color:#cdbfff;margin-top:6px}
.cover .rule{width:120px;height:4px;background:linear-gradient(90deg,#ffcf4d,#ff6aa8);border-radius:4px;margin:26px auto}
.cover .cubes{display:flex;flex-wrap:wrap;justify-content:center;gap:8px 22px;max-width:165mm;margin-top:18px}
.cover .tip{margin-top:38px;max-width:130mm;font-size:14px;line-height:1.55;color:#e4dcff;background:#ffffff14;border:1px solid #ffffff30;border-radius:14px;padding:14px 20px}
.cover .ver{position:absolute;bottom:14mm;left:0;right:0;font-size:12px;color:#9d8fe0;letter-spacing:2px}
.pghead{display:flex;justify-content:space-between;font-size:12px;letter-spacing:3px;text-transform:uppercase;color:#6a4fd0;font-weight:700;border-bottom:3px solid #6a4fd0;padding-bottom:6px;margin-bottom:12px}
.cards{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.card{background:#fff;border-radius:14px;padding:10px 12px 10px;box-shadow:0 2px 8px #3a2a7a22;border:1px solid #e1daf5;height:79mm;display:flex;flex-direction:column}
.card header{display:flex;align-items:center;gap:10px;margin-bottom:8px}
.card .ico{width:50px;height:46px;display:flex;align-items:center;justify-content:center;background:linear-gradient(145deg,#efe9ff,#dccfff);border-radius:10px}
.card .ico svg{width:40px;height:auto}.card .ico .item{width:34px;height:34px;image-rendering:pixelated}
h2{margin:0;font-size:16px;color:#2a1668;line-height:1.1}
em{font-size:10.5px;color:#7c6bb8;font-style:normal;letter-spacing:.5px;text-transform:uppercase}
.row{display:flex;align-items:center;justify-content:space-between;gap:6px;flex:1}
.grid{display:grid;gap:3px;background:#c6c6c6;padding:4px;border:2px solid #8b8b8b;border-radius:4px;box-shadow:inset 2px 2px #fff6,inset -2px -2px #0003}
.slot{width:62px;height:40px;border-radius:3px;display:flex;align-items:center;justify-content:center;text-align:center;font-size:8.6px;font-weight:700;line-height:1.05;padding:2px;border:1px solid #0003;box-shadow:inset 1px 1px #fff7,inset -1px -1px #0004}
.slot.empty{background:#8b8b8b;box-shadow:inset 2px 2px #0005,inset -1px -1px #fff4;border:1px solid #6e6e6e}
.arrow{font-size:22px;color:#6a4fd0}
.res{width:64px;display:flex;flex-direction:column;align-items:center;gap:4px;text-align:center;font-size:9.6px;color:#2a1668;background:linear-gradient(160deg,#fff6d4,#ffe08a);border:2px solid #d9a82e;border-radius:10px;padding:6px 3px}
.res .resico{height:42px;display:flex;align-items:center}.res svg{width:40px;height:auto}.res .item{width:36px;height:36px;image-rendering:pixelated}
.card p{margin:6px 0 0;font-size:10px;line-height:1.35;color:#4a4468}
.info{background:#fff;border-radius:14px;padding:16px 20px;border:1px solid #e1daf5;margin-bottom:12px}
.info h3{margin:0 0 8px;color:#2a1668;font-size:17px}.info li,.info p{font-size:12px;line-height:1.55;color:#3b3558;margin:3px 0}
.info ul{margin:4px 0;padding-left:20px}
code{background:#efe9ff;border-radius:4px;padding:1px 5px;font-size:11px;color:#4a2db8}
</style></head><body>
<div class="page cover">
  <div class="sub">Minecraft 26.2 · Fabric + Paper</div>
  <h1>Peak<b>Sound</b></h1>
  <div class="rule"></div>
  <div style="font-size:26px;font-weight:600">Crafting-Rezepte</div>
  <div class="cubes">${showcase}</div>
  <div class="tip">Alle Rezepte werden beim Beitritt im Rezeptbuch freigeschaltet. Spieler ohne Mod sehen die Blöcke als Notenblöcke und können trotzdem mitspielen und craften.</div>
  <div class="ver">PEAKSOUND 0.1.0</div>
</div>
${pages.join('\n')}
<div class="page"><div class="pghead"><span>PeakSound</span><span>Hinweise</span></div>
  <div class="cards" style="margin-bottom:12px">${tunerCard}</div>
  <div class="info"><h3>So liest du die Rezepte</h3>
    <ul><li>Das Gitter zeigt die 3×3-Felder der Werkbank, graue Felder bleiben <b>leer</b>.</li>
    <li>Der gelbe Kasten rechts zeigt das Ergebnis samt Stückzahl.</li>
    <li>„Ungeformt“ bedeutet: Die Zutaten dürfen beliebig auf der Werkbank liegen.</li></ul></div>
  <div class="info"><h3>Wichtige Hinweise</h3>
    <ul><li>Die <b>PeakSound-Jukebox</b> braucht eine normale Jukebox, das <b>DJ-Pult</b> braucht zwei fertige PeakSound-Jukeboxen.</li>
    <li>Radio-Sender und -Empfänger funken auf einstellbaren Frequenzen; das Taschenradio empfängt weltweit.</li>
    <li>Displays lassen sich zu einer Cover-Wand zusammenbauen.</li>
    <li>Admins können Rechte (Platzieren, Hochladen, Radio …) über Berechtigungen steuern – standardmäßig ist alles erlaubt.</li></ul></div>
  <div class="info"><h3>Nützliche Befehle</h3>
    <ul><li><code>/peaksound stats</code> – Statistik</li><li><code>/peaksound backup</code> – Backup erstellen</li><li><code>/peaksound backups</code> – vorhandene Backups anzeigen</li><li><code>/peaksound debug</code> – Diagnosetext für Fehlersuche</li></ul></div>
</div>
</body></html>`;

const out = path.join(root, 'dist');
const htmlFile = path.join(out, 'PeakSound-Rezepte.html'), pdfFile = path.join(out, 'PeakSound-Rezepte.pdf');
fs.writeFileSync(htmlFile, html, 'utf8');
const edge = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
cp.execFileSync(edge, ['--headless=new', '--disable-gpu', '--no-pdf-header-footer', `--print-to-pdf=${pdfFile}`, 'file:///' + htmlFile.replace(/\\/g, '/')], { stdio: 'inherit' });
console.log('ok', fs.statSync(pdfFile).size);
