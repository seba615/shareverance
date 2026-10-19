import assert from 'node:assert/strict';
import test from 'node:test';
import { createCalendarOperations } from '../src/calendarOperations.ts';

function deferred() {
  let resolve, reject;
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail; });
  return { promise, resolve, reject };
}

function fixture() {
  const events = [];
  const operations = createCalendarOperations(busy => events.push(['busy', busy]));
  function start(kind) {
    const pending = deferred();
    const done = operations.run(() => pending.promise,
      value => events.push([kind, value]),
      cause => events.push(['error', cause]));
    return { ...pending, done };
  }
  return { operations, events, start };
}

for (const outcome of ['resolve', 'reject']) {
  test(`detalle tardío (${outcome}) tras cambiar fecha libera controles y no altera la nueva selección`, async () => {
    const { operations, events, start } = fixture();
    const oldDetail = start('detail');
    operations.invalidate();
    assert.deepEqual(events, [['busy', true], ['busy', false]]);
    const newDetail = start('detail');
    const before = [...events];
    oldDetail[outcome](new Error('Detalle anterior'));
    await oldDetail.done;
    assert.deepEqual(events, before); // El finally anterior tampoco libera la operación nueva.
    newDetail.resolve('Detalle del día actual');
    await newDetail.done;
    assert.deepEqual(events.slice(-2), [['detail', 'Detalle del día actual'], ['busy', false]]);
  });

  for (const kind of ['update', 'annul']) {
    test(`${kind} tardío (${outcome}) tras navegar de mes no publica detalle, aviso, error ni recarga`, async () => {
      const { operations, events, start } = fixture();
      const writing = start(kind);
      operations.invalidate();
      const afterNavigation = [...events];
      writing[outcome]({ status: 409, expenseDate: '2026-09-15' });
      await writing.done;
      assert.deepEqual(events, afterNavigation);
    });
  }
}

test('A→B→A no recupera una edición de la primera visita', async () => {
  const { operations, events, start } = fixture();
  const oldWrite = start('update');
  operations.invalidate();
  operations.invalidate();
  const latest = start('detail');
  latest.resolve('A actual'); await latest.done;
  const before = [...events];
  oldWrite.resolve('A anterior'); await oldWrite.done;
  assert.deepEqual(events, before);
});

test('operación vigente aplica éxito o conflicto 409 y siempre libera controles', async () => {
  const { events, start } = fixture();
  const write = start('update');
  write.resolve('Gasto actualizado'); await write.done;
  assert.deepEqual(events.slice(-2), [['update', 'Gasto actualizado'], ['busy', false]]);
  const conflict = start('update');
  conflict.reject({ status: 409 }); await conflict.done;
  assert.deepEqual(events.slice(-2), [['error', { status: 409 }], ['busy', false]]);
});

test('desmontaje descarta respuestas sin actualizar estado, incluso tras reactivar la instancia', async () => {
  const { operations, events, start } = fixture();
  const previous = start('annul');
  operations.dispose();
  const before = [...events];
  const ignored = start('detail');
  await ignored.done;
  assert.deepEqual(events, before);
  operations.activate();
  const current = start('detail');
  previous.reject(new Error('Respuesta del montaje anterior')); await previous.done;
  assert.deepEqual(events, [...before, ['busy', true]]);
  current.resolve('Montaje actual'); await current.done;
  assert.deepEqual(events.slice(-2), [['detail', 'Montaje actual'], ['busy', false]]);
});
