import assert from 'node:assert/strict';
import test from 'node:test';
import { analyticsWindowError, createAnalyticsLoader, defaultAnalyticsWindow, formatMinor, formatAnalyticsMonth } from '../src/analyticsModel.ts';

test('ventana predeterminada usa seis meses cerrados en Buenos Aires y cruza años', () => {
  assert.deepEqual(defaultAnalyticsWindow(new Date('2026-01-01T01:00:00Z')), { fromMonth: '2025-06', toMonth: '2025-11' });
});
test('valida extremos, formato, orden, 60 meses y meses cerrados', () => {
  assert.equal(analyticsWindowError('2021-10', '2026-09', '2026-10'), '');
  for (const [from, to] of [['2021-09', '2026-09'], ['2026-09', '2026-08'], ['2026-01', '2026-10'], ['0000-01', '2026-09'], ['', '2026-09'], ['2026-13', '2026-09']]) {
    assert.notEqual(analyticsWindowError(from, to, '2026-10'), '');
  }
  assert.equal(analyticsWindowError('0001-01', '0001-01', '2026-10'), '');
});
test('formato monetario distingue nulo de cero y conserva centavos en el límite seguro', () => {
  assert.equal(formatMinor(null), 'No disponible');
  assert.equal(formatMinor(0), '$ 0,00');
  assert.equal(formatMinor(10001), '$ 100,01');
  assert.equal(formatMinor(-1), '−$ 0,01');
  assert.equal(formatMinor(9007199254740991), '$ 90.071.992.547.409,91');
});
test('etiquetas mensuales conservan los años 0001 a 0099 sin convertirlos al siglo XX', () => {
  assert.equal(formatAnalyticsMonth('0001-01'), 'enero de 1');
  assert.equal(formatAnalyticsMonth('0099-12'), 'diciembre de 99');
});
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };
test('cargador real descarta A→B→A, consultas anteriores, errores tardíos y desmontaje', async () => {
  const pending = []; const results = [];
  const loader = createAnalyticsLoader(() => { const item = deferred(); pending.push(item); return item.promise; }, (...args) => results.push(args));
  const a = loader.load('A1', 1, '2026-01', '2026-02');
  const b = loader.load('B', 2, '2026-01', '2026-02');
  const nextA = loader.load('A2', 1, '2026-01', '2026-03');
  pending[2].resolve('vigente'); await nextA;
  pending[0].resolve('anterior'); pending[1].reject(new Error('tardío')); await Promise.all([a, b]);
  assert.deepEqual(results, [['A2', 'vigente', null]]);
  const last = loader.load('A3', 1, '2026-01', '2026-03');
  loader.invalidate(); pending[3].resolve('tras logout'); await last;
  assert.equal(results.length, 1);
});
test('cargador publica el error vigente y permite reintentar la misma consulta', async () => {
  const results = []; let failed = true;
  const loader = createAnalyticsLoader(async () => { if (failed) throw new Error('sin servicio'); return 'recuperado'; }, (...args) => results.push(args));
  await loader.load('1', 1, '2026-01', '2026-02');
  assert.equal(results[0][1], null); assert.equal(results[0][2].message, 'sin servicio');
  failed = false; await loader.load('2', 1, '2026-01', '2026-02');
  assert.deepEqual(results[1], ['2', 'recuperado', null]);
});
