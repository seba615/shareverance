import assert from 'node:assert/strict';
import test from 'node:test';
import { createOrganizationLoader } from '../src/organizationLoader.ts';

function deferred() {
  let resolve, reject;
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail; });
  return { promise, resolve, reject };
}
function fixture() {
  const pending = [], events = [];
  const loader = createOrganizationLoader({
    fetchData(id) { const response = deferred(); pending.push({ id, ...response }); return response.promise; },
    onData(id, data) { events.push(['data', id, data]); },
    onError(error) { events.push(['error', error.message]); },
    onLoading(value) { events.push(['loading', value]); },
  });
  return { loader, pending, events };
}

test('una respuesta tardía de A no sobrescribe datos ni loading de B', async () => {
  const { loader, pending, events } = fixture();
  loader.select(1); const a = loader.load(1);
  loader.select(2); const b = loader.load(2);
  pending[0].resolve('A'); await a;
  assert.deepEqual(events, [['loading', true], ['loading', true]]);
  pending[1].resolve('B'); await b;
  assert.deepEqual(events.slice(2), [['data', 2, 'B'], ['loading', false]]);
});

test('una respuesta de A posterior a B no reemplaza los datos de B', async () => {
  const { loader, pending, events } = fixture();
  loader.select(1); const a = loader.load(1);
  loader.select(2); const b = loader.load(2);
  pending[1].resolve('B'); await b;
  const latest = [...events];
  pending[0].resolve('A'); await a;
  assert.deepEqual(events, latest);
});

test('los errores de organizaciones anteriores no aparecen en la selección actual', async () => {
  const { loader, pending, events } = fixture();
  loader.select(1); const a = loader.load(1);
  loader.select(2); const b = loader.load(2);
  pending[0].reject(new Error('Error de A')); await a;
  pending[1].reject(new Error('Error de B')); await b;
  assert.deepEqual(events.filter(e => e[0] === 'error'), [['error', 'Error de B']]);
});

test('A→B→A no acepta una respuesta de la primera visita a A', async () => {
  const { loader, pending, events } = fixture();
  loader.select(1); const oldA = loader.load(1);
  loader.select(2); const b = loader.load(2);
  loader.select(1); const newA = loader.load(1);
  pending[2].resolve('A nueva'); await newA;
  pending[0].resolve('A anterior'); pending[1].resolve('B'); await Promise.all([oldA, b]);
  assert.deepEqual(events.filter(e => e[0] === 'data'), [['data', 1, 'A nueva']]);
});

test('la última recarga de una misma organización gana', async () => {
  const { loader, pending, events } = fixture();
  loader.select(1); const old = loader.load(1); const latest = loader.load(1);
  pending[1].resolve('Nuevo'); await latest;
  pending[0].resolve('Anterior'); await old;
  assert.deepEqual(events.filter(e => e[0] === 'data'), [['data', 1, 'Nuevo']]);
});

test('logout o desmontaje descarta datos y errores pendientes', async () => {
  for (const fail of [false, true]) {
    const { loader, pending, events } = fixture();
    loader.select(1); const request = loader.load(1);
    loader.select(null);
    if (fail) pending[0].reject(new Error('Sesión anterior')); else pending[0].resolve('Sesión anterior');
    await request;
    assert.deepEqual(events, [['loading', true]]);
  }
});

test('una operación anterior no recarga otra selección ni reutiliza su alcance al volver', async () => {
  const { loader, pending } = fixture();
  loader.select(1); const isOriginalSelection = loader.scope(1);
  assert.equal(isOriginalSelection(), true);
  loader.select(2); await loader.load(1);
  assert.equal(pending.length, 0);
  assert.equal(isOriginalSelection(), false);
  loader.select(1);
  assert.equal(isOriginalSelection(), false);
});
