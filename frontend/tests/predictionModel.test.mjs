import assert from 'node:assert/strict';
import test from 'node:test';
import {
  createPredictionLoader,
  formatPredictionMethod,
  formatPredictionMonth,
  validatePredictionTargetMonth,
} from '../src/predictionModel.ts';

test('valida mes objetivo: admite vacío como automático y exige YYYY-MM con años 0001 a 9999', () => {
  assert.equal(validatePredictionTargetMonth(''), '');
  assert.equal(validatePredictionTargetMonth('   '), '');
  assert.equal(validatePredictionTargetMonth('2026-11'), '');
  assert.equal(validatePredictionTargetMonth('0001-01'), '');
  assert.equal(validatePredictionTargetMonth('9999-12'), '');

  assert.notEqual(validatePredictionTargetMonth('0000-01'), '');
  assert.notEqual(validatePredictionTargetMonth('2026-00'), '');
  assert.notEqual(validatePredictionTargetMonth('2026-13'), '');
  assert.notEqual(validatePredictionTargetMonth('2026-1'), '');
  assert.notEqual(validatePredictionTargetMonth('2026'), '');
  assert.notEqual(validatePredictionTargetMonth('noviembre-2026'), '');
});

test('etiquetas mensuales de predicción formatean correctamente respetando años antiguos', () => {
  assert.equal(formatPredictionMonth('2026-11'), 'noviembre de 2026');
  assert.equal(formatPredictionMonth('0001-01'), 'enero de 1');
  assert.equal(formatPredictionMonth('0099-12'), 'diciembre de 99');
  assert.equal(formatPredictionMonth('invalido'), 'invalido');
});

test('identificación del método predictivo distingue simulado de modelo real SES', () => {
  assert.equal(formatPredictionMethod('mock', true), 'Simulado (mock)');
  assert.equal(formatPredictionMethod('simple_exponential_smoothing', true), 'Simulado (mock)');
  assert.equal(formatPredictionMethod('simple_exponential_smoothing', false), 'Suavizado Exponencial Simple (SES)');
  assert.equal(formatPredictionMethod('mock', false), 'Simulado (mock)');
  assert.equal(formatPredictionMethod('arima_custom', false), 'arima_custom');
});

const deferred = () => {
  let resolve, reject;
  const promise = new Promise((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
};

test('cargador predictivo descarta A→B→A, consultas anteriores, errores tardíos y desmontaje', async () => {
  const pending = [];
  const results = [];
  const loader = createPredictionLoader(
    () => {
      const item = deferred();
      pending.push(item);
      return item.promise;
    },
    (...args) => results.push(args)
  );

  const a = loader.load('A1', 1, '2026-11');
  const b = loader.load('B', 2, '2026-11');
  const nextA = loader.load('A2', 1, '2026-12');

  pending[2].resolve('vigente');
  await nextA;

  pending[0].resolve('anterior');
  pending[1].reject(new Error('tardío'));
  await Promise.all([a, b]);

  assert.deepEqual(results, [['A2', 'vigente', null]]);

  const last = loader.load('A3', 1, '2026-12');
  loader.invalidate();
  pending[3].resolve('tras logout');
  await last;

  assert.equal(results.length, 1);
});

test('cargador predictivo publica error vigente y permite reintentar tras fallo', async () => {
  const results = [];
  let failed = true;
  const loader = createPredictionLoader(
    async () => {
      if (failed) throw new Error('Servicio predictivo no disponible');
      return 'recuperado';
    },
    (...args) => results.push(args)
  );

  await loader.load('1', 1, '2026-11');
  assert.equal(results[0][1], null);
  assert.equal(results[0][2].message, 'Servicio predictivo no disponible');

  failed = false;
  await loader.load('2', 1, '2026-11');
  assert.deepEqual(results[1], ['2', 'recuperado', null]);
});

test('previsión envía el horizonte de cada consulta y descarta respuestas de otro horizonte', async () => {
  const calls = [];
  const published = [];
  const loader = createPredictionLoader((org, origin, horizon) => {
    const pending = deferred();
    calls.push({ org, origin, horizon, pending });
    return pending.promise;
  }, (...args) => published.push(args));
  const four = loader.load('org1-4', 1, '2024-12', 4);
  const six = loader.load('org1-6', 1, '2024-12', 6);
  calls[1].pending.resolve({ horizon_months: 6 });
  await six;
  calls[0].pending.resolve({ horizon_months: 4 });
  await four;
  const five = loader.load('org2-auto-5', 2, undefined, 5);
  calls[2].pending.resolve({ horizon_months: 5 });
  await five;
  assert.deepEqual(calls.map(({ org, origin, horizon }) => [org, origin, horizon]),
    [[1, '2024-12', 4], [1, '2024-12', 6], [2, undefined, 5]]);
  assert.deepEqual(published, [['org1-6', { horizon_months: 6 }, null],
    ['org2-auto-5', { horizon_months: 5 }, null]]);
});
