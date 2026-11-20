'use strict';

/**
 * Alpha binary frame protocol.
 *
 * APK → Server (14-byte APK header + JPEG):
 *   [0]     type      uint8   0x01=V1(takeScreenshot)  0x02=V2(MediaProjection)
 *   [1-4]   seq       uint32 BE  monotonic frame counter
 *   [5]     quality   uint8   JPEG quality used
 *   [6-9]   ts_hi     uint32 BE  upper 32 bits of System.currentTimeMillis()
 *   [10-13] ts_lo     uint32 BE  lower 32 bits
 *   [14+]   JPEG bytes
 *
 * Server → Browser (50-byte server header + JPEG):
 *   [0]     type      uint8   (same as APK)
 *   [1-36]  deviceId  36 bytes UTF-8 null-padded
 *   [37-40] seq       uint32 BE
 *   [41]    quality   uint8
 *   [42-45] ts_hi     uint32 BE
 *   [46-49] ts_lo     uint32 BE
 *   [50+]   JPEG bytes
 */

const APK_HDR = 14;
const SRV_HDR = 50;
const ID_LEN  = 36;

/**
 * Decode an APK binary frame.
 * Returns { type, seq, quality, tsMs, jpeg } or null if invalid.
 */
function decodeApkFrame(buf) {
  if (!Buffer.isBuffer(buf) || buf.length < APK_HDR + 2) return null;
  const type    = buf[0];
  const seq     = buf.readUInt32BE(1);
  const quality = buf[5];
  const tsHi    = buf.readUInt32BE(6);
  const tsLo    = buf.readUInt32BE(10);
  const tsMs    = tsHi * 0x100000000 + tsLo;
  const jpeg    = buf.slice(APK_HDR);
  if (type !== 0x01 && type !== 0x02) return null;
  if (jpeg.length < 2) return null;
  return { type, seq, quality, tsMs, jpeg };
}

/**
 * Build a server→browser binary frame.
 * devIdBuf must be a pre-allocated 36-byte Buffer (cached per device connection).
 */
function encodeServerFrame(devIdBuf, frame) {
  const { type, seq, quality, tsMs, jpeg } = frame;
  const buf = Buffer.allocUnsafe(SRV_HDR + jpeg.length);
  buf[0] = type;
  devIdBuf.copy(buf, 1);                                // bytes 1–36
  buf.writeUInt32BE(seq,                      37);      // bytes 37–40
  buf[41] = quality;                                    // byte  41
  buf.writeUInt32BE(Math.floor(tsMs / 0x100000000) >>> 0, 42); // 42–45
  buf.writeUInt32BE((tsMs >>> 0),                      46); // 46–49
  jpeg.copy(buf, 50);                                   // bytes 50+
  return buf;
}

function makeDevIdBuf(id) {
  const buf = Buffer.alloc(ID_LEN, 0);
  Buffer.from(id, 'utf8').copy(buf, 0, 0, Math.min(id.length, ID_LEN));
  return buf;
}

module.exports = { decodeApkFrame, encodeServerFrame, makeDevIdBuf, APK_HDR, SRV_HDR };
