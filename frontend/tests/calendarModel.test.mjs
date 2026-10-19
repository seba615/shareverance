import assert from 'node:assert/strict';
import test from 'node:test';
import {
  buenosAiresMonth,
  buenosAiresToday,
  buildCalendarCells,
  canNavigateNextMonth,
  resolveSelectedDateForMonth,
  shiftMonth,
} from '../src/calendarModel.ts';

test('buenosAiresToday y buenosAiresMonth respetan el huso America/Argentina/Buenos_Aires', () => {
  // 2026-10-01T01:30:00Z son las 22:30 del 2026-09-30 en Buenos Aires (UTC-3)
  const instant = new Date('2026-10-01T01:30:00.000Z');
  assert.equal(buenosAiresToday(instant), '2026-09-30');
  assert.equal(buenosAiresMonth(instant), '2026-09');
});

test('shiftMonth navega correctamente entre años y meses y bloquea meses futuros', () => {
  assert.equal(shiftMonth('2026-01', -1), '2025-12');
  assert.equal(shiftMonth('2025-12', 1), '2026-01');
  assert.equal(shiftMonth('2026-10', -1), '2026-09');
  assert.equal(canNavigateNextMonth('2026-09', '2026-10'), true);
  assert.equal(canNavigateNextMonth('2026-10', '2026-10'), false);
});

test('resolveSelectedDateForMonth selecciona hoy en el mes actual o el día 01 en meses anteriores', () => {
  assert.equal(
    resolveSelectedDateForMonth('2026-10', '2026-10', '2026-10-19', null),
    '2026-10-19',
  );
  assert.equal(
    resolveSelectedDateForMonth('2026-09', '2026-10', '2026-10-19', '2026-10-19'),
    '2026-09-01',
  );
  assert.equal(
    resolveSelectedDateForMonth('2026-09', '2026-10', '2026-10-19', '2026-09-15'),
    '2026-09-15',
  );
  assert.equal(
    resolveSelectedDateForMonth('2026-10', '2026-10', '2026-10-19', '2026-10-25'),
    '2026-10-19',
  );
});

test('buildCalendarCells alinea lunes como primer día, mapea totales diarios y marca días futuros', () => {
  // Octubre 2026 empieza un jueves (índice 3 empezando en lunes: Lun=0, Mar=1, Mié=2, Jue=3)
  const cells = buildCalendarCells(
    '2026-10',
    [
      { date: '2026-10-01', totalAmount: 1500.5, expenseCount: 2 },
      { date: '2026-10-19', totalAmount: 3200, expenseCount: 1 },
    ],
    '2026-10-19',
  );

  assert.equal(cells.length % 7, 0);
  assert.equal(cells[0].date, null);
  assert.equal(cells[1].date, null);
  assert.equal(cells[2].date, null);
  assert.equal(cells[3].date, '2026-10-01');
  assert.equal(cells[3].totalAmount, 1500.5);
  assert.equal(cells[3].expenseCount, 2);
  assert.equal(cells[3].isFuture, false);

  const todayCell = cells.find(c => c.date === '2026-10-19');
  assert.ok(todayCell);
  assert.equal(todayCell.isToday, true);
  assert.equal(todayCell.isFuture, false);
  assert.equal(todayCell.totalAmount, 3200);

  const futureCell = cells.find(c => c.date === '2026-10-20');
  assert.ok(futureCell);
  assert.equal(futureCell.isFuture, true);
  assert.equal(futureCell.totalAmount, 0);
  assert.equal(futureCell.expenseCount, 0);
});
