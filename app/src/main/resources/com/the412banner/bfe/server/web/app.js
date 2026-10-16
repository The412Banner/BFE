/* BFE "Access from network" web page: sorting, filtering, selection, uploads (files, folders,
   drag & drop), new folder / rename / delete, zip downloads and a media viewer, layered on the
   server-rendered listing. Plain ES2017, no dependencies. */
'use strict';
(function () {
  const body = document.body;
  const listEl = document.getElementById('list');
  if (!listEl) return;

  const RW = body.dataset.rw === '1';
  const TOP = body.dataset.top === '1';
  const here = location.pathname.endsWith('/') ? location.pathname : location.pathname + '/';
  const $ = (id) => document.getElementById(id);

  // ── Small helpers ──
  const store = {
    get(k, d) { try { const v = localStorage.getItem('bfe.' + k); return v === null ? d : JSON.parse(v); } catch (e) { return d; } },
    set(k, v) { try { localStorage.setItem('bfe.' + k, JSON.stringify(v)); } catch (e) { /* private mode */ } },
  };
  function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined) e.textContent = text;
    return e;
  }
  function icon(name) {
    const ns = 'http://www.w3.org/2000/svg';
    const s = document.createElementNS(ns, 'svg');
    const u = document.createElementNS(ns, 'use');
    u.setAttribute('href', '#i-' + name);
    s.appendChild(u);
    return s;
  }
  function button(label, ic, onClick, opts) {
    opts = opts || {};
    const b = el('button', 'btn' + (opts.cls ? ' ' + opts.cls : ''));
    b.type = 'button';
    if (ic) b.append(icon(ic));
    if (label) b.append(el('span', '', label));
    if (opts.title) { b.title = opts.title; b.setAttribute('aria-label', opts.title); }
    b.disabled = !!opts.disabled;
    b.addEventListener('click', onClick);
    return b;
  }
  function fmtSize(b) {
    if (b < 1024) return b + ' B';
    const u = ['KB', 'MB', 'GB', 'TB'];
    let v = b / 1024, i = 0;
    while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
    return (v >= 100 ? v.toFixed(0) : v.toFixed(1)) + ' ' + u[i];
  }
  const toastEl = $('toast');
  let toastTimer = 0;
  function toast(msg, err) {
    toastEl.textContent = msg;
    toastEl.classList.toggle('err', !!err);
    toastEl.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { toastEl.hidden = true; }, err ? 6000 : 2800);
  }
  async function call(method, url, bodyData) {
    const r = await fetch(url, { method, body: bodyData, headers: { 'X-BFE-Request': '1' }, credentials: 'same-origin' });
    if (!r.ok) {
      let t = '';
      try { t = (await r.text()).trim(); } catch (e) { /* ignore */ }
      throw new Error(t || (r.status + ' ' + r.statusText));
    }
    return r;
  }

  // ── Rows ──
  const rows = () => Array.from(listEl.children);
  const isDir = (li) => li.dataset.d === '1';
  const urlOf = (li) => here + encodeURIComponent(li.dataset.n) + (isDir(li) ? '/' : '');
  let showHidden = store.get('hidden', false);
  const visible = (li) => !li.classList.contains('out') && (showHidden || !li.classList.contains('hid'));

  const dateFmt = (() => {
    try { return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }); } catch (e) { return null; }
  })();
  function localizeDates() {
    if (!dateFmt) return;
    for (const li of rows()) {
      const t = +li.dataset.t;
      const tm = li.querySelector('.tm');
      if (t > 0 && tm) tm.textContent = dateFmt.format(new Date(t));
    }
  }

  // ── Sorting ──
  let sort = store.get('sort', { k: 'n', d: false });
  const collator = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' });
  function applySort() {
    const dir = sort.d ? -1 : 1;
    const items = rows();
    items.sort((a, b) => {
      const da = isDir(a), db = isDir(b);
      if (da !== db) return da ? -1 : 1;
      let c = 0;
      if (sort.k === 's') c = (+a.dataset.s) - (+b.dataset.s);
      else if (sort.k === 't') c = (+a.dataset.t) - (+b.dataset.t);
      if (c === 0) c = collator.compare(a.dataset.n, b.dataset.n);
      return c * dir;
    });
    const frag = document.createDocumentFragment();
    items.forEach((li) => frag.appendChild(li));
    listEl.appendChild(frag);
    document.querySelectorAll('.head .sort').forEach((b) => {
      const on = b.dataset.k === sort.k;
      b.classList.toggle('on', on);
      b.dataset.dir = on ? (sort.d ? 'desc' : 'asc') : '';
    });
  }
  document.querySelectorAll('.head .sort').forEach((b) => b.addEventListener('click', () => {
    if (sort.k === b.dataset.k) sort.d = !sort.d;
    else { sort.k = b.dataset.k; sort.d = b.dataset.k !== 'n'; }
    store.set('sort', sort);
    applySort();
  }));

  // ── Filter + hidden files ──
  const q = $('q');
  const emptyEl = document.querySelector('.empty');
  function updateEmpty() {
    const any = rows().some(visible);
    emptyEl.hidden = any;
    if (!any) {
      emptyEl.textContent = q.value.trim() ? 'Nothing here matches “' + q.value.trim() + '”.'
        : (rows().length ? 'Only hidden files here. Use the eye button to show them.'
          : (TOP ? 'Nothing is shared yet.' : 'This folder is empty.'));
    }
  }
  function applyFilter() {
    const t = q.value.trim().toLowerCase();
    for (const li of rows()) li.classList.toggle('out', !!t && !li.dataset.n.toLowerCase().includes(t));
    updateEmpty();
    updateSelbar();
  }
  q.addEventListener('input', applyFilter);
  const hiddenBtn = $('b-hidden');
  function applyHidden() {
    body.classList.toggle('show-hidden', showHidden);
    hiddenBtn.classList.toggle('on', showHidden);
    hiddenBtn.setAttribute('aria-pressed', String(showHidden));
    hiddenBtn.title = showHidden ? 'Hide hidden files' : 'Show hidden files';
    updateEmpty();
  }
  hiddenBtn.addEventListener('click', () => { showHidden = !showHidden; store.set('hidden', showHidden); applyHidden(); });

  // ── Selection ──
  const selectBtn = $('b-select');
  const selbar = $('selbar');
  const selecting = () => body.classList.contains('selecting');
  const selected = () => rows().filter((li) => visible(li) && li.querySelector('.ck').checked);
  function setChecked(li, on) {
    li.querySelector('.ck').checked = on;
    li.classList.toggle('sel', on);
  }
  function setSelecting(on) {
    body.classList.toggle('selecting', on);
    selectBtn.classList.toggle('on', on);
    selectBtn.setAttribute('aria-pressed', String(on));
    if (!on) rows().forEach((li) => setChecked(li, false));
    updateSelbar();
  }
  function updateSelbar() {
    if (!selecting()) { selbar.hidden = true; return; }
    const sel = selected();
    const all = rows().filter(visible);
    selbar.innerHTML = '';
    selbar.append(el('span', 'count', sel.length ? sel.length + ' selected' : 'Select items'));
    const allOn = all.length > 0 && sel.length === all.length;
    selbar.append(button(allOn ? 'None' : 'All', 'check', () => { all.forEach((li) => setChecked(li, !allOn)); updateSelbar(); }));
    selbar.append(button('Download .zip', 'zip', () => zipSelected(sel), { disabled: !sel.length }));
    if (RW && !TOP) selbar.append(button('Delete', 'delete', () => deleteItems(sel), { disabled: !sel.length, cls: 'danger' }));
    selbar.append(button('', 'close', () => setSelecting(false), { title: 'Done' }));
    selbar.hidden = false;
  }
  selectBtn.addEventListener('click', () => setSelecting(!selecting()));
  listEl.addEventListener('change', (e) => {
    if (e.target.classList.contains('ck')) { e.target.closest('li').classList.toggle('sel', e.target.checked); updateSelbar(); }
  });

  // ── Row clicks ──
  const viewable = (li) => ['image', 'video', 'audio'].includes(li.dataset.k);
  listEl.addEventListener('click', (e) => {
    const li = e.target.closest('li.it');
    if (!li) return;
    const more = e.target.closest('.mn');
    if (more) { e.preventDefault(); openMenu(li, more); return; }
    if (e.target.classList.contains('ck')) return;
    if (selecting()) { e.preventDefault(); setChecked(li, !li.querySelector('.ck').checked); updateSelbar(); return; }
    if (e.ctrlKey || e.metaKey || e.shiftKey || e.altKey) return;
    if (viewable(li)) { e.preventDefault(); openViewer(li); return; }
    if (!e.target.closest('a')) location.href = urlOf(li);
  });

  // ── Item menu ──
  const menu = $('menu');
  function closeMenu() { menu.hidden = true; menu.innerHTML = ''; }
  function openMenu(li, anchor) {
    closeMenu();
    const items = [];
    const canChange = RW && !TOP && li.dataset.ro !== '1';
    if (isDir(li)) {
      items.push(['Open', 'folder', () => { location.href = urlOf(li); }]);
      items.push(['Download as .zip', 'zip', () => { location.href = urlOf(li) + '?op=zip'; }]);
    } else {
      if (viewable(li)) items.push(['View', 'eye', () => openViewer(li)]);
      items.push(['Open in new tab', 'open', () => window.open(urlOf(li), '_blank', 'noopener')]);
      items.push(['Download', 'download', () => { location.href = urlOf(li) + '?dl=1'; }]);
    }
    if (canChange) {
      items.push(['Rename', 'edit', () => renameItem(li)]);
      items.push(['Delete', 'delete', () => deleteItems([li]), 'danger']);
    }
    for (const [label, ic, fn, cls] of items) {
      const b = el('button', 'mi' + (cls ? ' ' + cls : ''));
      b.type = 'button';
      b.append(icon(ic), el('span', '', label));
      b.addEventListener('click', (ev) => { ev.stopPropagation(); closeMenu(); fn(); });
      menu.append(b);
    }
    menu.hidden = false;
    const r = anchor.getBoundingClientRect();
    const mw = menu.offsetWidth, mh = menu.offsetHeight;
    let x = Math.min(r.right - mw, window.innerWidth - mw - 8);
    let y = r.bottom + 4;
    if (y + mh > window.innerHeight - 8) y = Math.max(8, r.top - mh - 4);
    menu.style.left = Math.max(8, x) + 'px';
    menu.style.top = y + 'px';
    const first = menu.querySelector('button');
    if (first) first.focus({ preventScroll: true });
  }
  document.addEventListener('click', (e) => { if (!menu.hidden && !menu.contains(e.target) && !e.target.closest('.mn')) closeMenu(); });
  window.addEventListener('scroll', () => { if (!menu.hidden) closeMenu(); }, { passive: true });
  window.addEventListener('resize', () => { if (!menu.hidden) closeMenu(); });

  // ── Actions ──
  function zipSelected(sel) {
    if (!sel.length) return;
    // A form POST: a long selection would overflow a GET request line.
    const f = el('form');
    f.method = 'POST';
    f.action = here + '?op=zip';
    f.hidden = true;
    for (const li of sel) {
      const i = el('input');
      i.type = 'hidden';
      i.name = 'f';
      i.value = li.dataset.n;
      f.append(i);
    }
    body.append(f);
    f.submit();
    f.remove();
  }
  async function renameItem(li) {
    const old = li.dataset.n;
    const answer = prompt('Rename “' + old + '” to:', old);
    if (answer === null) return;
    const name = answer.trim();
    if (!name || name === old) return;
    if (name.includes('/')) { toast('Names can’t contain “/”.', true); return; }
    try {
      await call('POST', here + encodeURIComponent(old) + '?op=rename&to=' + encodeURIComponent(name));
      toast('Renamed to “' + name + '”');
      refresh();
    } catch (e) { toast(e.message, true); }
  }
  async function deleteItems(list) {
    if (!list.length) return;
    const one = list[0];
    const msg = list.length === 1
      ? 'Delete “' + one.dataset.n + '”' + (isDir(one) ? ' and everything in it' : '') + '? This can’t be undone.'
      : 'Delete ' + list.length + ' items? Folders are deleted with everything in them. This can’t be undone.';
    if (!confirm(msg)) return;
    let failed = 0;
    for (const li of list) {
      try { await call('DELETE', here + encodeURIComponent(li.dataset.n)); } catch (e) { failed++; }
    }
    toast(failed ? failed + (failed === 1 ? ' item' : ' items') + ' couldn’t be deleted.' : 'Deleted.', failed > 0);
    setSelecting(false);
    refresh();
  }
  if (RW) {
    $('b-mkdir').addEventListener('click', async () => {
      const answer = prompt('New folder name:');
      const name = (answer || '').trim();
      if (!name) return;
      if (name.includes('/')) { toast('Names can’t contain “/”.', true); return; }
      try { await call('POST', here + '?op=mkdir&name=' + encodeURIComponent(name)); refresh(); } catch (e) { toast(e.message, true); }
    });
  }

  /** Re-reads this folder's page and swaps in the fresh listing. */
  async function refresh() {
    try {
      const r = await fetch(location.pathname, { credentials: 'same-origin', cache: 'no-store' });
      if (!r.ok) return;
      const doc = new DOMParser().parseFromString(await r.text(), 'text/html');
      const fresh = doc.getElementById('list');
      if (!fresh) return;
      listEl.innerHTML = fresh.innerHTML;
      const sum = doc.getElementById('summary');
      if (sum) $('summary').textContent = sum.textContent;
      const zipBtn = $('b-zip');
      if (zipBtn) zipBtn.hidden = rows().length === 0;
      decorate();
    } catch (e) { /* offline: keep what's shown */ }
  }

  // ── Uploads ──
  const panel = $('uploads');
  const jobs = [];
  let active = 0;
  let renderQueued = false;
  const MAX_PARALLEL = 2;

  function existingNames() { return new Set(rows().map((li) => li.dataset.n)); }

  function enqueue(files) {
    if (!files.length) return;
    const names = existingNames();
    let list = files;
    const clash = files.filter((f) => !f.rel.includes('/') && names.has(f.file.name));
    if (clash.length) {
      const msg = clash.length === 1
        ? '“' + clash[0].file.name + '” already exists here. Replace it?'
        : clash.length + ' files already exist here. Replace them?';
      if (!confirm(msg)) list = files.filter((f) => !clash.includes(f));
    }
    if (!list.length) return;
    for (const f of list) jobs.push({ file: f.file, rel: f.rel, size: f.file.size, sent: 0, state: 'queued', xhr: null, li: null });
    buildPanel();
    pump();
  }

  function pump() {
    while (active < MAX_PARALLEL) {
      const job = jobs.find((j) => j.state === 'queued');
      if (!job) break;
      start(job);
    }
    scheduleRender();
    if (active === 0 && !jobs.some((j) => j.state === 'queued')) finished();
  }

  function start(job) {
    active++;
    job.state = 'up';
    const xhr = new XMLHttpRequest();
    job.xhr = xhr;
    const rel = job.rel.split('/').map(encodeURIComponent).join('/');
    let url = here + rel + '?mtime=' + (job.file.lastModified || 0);
    if (job.rel.includes('/')) url += '&mkdirs=1';
    xhr.open('PUT', url);
    xhr.setRequestHeader('X-BFE-Request', '1');
    xhr.upload.onprogress = (e) => { job.sent = e.loaded; scheduleRender(); };
    const done = (state, msg) => {
      if (job.state !== 'up') return;
      job.state = state;
      job.msg = msg || '';
      if (state === 'ok') job.sent = job.size;
      active--;
      pump();
    };
    xhr.onload = () => (xhr.status >= 200 && xhr.status < 300) ? done('ok') : done('err', (xhr.responseText || xhr.statusText || 'Failed').trim());
    xhr.onerror = () => done('err', 'Connection lost');
    xhr.onabort = () => done('cancel');
    xhr.send(job.file);
  }

  function cancel(job) {
    if (job.state === 'queued') { job.state = 'cancel'; pump(); }
    else if (job.state === 'up' && job.xhr) job.xhr.abort();
  }

  function buildPanel() {
    panel.innerHTML = '';
    const head = el('div', 'up-head');
    head.append(el('b', '', 'Uploading'));
    head.append(el('span', 'grow'));
    head.append(button('', 'delete', () => jobs.forEach(cancel), { cls: 'icon', title: 'Cancel all uploads' }));
    head.append(button('', 'close', () => { panel.hidden = true; }, { cls: 'icon', title: 'Hide' }));
    const total = el('div', 'up-total');
    const tb = el('div', 'bar-p');
    tb.append(el('i'));
    total.append(tb);
    const ul = el('ul', 'up-list');
    for (const job of jobs) {
      const li = el('li');
      li.append(el('span', 'up-name', job.rel), el('span', 'up-st', ''));
      const x = el('button', 'x');
      x.type = 'button';
      x.title = 'Cancel';
      x.append(icon('close'));
      x.addEventListener('click', () => cancel(job));
      li.append(x);
      const b = el('div', 'bar-p');
      b.append(el('i'));
      li.append(b);
      job.li = li;
      ul.append(li);
    }
    panel.append(head, total, ul);
    panel.hidden = false;
    render();
  }

  function scheduleRender() {
    if (renderQueued) return;
    renderQueued = true;
    requestAnimationFrame(() => { renderQueued = false; render(); });
  }

  function render() {
    if (panel.hidden || !panel.firstChild) return;
    let size = 0, sent = 0, done = 0, failed = 0, live = 0;
    for (const j of jobs) {
      if (j.state === 'cancel') continue;
      size += j.size;
      sent += Math.min(j.sent, j.size);
      if (j.state === 'ok') done++;
      if (j.state === 'err') failed++;
      if (j.state === 'up' || j.state === 'queued') live++;
      if (!j.li) continue;
      const pct = j.size ? Math.floor(100 * Math.min(j.sent, j.size) / j.size) : (j.state === 'ok' ? 100 : 0);
      j.li.className = j.state;
      j.li.querySelector('.up-st').textContent =
        j.state === 'ok' ? 'Done' : j.state === 'err' ? 'Failed' : j.state === 'cancel' ? 'Cancelled'
          : j.state === 'queued' ? 'Waiting' : pct + '%';
      if (j.state === 'err') j.li.title = j.msg;
      j.li.querySelector('.bar-p i').style.width = (j.state === 'cancel' ? 0 : pct) + '%';
      const x = j.li.querySelector('.x');
      x.hidden = !(j.state === 'up' || j.state === 'queued');
    }
    const total = jobs.filter((j) => j.state !== 'cancel').length;
    const title = live
      ? 'Uploading ' + (done + failed + 1 > total ? total : done + failed + 1) + ' of ' + total + ' · ' + fmtSize(sent) + ' / ' + fmtSize(size)
      : failed ? done + ' uploaded, ' + failed + ' failed' : done + (done === 1 ? ' file' : ' files') + ' uploaded';
    panel.querySelector('.up-head b').textContent = title;
    panel.querySelector('.up-total i').style.width = (size ? Math.floor(100 * sent / size) : 100) + '%';
  }

  function finished() {
    render();
    const failed = jobs.filter((j) => j.state === 'err');
    if (failed.length) toast(failed[0].msg || 'Upload failed', true);
    refresh();
    // Keep the finished list around a moment, longer when something needs attention.
    const snapshot = jobs.length;
    setTimeout(() => {
      if (jobs.length === snapshot && active === 0 && !jobs.some((j) => j.state === 'queued')) {
        if (!failed.length) panel.hidden = true;
        jobs.length = 0;
      }
    }, failed.length ? 15000 : 4000);
  }

  function fromFileList(fileList) {
    return Array.from(fileList).map((f) => ({ file: f, rel: f.webkitRelativePath || f.name }));
  }

  /** Files from a drop, including whole folders (walked with the entries API where available). */
  async function fromDrop(dt) {
    const entries = Array.from(dt.items || [])
      .filter((it) => it.kind === 'file')
      .map((it) => (it.webkitGetAsEntry ? it.webkitGetAsEntry() : null));
    if (!entries.length || entries.some((e) => !e)) return fromFileList(dt.files || []);
    const out = [];
    async function walk(entry, prefix) {
      if (entry.isFile) {
        const f = await new Promise((res, rej) => entry.file(res, rej));
        out.push({ file: f, rel: prefix + f.name });
      } else if (entry.isDirectory) {
        const reader = entry.createReader();
        for (;;) {
          const batch = await new Promise((res, rej) => reader.readEntries(res, rej));
          if (!batch.length) break;
          for (const child of batch) await walk(child, prefix + entry.name + '/');
        }
      }
    }
    for (const e of entries) await walk(e, '');
    return out;
  }

  if (RW) {
    const pick = $('pick');
    const pickDir = $('pick-dir');
    $('b-upload').addEventListener('click', () => pick.click());
    $('b-upload-dir').addEventListener('click', () => pickDir.click());
    pick.addEventListener('change', () => { enqueue(fromFileList(pick.files)); pick.value = ''; });
    pickDir.addEventListener('change', () => { enqueue(fromFileList(pickDir.files)); pickDir.value = ''; });
  }
  const dropEl = $('drop');
  let dragDepth = 0;
  const hasFiles = (e) => e.dataTransfer && Array.from(e.dataTransfer.types || []).includes('Files');
  window.addEventListener('dragenter', (e) => {
    if (!hasFiles(e)) return;
    e.preventDefault();
    dragDepth++;
    if (RW) dropEl.hidden = false;
  });
  window.addEventListener('dragover', (e) => {
    if (!hasFiles(e)) return;
    e.preventDefault();
    e.dataTransfer.dropEffect = RW ? 'copy' : 'none';
  });
  window.addEventListener('dragleave', (e) => {
    if (!hasFiles(e)) return;
    if (--dragDepth <= 0) { dragDepth = 0; dropEl.hidden = true; }
  });
  window.addEventListener('drop', (e) => {
    if (!hasFiles(e)) return;
    e.preventDefault();
    dragDepth = 0;
    dropEl.hidden = true;
    if (!RW) { toast(TOP ? 'Open a shared folder first, then drop files there.' : 'This folder is read-only.', true); return; }
    fromDrop(e.dataTransfer).then(enqueue).catch(() => toast('Couldn’t read the dropped files.', true));
  });
  window.addEventListener('beforeunload', (e) => {
    if (jobs.some((j) => j.state === 'up' || j.state === 'queued')) { e.preventDefault(); e.returnValue = ''; }
  });

  // ── Media viewer ──
  const viewer = $('viewer');
  let vItems = [];
  let vIndex = 0;
  function openViewer(li) {
    vItems = li.dataset.k === 'image' ? rows().filter((r) => visible(r) && r.dataset.k === 'image') : [li];
    vIndex = Math.max(0, vItems.indexOf(li));
    if (viewer.hidden) history.pushState({ bfeViewer: true }, '');
    showViewer();
  }
  function stopMedia() {
    const m = viewer.querySelector('video, audio');
    if (m) { m.pause(); m.removeAttribute('src'); m.load(); }
  }
  function showViewer() {
    const li = vItems[vIndex];
    if (!li) { closeViewer(); return; }
    stopMedia();
    viewer.innerHTML = '';
    const src = urlOf(li);
    const top = el('div', 'v-top');
    top.append(el('span', 'v-name', li.dataset.n));
    if (vItems.length > 1) top.append(el('span', 'v-count', (vIndex + 1) + ' / ' + vItems.length));
    const dl = el('a', 'v-btn');
    dl.href = src + '?dl=1';
    dl.title = 'Download';
    dl.append(icon('download'));
    const nt = el('a', 'v-btn');
    nt.href = src;
    nt.target = '_blank';
    nt.rel = 'noopener';
    nt.title = 'Open in new tab';
    nt.append(icon('open'));
    const close = el('button', 'v-btn');
    close.type = 'button';
    close.title = 'Close';
    close.append(icon('close'));
    close.addEventListener('click', () => closeViewer());
    top.append(dl, nt, close);

    const stage = el('div', 'v-stage');
    const k = li.dataset.k;
    const fail = () => stage.append(el('p', 'v-err', 'This file can’t be played in this browser. Use Download instead.'));
    if (k === 'image') {
      const img = el('img');
      img.src = src;
      img.alt = li.dataset.n;
      img.addEventListener('error', fail);
      stage.append(img);
    } else if (k === 'video') {
      const v = el('video');
      v.src = src;
      v.controls = true;
      v.autoplay = true;
      v.playsInline = true;
      v.addEventListener('error', fail);
      stage.append(v);
    } else {
      const box = el('div', 'v-audio');
      box.append(icon('audio'));
      const a = el('audio');
      a.src = src;
      a.controls = true;
      a.autoplay = true;
      a.addEventListener('error', fail);
      box.append(a);
      stage.append(box);
    }
    if (vItems.length > 1) {
      const prev = el('button', 'v-btn v-nav prev');
      prev.type = 'button';
      prev.title = 'Previous';
      prev.append(icon('prev'));
      prev.addEventListener('click', () => step(-1));
      const next = el('button', 'v-btn v-nav next');
      next.type = 'button';
      next.title = 'Next';
      next.append(icon('next'));
      next.addEventListener('click', () => step(1));
      stage.append(prev, next);
    }
    viewer.append(top, stage);
    viewer.hidden = false;
    body.classList.add('noscroll');
  }
  function step(d) {
    if (vItems.length < 2) return;
    vIndex = (vIndex + d + vItems.length) % vItems.length;
    showViewer();
  }
  function closeViewer(fromHistory) {
    if (viewer.hidden) return;
    stopMedia();
    viewer.hidden = true;
    viewer.innerHTML = '';
    body.classList.remove('noscroll');
    if (!fromHistory && history.state && history.state.bfeViewer) history.back();
  }
  window.addEventListener('popstate', () => closeViewer(true));
  let touchX = null;
  viewer.addEventListener('touchstart', (e) => { touchX = e.touches.length === 1 ? e.touches[0].clientX : null; }, { passive: true });
  viewer.addEventListener('touchend', (e) => {
    if (touchX === null || vItems.length < 2 || !viewer.querySelector('img')) return;
    const dx = e.changedTouches[0].clientX - touchX;
    if (Math.abs(dx) > 60) step(dx < 0 ? 1 : -1);
    touchX = null;
  });
  document.addEventListener('keydown', (e) => {
    if (!viewer.hidden) {
      if (e.key === 'Escape') closeViewer();
      else if (e.key === 'ArrowLeft') step(-1);
      else if (e.key === 'ArrowRight') step(1);
      return;
    }
    if (e.key === 'Escape') {
      if (!menu.hidden) closeMenu();
      else if (selecting()) setSelecting(false);
    }
  });

  // ── Start ──
  function decorate() {
    localizeDates();
    applySort();
    applyHidden();
    applyFilter();
    if (selecting()) updateSelbar();
  }
  decorate();
})();
