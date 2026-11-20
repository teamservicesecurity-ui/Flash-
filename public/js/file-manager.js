'use strict';

class AlphaFileManager {

  static ROOTS = [
    { label: '📱 Internal',   path: '/sdcard' },
    { label: '📥 Downloads',  path: '/sdcard/Download' },
    { label: '📷 DCIM',       path: '/sdcard/DCIM' },
    { label: '💬 WhatsApp',   path: '/sdcard/WhatsApp' },
    { label: '🎵 Music',      path: '/sdcard/Music' },
    { label: '📄 Documents',  path: '/sdcard/Documents' },
  ];

  static ICONS = {
    image: '🖼', video: '🎬', audio: '🎵', text: '📄',
    pdf: '📕', apk: '📦', archive: '🗜', binary: '📁',
  };

  constructor(containerEl, ws, devId) {
    this.el      = containerEl;
    this.ws      = ws;
    this.devId   = devId;
    this._path   = '/sdcard';
    this._stack  = [];
    this._transfers = new Map(); // transferId → { chunks, total, name }
    this._render();
    this._browse(this._path);
  }

  onFileList({ id, path, data }) {
    if (id !== this.devId) return;
    const parsed = typeof data === 'string' ? JSON.parse(data) : data;
    if (parsed.error) { this._toast('✗ ' + parsed.error); return; }
    this._path = parsed.path || path;
    this._renderList(parsed.items || []);
    this._renderCrumb();
  }

  onFileChunk({ id, transferId, chunkIdx, totalChunks, data, done }) {
    if (id !== this.devId) return;
    let t = this._transfers.get(transferId);
    if (!t) {
      t = { chunks: [], total: totalChunks, bytes: 0 };
      this._transfers.set(transferId, t);
    }
    if (data) {
      const bytes = Uint8Array.from(atob(data), c => c.charCodeAt(0));
      t.chunks.push(bytes);
      t.bytes += bytes.length;
      this._toast(`Downloading… ${(t.bytes / 1024).toFixed(0)} KB`);
    }
    if (done || (totalChunks > 0 && t.chunks.length >= totalChunks)) {
      const blob = new Blob(t.chunks);
      const url  = URL.createObjectURL(blob);
      const a    = document.createElement('a');
      a.href = url; a.download = t.name || transferId;
      a.click();
      URL.revokeObjectURL(url);
      this._transfers.delete(transferId);
      this._toast('✓ Download complete');
    }
  }

  onOpResult({ id, cmd, path, success, msg }) {
    if (id !== this.devId) return;
    this._toast(`${cmd}: ${success ? '✓' : '✗'} ${msg || ''}`);
    if (success) this._browse(this._path);
  }

  _render() {
    this.el.innerHTML = `
      <div class="fm-toolbar">
        <div class="fm-roots">
          ${AlphaFileManager.ROOTS.map(r =>
            `<button class="btn-sm fm-root" data-path="${r.path}">${r.label}</button>`
          ).join('')}
        </div>
        <div class="fm-crumb" id="fmCrumb"></div>
        <button class="btn-sm" id="fmUp">↑ Up</button>
        <button class="btn-sm" id="fmRefresh">↻</button>
        <button class="btn-sm" id="fmMkdir">+ Dir</button>
        <input type="file" id="fmUpload" style="display:none" multiple />
        <button class="btn-sm" id="fmUploadBtn">⬆ Upload</button>
      </div>
      <div class="fm-list" id="fmList"></div>
      <div class="fm-toast" id="fmToast"></div>
    `;

    this.el.querySelectorAll('.fm-root').forEach(b => {
      b.onclick = () => this._browse(b.dataset.path);
    });
    this.el.querySelector('#fmUp').onclick      = () => this._goUp();
    this.el.querySelector('#fmRefresh').onclick  = () => this._browse(this._path);
    this.el.querySelector('#fmMkdir').onclick    = () => {
      const name = prompt('New folder name:');
      if (name) this.ws.cmd(this.devId, 'MKDIR',
                            { path: this._path + '/' + name });
    };
    this.el.querySelector('#fmUploadBtn').onclick = () =>
      this.el.querySelector('#fmUpload').click();
    this.el.querySelector('#fmUpload').onchange   = (e) => {
      for (const f of e.target.files) this._uploadFile(f);
      e.target.value = '';
    };
    this._listEl = this.el.querySelector('#fmList');
  }

  _renderList(items) {
    if (!this._listEl) return;
    this._listEl.innerHTML = '';
    if (!items.length) {
      this._listEl.innerHTML = '<div class="fm-empty">Empty folder</div>';
      return;
    }
    for (const item of items) {
      const row = document.createElement('div');
      row.className = 'fm-row';
      const icon = item.isDir ? '📁' : (AlphaFileManager.ICONS[item.type] || '📄');
      const size = item.isDir ? '' : this._fmtSize(item.size);
      row.innerHTML = `
        <span class="fm-icon">${icon}</span>
        <span class="fm-name">${this._esc(item.name)}</span>
        <span class="fm-size">${size}</span>
        <div class="fm-row-actions">
          ${item.isDir
            ? `<button title="Open">📂</button>`
            : `<button title="Download">⬇</button>`}
          <button title="Delete">🗑</button>
          <button title="Rename">✏</button>
        </div>
      `;
      const btns = row.querySelectorAll('button');
      if (item.isDir) {
        btns[0].onclick = () => this._browse(item.path);
        row.ondblclick  = () => this._browse(item.path);
      } else {
        btns[0].onclick = () => this._download(item);
      }
      btns[item.isDir ? 1 : 1].onclick = () => {
        if (confirm(`Delete ${item.name}?`))
          this.ws.cmd(this.devId, 'DELETE_FILE', { path: item.path });
      };
      btns[item.isDir ? 2 : 2].onclick = () => {
        const n = prompt('Rename to:', item.name);
        if (n) this.ws.cmd(this.devId, 'RENAME_FILE', { path: item.path, newName: n });
      };
      this._listEl.appendChild(row);
    }
  }

  _renderCrumb() {
    const crumb = this.el.querySelector('#fmCrumb');
    if (!crumb) return;
    const parts = this._path.split('/').filter(Boolean);
    crumb.innerHTML = parts.map((p, i) => {
      const path = '/' + parts.slice(0, i + 1).join('/');
      return `<span class="fm-crumb-seg" data-path="${path}">${this._esc(p)}</span>`;
    }).join('<span class="fm-sep">/</span>');
    crumb.querySelectorAll('.fm-crumb-seg').forEach(s => {
      s.onclick = () => this._browse(s.dataset.path);
    });
  }

  _browse(path) {
    this._path = path;
    this.ws.cmd(this.devId, 'LIST_FILES', { path });
  }

  _goUp() {
    const parent = this._path.substring(0, this._path.lastIndexOf('/')) || '/sdcard';
    this._browse(parent);
  }

  _download(item) {
    const id = 'dl_' + Date.now();
    if (!this._transfers.has(id))
      this._transfers.set(id, { chunks: [], total: -1, name: item.name, bytes: 0 });
    this.ws.cmd(this.devId, 'DOWNLOAD_FILE', { path: item.path, transferId: id });
    this._toast(`Downloading ${item.name}…`);
  }

  async _uploadFile(file) {
    const CHUNK = 128 * 1024;
    const id    = 'ul_' + Date.now();
    const total = Math.ceil(file.size / CHUNK);
    const dest  = this._path + '/' + file.name;
    this.ws.send({ type: 'upload_init', devId: this.devId,
                   transferId: id, totalChunks: total, destPath: dest });
    for (let i = 0; i < total; i++) {
      const slice = file.slice(i * CHUNK, (i + 1) * CHUNK);
      const b64   = await this._toB64(slice);
      this.ws.send({ type: 'upload_chunk', devId: this.devId,
                     transferId: id, chunkIdx: i, data: b64, destPath: dest });
      this._toast(`Uploading ${file.name}… ${Math.round((i+1)/total*100)}%`);
      await new Promise(r => setTimeout(r, 15));
    }
  }

  _toB64(blob) {
    return new Promise(resolve => {
      const r = new FileReader();
      r.onload = () => resolve(r.result.split(',')[1]);
      r.readAsDataURL(blob);
    });
  }

  _fmtSize(bytes) {
    if (bytes < 0) return '';
    if (bytes < 1024)        return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1024 / 1024).toFixed(2) + ' MB';
  }

  _toast(msg) {
    const t = this.el.querySelector('#fmToast');
    if (!t) return;
    t.textContent = msg; t.style.opacity = '1';
    clearTimeout(this._tt);
    this._tt = setTimeout(() => t.style.opacity = '0', 3000);
  }

  _esc(s) { return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;'); }
}

window.AlphaFileManager = AlphaFileManager;
