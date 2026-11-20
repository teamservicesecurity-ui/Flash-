/**
 * ws-client.js — Alpha operator WebSocket client.
 *
 * Connects to ws[s]://host/ws?role=operator&token=TOKEN
 * Binary frames → emitted as 'vnc_frame' with decoded header
 * JSON messages → emitted by their 'type' field
 * Auto-reconnects with exponential backoff (1 s → 60 s)
 */
'use strict';

class AlphaWs extends EventTarget {

  static FRAME_V1 = 0x01;
  static FRAME_V2 = 0x02;
  static SRV_HDR  = 50;
  static ID_LEN   = 36;

  constructor(token) {
    super();
    this._token    = token;
    this._ws       = null;
    this._backoff  = 1000;
    this._dead     = false;
    this._dec      = new TextDecoder();
    this._connect();
  }

  // ── Connection ───────────────────────────────────────────────────────────────

  _connect() {
    if (this._dead) return;
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    const url   = `${proto}://${location.host}/ws?role=operator&token=${encodeURIComponent(this._token)}`;
    const ws    = new WebSocket(url);
    ws.binaryType = 'arraybuffer';
    this._ws = ws;

    ws.onopen = () => {
      this._backoff = 1000;
      this._emit('connected');
    };

    ws.onmessage = (e) => {
      if (e.data instanceof ArrayBuffer) {
        this._handleBinary(e.data);
      } else {
        this._handleText(e.data);
      }
    };

    ws.onclose = ws.onerror = () => {
      this._emit('disconnected');
      this._scheduleReconnect();
    };
  }

  _scheduleReconnect() {
    if (this._dead) return;
    const delay = this._backoff * (0.8 + Math.random() * 0.4);
    this._backoff = Math.min(this._backoff * 1.8, 60000);
    setTimeout(() => this._connect(), delay);
  }

  destroy() {
    this._dead = true;
    if (this._ws) this._ws.close();
  }

  // ── Send helpers ─────────────────────────────────────────────────────────────

  send(obj) {
    if (this._ws?.readyState === WebSocket.OPEN) {
      this._ws.send(JSON.stringify(obj));
    }
  }

  cmd(devId, cmdName, args = {}) {
    this.send({ type: 'cmd', devId, cmd: cmdName, args });
  }

  // ── Binary frame decoder ─────────────────────────────────────────────────────

  _handleBinary(buf) {
    if (buf.byteLength < AlphaWs.SRV_HDR + 2) return;
    const view  = new DataView(buf);
    const type  = view.getUint8(0);
    if (type !== AlphaWs.FRAME_V1 && type !== AlphaWs.FRAME_V2) return;

    const idBuf = new Uint8Array(buf, 1, AlphaWs.ID_LEN);
    const devId = this._dec.decode(idBuf).replace(/\0/g, '');
    const seq     = view.getUint32(37, false);
    const quality = view.getUint8(41);
    const tsHi    = view.getUint32(42, false);
    const tsLo    = view.getUint32(46, false);
    const tsMs    = tsHi * 0x100000000 + tsLo;
    const jpeg    = buf.slice(AlphaWs.SRV_HDR);

    this._emit('vnc_frame', { type, devId, seq, quality, tsMs, jpeg });
  }

  _handleText(raw) {
    let msg;
    try { msg = JSON.parse(raw); } catch { return; }
    const type = msg.type || msg.action || '';
    if (type) this._emit(type, msg);
    this._emit('message', msg);
  }

  _emit(name, detail = null) {
    this.dispatchEvent(new CustomEvent(name, { detail }));
  }

  // ── Shorthand subscription helpers ─────────────────────────────────────────

  on(type, fn) {
    this.addEventListener(type, (e) => fn(e.detail));
    return this;
  }
}

window.AlphaWs = AlphaWs;
