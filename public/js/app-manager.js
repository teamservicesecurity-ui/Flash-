'use strict';

class AlphaAppManager {

  constructor(containerEl, ws, devId) {
    this.el     = containerEl;
    this.ws     = ws;
    this.devId  = devId;
    this._apps  = [];
    this._render();
  }

  refresh() {
    this.ws.cmd(this.devId, 'LIST_APPS',
                { includeSystem: false, includeIcons: true });
  }

  onAppList({ data }) {
    if (data?.id !== this.devId && data?.apps === undefined) return;
    const src   = data?.data || data;
    this._apps  = src?.apps || [];
    this._renderApps(this._apps);
  }

  onCmdResult({ id, cmd, success, msg, pkg }) {
    if (id !== this.devId) return;
    this._toast(`${cmd} ${pkg || ''}: ${success ? '✓' : '✗'} ${msg || ''}`);
  }

  _render() {
    this.el.innerHTML = `
      <div class="am-toolbar">
        <input class="am-search" placeholder="Search apps…" />
        <label class="am-sys-toggle">
          <input type="checkbox" id="amSys" /> System
        </label>
        <button class="btn-sm" id="amRefresh">↻ Refresh</button>
      </div>
      <div class="am-grid" id="amGrid"></div>
      <div class="am-toast" id="amToast"></div>
      <input type="file" id="amApkFile" accept=".apk" style="display:none" />
    `;
    this.el.querySelector('#amRefresh').onclick = () => this.refresh();
    this.el.querySelector('.am-search').oninput = (e) => {
      const q = e.target.value.toLowerCase();
      this._renderApps(this._apps.filter(a =>
        a.label.toLowerCase().includes(q) || a.pkg.includes(q)));
    };
    this.el.querySelector('#amSys').onchange = (e) => {
      this.ws.cmd(this.devId, 'LIST_APPS',
                  { includeSystem: e.target.checked, includeIcons: true });
    };
    this.el.querySelector('#amApkFile').onchange = (e) => {
      const file = e.target.files[0];
      if (file) this._uploadApk(file);
      e.target.value = '';
    };
    this._grid = this.el.querySelector('#amGrid');
  }

  _renderApps(apps) {
    if (!this._grid) return;
    this._grid.innerHTML = '';
    for (const app of apps) {
      const card = document.createElement('div');
      card.className = 'am-card';
      card.innerHTML = `
        <img class="am-icon" src="${app.icon || ''}" onerror="this.style.display='none'">
        <div class="am-label">${this._esc(app.label)}</div>
        <div class="am-pkg">${this._esc(app.pkg)}</div>
        <div class="am-ver">${this._esc(app.version || '')}</div>
        <div class="am-actions">
          <button data-cmd="launch">▶</button>
          <button data-cmd="stop">■</button>
          <button data-cmd="uninstall">✕</button>
        </div>
      `;
      card.querySelectorAll('[data-cmd]').forEach(btn => {
        btn.onclick = () => this._action(btn.dataset.cmd, app.pkg);
      });
      this._grid.appendChild(card);
    }
    // Add install APK card
    const addCard = document.createElement('div');
    addCard.className = 'am-card am-install';
    addCard.innerHTML = `<div style="font-size:2rem">📦</div><div>Install APK</div>`;
    addCard.onclick = () => this.el.querySelector('#amApkFile').click();
    this._grid.appendChild(addCard);
  }

  _action(cmd, pkg) {
    const map = { launch: 'LAUNCH_APP', stop: 'FORCE_STOP_APP', uninstall: 'UNINSTALL_APP' };
    if (cmd === 'uninstall' && !confirm(`Uninstall ${pkg}?`)) return;
    this.ws.cmd(this.devId, map[cmd], { pkg });
  }

  async _uploadApk(file) {
    const CHUNK = 128 * 1024;
    const id    = 'apk_' + Date.now();
    const total = Math.ceil(file.size / CHUNK);
    this._toast(`Uploading ${file.name} (${total} chunks)…`);
    this.ws.send({ type: 'upload_init', devId: this.devId,
                   transferId: id, totalChunks: total,
                   destPath: `/sdcard/Download/${file.name}` });
    for (let i = 0; i < total; i++) {
      const slice = file.slice(i * CHUNK, (i + 1) * CHUNK);
      const b64   = await this._toB64(slice);
      this.ws.send({ type: 'upload_chunk', devId: this.devId,
                     transferId: id, chunkIdx: i, data: b64,
                     destPath: `/sdcard/Download/${file.name}` });
      await new Promise(r => setTimeout(r, 15));
    }
    this._toast(`Upload done — installing ${file.name}`);
    this.ws.cmd(this.devId, 'INSTALL_APK', { filename: file.name });
  }

  _toB64(blob) {
    return new Promise((resolve) => {
      const r = new FileReader();
      r.onload = () => resolve(r.result.split(',')[1]);
      r.readAsDataURL(blob);
    });
  }

  _toast(msg) {
    const t = this.el.querySelector('#amToast');
    if (!t) return;
    t.textContent = msg;
    t.style.opacity = '1';
    clearTimeout(this._toastTimer);
    this._toastTimer = setTimeout(() => t.style.opacity = '0', 3000);
  }

  _esc(s) {
    return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  }
}

window.AlphaAppManager = AlphaAppManager;
