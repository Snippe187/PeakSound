// Seite "PeakSound": Songs ansehen, anhoeren, umbenennen, freigeben, loeschen und Upload-Limits einstellen.
export default async function mount(root, ctx) {
  const { h } = ctx;
  const L = (de, en) => ctx.t({ de, en });
  let meta, songs = [], q = '', sort = 'new', only = 'all', playing = null, editing = null;
  const rel = async () => { meta = await ctx.api('meta'); songs = await ctx.api('songs'); };
  async function call(path, method, body, ok) {
    try { meta = await ctx.api(path, { method, body }); songs = await ctx.api('songs'); if (ok) ctx.toast(ok, 'good'); return true; }
    catch (e) { if (e.message) ctx.toast(e.message, 'bad'); return false; }
  }
  const dur = ms => { const s = Math.round(ms / 1000); return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0'); };
  const size = b => b >= 1048576 ? (b / 1048576).toFixed(1) + ' MB' : Math.max(1, Math.round(b / 1024)) + ' KB';
  const set = (...kids) => root.replaceChildren(...kids.filter(k => k != null && k !== false));

  if (!document.getElementById('ps-style')) {
    const st = document.createElement('style'); st.id = 'ps-style';
    st.textContent = `
.ps-stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:14px;margin:14px 0}
.ps-stat{background:var(--panel);border:2px solid var(--line);padding:14px}
.ps-stat b{display:block;font-size:30px;color:var(--accent)}.ps-stat span{color:var(--dim);font-size:13px}
.ps-bar{height:10px;background:var(--panel2);border:2px solid var(--line);margin-top:8px}.ps-bar i{display:block;height:100%;background:var(--accent);transition:width .6s}
.ps-song{display:grid;grid-template-columns:56px 1fr auto;gap:14px;align-items:center;background:var(--panel);border:2px solid var(--line);padding:10px 14px;margin-bottom:8px;transition:border-color .15s,transform .15s}
.ps-song:hover{border-color:var(--accent);transform:translateX(3px)}
.ps-cov{width:56px;height:56px;image-rendering:auto;background:var(--panel2);border:2px solid var(--line);display:grid;place-items:center;font-size:24px;overflow:hidden}
.ps-cov img{width:100%;height:100%;object-fit:cover}
.ps-song.playing .ps-cov{animation:psp 1.2s ease-in-out infinite alternate}
@keyframes psp{from{box-shadow:0 0 0 0 var(--accent)}to{box-shadow:0 0 14px 3px var(--accent)}}
.ps-t{font-weight:bold}.ps-m{color:var(--dim);font-size:12px;margin-top:2px}
.ps-act{display:flex;gap:6px;flex-wrap:wrap;justify-content:flex-end}
.ps-audio{grid-column:1/-1;width:100%}
@media(max-width:700px){.ps-song{grid-template-columns:56px 1fr}.ps-act{grid-column:1/-1;justify-content:flex-start}}`;
    document.head.appendChild(st);
  }

  function statsView() {
    const pct = meta.maxBytes > 0 ? Math.min(100, Math.round(meta.totalBytes / meta.maxBytes * 100)) : 0;
    return h('div', { class: 'ps-stats' },
      h('div', { class: 'ps-stat' }, h('b', {}, meta.count), h('span', {}, L('Songs', 'Songs'))),
      h('div', { class: 'ps-stat' }, h('b', { style: meta.pending ? 'color:var(--warn)' : '' }, meta.pending), h('span', {}, L('warten auf Freigabe', 'waiting for approval'))),
      h('div', { class: 'ps-stat' }, h('b', {}, size(meta.totalBytes)), h('span', {}, meta.maxBytes > 0 ? L('von ', 'of ') + size(meta.maxBytes) + ' (' + pct + ' %)' : L('Speicher, unbegrenzt', 'storage, unlimited')),
        meta.maxBytes > 0 ? h('div', { class: 'ps-bar' }, h('i', { style: 'width:' + pct + '%' })) : null));
  }

  function songRow(s) {
    const isEdit = editing === s.hash, isPlay = playing === s.hash;
    const title = h('input', { value: s.title, maxlength: 64, style: 'max-width:340px' });
    return h('div', { class: 'ps-song' + (isPlay ? ' playing' : '') },
      h('div', { class: 'ps-cov' }, s.hasCover ? h('img', { src: '/api/peaksound/cover/' + s.hash, loading: 'lazy', onerror: ev => ev.target.replaceWith(document.createTextNode('♫')) }) : '♫'),
      h('div', {},
        isEdit ? h('div', { class: 'row' }, title, h('button', { class: 'px-btn small primary', onclick: async () => { if (await call('songs/' + s.hash + '/rename', 'POST', { title: title.value }, L('Umbenannt', 'Renamed'))) { editing = null; render(); } } }, 'OK'), h('button', { class: 'px-btn small ghost', onclick: () => { editing = null; render(); } }, '×'))
          : h('div', { class: 'ps-t' }, s.title, s.pending ? h('span', { class: 'tag warn', style: 'margin-left:8px' }, L('wartet', 'pending')) : null),
        h('div', { class: 'ps-m' }, [s.artist, s.uploader ? L('von ', 'by ') + s.uploader : '', dur(s.durationMs), size(s.sizeBytes) + ' ' + s.format.toUpperCase(), s.channels > 2 ? s.channels + ' ' + L('Kanäle', 'channels') : '', s.plays + ' ' + L('Wiedergaben', 'plays')].filter(Boolean).join(' · '))),
      h('div', { class: 'ps-act' },
        h('button', { class: 'px-btn small' + (isPlay ? ' active' : ''), onclick: () => { playing = isPlay ? null : s.hash; render(); } }, isPlay ? '■' : '▶ ' + L('Anhören', 'Listen')),
        s.pending ? h('button', { class: 'px-btn small primary', onclick: async () => { if (await call('songs/' + s.hash + '/approve', 'POST', '', L('Freigegeben', 'Approved'))) render(); } }, L('Freigeben', 'Approve')) : null,
        h('button', { class: 'px-btn small', onclick: () => { editing = s.hash; render(); } }, L('Umbenennen', 'Rename')),
        h('button', { class: 'px-btn small danger', onclick: async () => { if (await ctx.confirm(L('Song "' + s.title + '" löschen? Platten damit bleiben stumm.', 'Delete song "' + s.title + '"? Records with it will stay silent.'))) { if (playing === s.hash) playing = null; if (await call('songs/' + s.hash, 'DELETE', undefined, L('Gelöscht', 'Deleted'))) render(); } } }, s.pending ? L('Ablehnen', 'Reject') : L('Löschen', 'Delete'))),
      isPlay ? h('audio', { class: 'ps-audio', controls: true, autoplay: true, src: '/api/peaksound/audio/' + s.hash }) : null);
  }

  function limitsView() {
    const def = h('input', { type: 'number', min: 0, value: meta.defaultMax, style: 'width:120px' });
    const mb = h('input', { type: 'number', min: 0, value: Math.round(meta.maxBytes / 1048576), style: 'width:140px' });
    return h('div', { class: 'card' },
      h('h2', { style: 'margin-top:0' }, L('Limits', 'Limits')), h('p', { class: 'sub' }, L('0 heißt unbegrenzt.', '0 means unlimited.')),
      h('div', { class: 'row' }, h('span', { class: 'sub' }, L('Songs pro Spieler', 'Songs per player')), def,
        h('button', { class: 'px-btn small primary', onclick: async () => { if (await call('limits', 'POST', { defaultMax: +def.value }, L('Gespeichert', 'Saved'))) render(); } }, L('Speichern', 'Save')),
        h('span', { class: 'sub', style: 'margin-left:18px' }, L('Speicher (MB)', 'Storage (MB)')), mb,
        h('button', { class: 'px-btn small primary', onclick: async () => { if (await call('limits', 'POST', { storageMb: +mb.value }, L('Gespeichert', 'Saved'))) render(); } }, L('Speichern', 'Save'))),
      meta.players.length ? h('table', { style: 'margin-top:14px' }, h('tr', {}, h('th', {}, L('Spieler', 'Player')), h('th', {}, L('Songs', 'Songs')), h('th', {}, L('Eigenes Limit (leer = Standard)', 'Own limit (empty = default)')), h('th', {})),
        meta.players.map(p => { const v = h('input', { type: 'number', min: 0, style: 'width:120px', placeholder: String(meta.defaultMax), value: p.override === meta.useDefault ? '' : p.override });
          return h('tr', {}, h('td', {}, p.name), h('td', {}, p.count), h('td', {}, v), h('td', {}, h('button', { class: 'px-btn small', onclick: async () => { if (await call('players/' + p.uuid + '/limit', 'POST', { value: v.value === '' ? meta.useDefault : +v.value }, L('Gespeichert', 'Saved'))) render(); } }, L('Speichern', 'Save')))); })) : null);
  }

  function listView() {
    const needle = q.trim().toLowerCase();
    let rows = songs.filter(s => (only === 'all' || s.pending) && (!needle || (s.title + ' ' + s.artist + ' ' + s.uploader).toLowerCase().includes(needle)));
    rows = [...rows].sort(sort === 'title' ? (a, b) => a.title.localeCompare(b.title) : sort === 'plays' ? (a, b) => b.plays - a.plays : (a, b) => b.uploadedAt - a.uploadedAt);
    return rows.length ? rows.map(songRow) : h('div', { class: 'empty' }, songs.length ? L('Nichts gefunden.', 'Nothing found.') : L('Noch keine Songs. Spieler laden sie im Spiel am Plattenspieler hoch.', 'No songs yet. Players upload them in the game at the record player.'));
  }

  function render() {
    const list = h('div', { id: 'ps-list', style: 'margin-top:12px' }, listView());
    const search = h('input', { placeholder: L('Suchen: Titel, Künstler, Spieler', 'Search: title, artist, player'), value: q, style: 'max-width:340px', oninput: ev => { q = ev.target.value; list.replaceChildren(...[].concat(listView())); } });
    const sel = h('select', { style: 'width:auto', onchange: ev => { sort = ev.target.value; render(); } },
      [['new', L('Neueste', 'Newest')], ['title', L('Titel A–Z', 'Title A–Z')], ['plays', L('Beliebteste', 'Most played')]].map(([v, l]) => h('option', { value: v, selected: sort === v }, l)));
    set(h('h1', {}, 'PeakSound'), h('p', { class: 'sub' }, L('Alle Songs des Servers verwalten. Hochgeladen wird weiterhin im Spiel am Plattenspieler.', 'Manage every song on the server. Uploading still happens in the game at the record player.')),
      statsView(),
      h('div', { class: 'row' }, search, sel,
        h('button', { class: 'px-btn small ' + (only === 'all' ? 'active' : ''), onclick: () => { only = 'all'; render(); } }, L('Alle', 'All')),
        h('button', { class: 'px-btn small ' + (only === 'pending' ? 'active' : ''), onclick: () => { only = 'pending'; render(); } }, L('Wartend', 'Pending') + ' (' + meta.pending + ')')),
      list, h('h2', {}, L('Limits und Speicher', 'Limits and storage')), limitsView());
  }

  ctx.on(async d => { if (d.module === 'peaksound' && !editing) { await rel(); render(); } });
  await rel();
  render();
}

