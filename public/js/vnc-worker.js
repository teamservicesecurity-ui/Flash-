/**
 * vnc-worker.js — Alpha VNC decode worker.
 *
 * Receives JPEG ArrayBuffers from main thread via postMessage.
 * Uses createImageBitmap() for GPU-accelerated decode (zero-copy Transferable).
 * Posts back ImageBitmap via Transferable so the main thread can drawImage()
 * without any CPU-side pixel copy.
 */
'use strict';

self.onmessage = async ({ data }) => {
  const { jpeg, seq, tsMs } = data;
  if (!jpeg || jpeg.byteLength < 4) return;

  try {
    const blob = new Blob([jpeg], { type: 'image/jpeg' });
    // createImageBitmap is GPU-accelerated on all modern browsers
    const bmp  = await createImageBitmap(blob);
    // Transfer ImageBitmap — zero copy, can't be used by worker after this
    self.postMessage({ bmp, seq, tsMs, bytes: jpeg.byteLength }, [bmp]);
  } catch (e) {
    // Corrupt JPEG — silently drop
  }
};
