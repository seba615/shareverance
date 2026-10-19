import assert from 'node:assert/strict';
import test from 'node:test';
import { createReceiptLoader } from '../src/receiptLoader.ts';

function deferred() {
  let resolve, reject;
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail; });
  return { promise, resolve, reject };
}
function fixture() {
  const uploads = [], scans = [], events = [];
  const loader = createReceiptLoader({
    upload() { const request = deferred(); uploads.push(request); return request.promise; },
    processOcr() { const request = deferred(); scans.push(request); return request.promise; },
    onUpload: receipt => events.push(['upload', receipt.receiptId]),
    onResult: result => events.push(['result', result.description]),
    onError: error => events.push(['error', error.message]),
    onOcrError: () => events.push(['ocrError']),
    onLoading: value => events.push(['loading', value]),
  });
  return { loader, uploads, scans, events };
}
const tick = () => new Promise(resolve => setImmediate(resolve));

test('cancelar durante la subida descarta el resultado y no inicia OCR', async () => {
  const { loader, uploads, scans, events } = fixture();
  const pending = loader.load(1, {}, () => true);
  assert.equal(loader.isLoading(), true);
  loader.invalidate();
  uploads[0].resolve({ receiptId: 'old' });
  await pending;
  assert.equal(scans.length, 0);
  assert.equal(loader.isLoading(), false);
  assert.deepEqual(events, [['loading', true], ['loading', false]]);
});

test('cancelar y reabrir en la misma organización descarta OCR anterior sin liberar la carga nueva', async () => {
  const { loader, uploads, scans, events } = fixture();
  const first = loader.load(1, {}, () => true);
  uploads[0].resolve({ receiptId: 'old' }); await tick();
  loader.invalidate();
  const second = loader.load(1, {}, () => true);
  const before = [...events];
  scans[0].resolve({ description: 'viejo' }); await first;
  assert.deepEqual(events, before);
  assert.equal(loader.isLoading(), true);
  uploads[1].resolve({ receiptId: 'new' }); await tick();
  scans[1].resolve({ description: 'nuevo' }); await second;
  assert.deepEqual(events.slice(-3), [['upload', 'new'], ['result', 'nuevo'], ['loading', false]]);
});

test('un error OCR tardío no reaparece tras cancelar', async () => {
  const { loader, uploads, scans, events } = fixture();
  const pending = loader.load(1, {}, () => true);
  uploads[0].resolve({ receiptId: 'old' }); await tick();
  loader.invalidate(); const before = [...events];
  scans[0].reject(new Error('fallo antiguo')); await pending;
  assert.deepEqual(events, before);
});

test('un fallo OCR vigente permite carga manual conservando el comprobante', async () => {
  const { loader, uploads, scans, events } = fixture();
  const pending = loader.load(1, {}, () => true);
  uploads[0].resolve({ receiptId: 'current' }); await tick();
  scans[0].reject(new Error('sin servicio')); await pending;
  assert.deepEqual(events, [['loading', true], ['upload', 'current'], ['ocrError'], ['loading', false]]);
  assert.equal(loader.isLoading(), false);
});

test('cambiar de organización y cancelar descarta errores de subida antiguos', async () => {
  const { loader, uploads, events } = fixture();
  let current = true;
  const pending = loader.load(1, {}, () => current);
  current = false; loader.invalidate(); const before = [...events];
  uploads[0].reject(new Error('organización anterior')); await pending;
  assert.deepEqual(events, before);
});
