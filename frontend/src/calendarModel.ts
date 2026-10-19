import type { ExpenseCalendarDay } from './api/java';

export interface CalendarCell {
  key: string;
  date: string | null;
  dayOfMonth: number | null;
  totalAmount: number;
  expenseCount: number;
  isFuture: boolean;
  isToday: boolean;
}

export const WEEKDAY_LABELS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'] as const;

export function buenosAiresToday(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Argentina/Buenos_Aires',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
}

export function buenosAiresMonth(now: Date = new Date()): string {
  return buenosAiresToday(now).slice(0, 7);
}

export function shiftMonth(month: string, delta: number): string {
  const [yearPart, monthPart] = month.split('-').map(Number);
  const totalMonths = yearPart * 12 + (monthPart - 1) + delta;
  const nextYear = Math.floor(totalMonths / 12);
  const nextMonth = (totalMonths % 12) + 1;
  return `${String(nextYear).padStart(4, '0')}-${String(nextMonth).padStart(2, '0')}`;
}

export function canNavigateNextMonth(month: string, currentMonth: string): boolean {
  return month < currentMonth;
}

export function formatMonthLabel(month: string): string {
  const [year, monthIndex] = month.split('-').map(Number);
  const utcDate = new Date(Date.UTC(year, monthIndex - 1, 15, 12, 0, 0));
  return new Intl.DateTimeFormat('es-AR', {
    timeZone: 'UTC',
    month: 'long',
    year: 'numeric',
  }).format(utcDate);
}

export function resolveSelectedDateForMonth(
  month: string,
  currentMonth: string,
  todayDate: string,
  currentSelectedDate: string | null,
): string {
  if (
    currentSelectedDate &&
    currentSelectedDate.startsWith(`${month}-`) &&
    currentSelectedDate <= todayDate
  ) {
    return currentSelectedDate;
  }
  return month === currentMonth ? todayDate : `${month}-01`;
}

export function buildCalendarCells(
  month: string,
  days: ExpenseCalendarDay[],
  todayDate: string,
): CalendarCell[] {
  const [year, monthNumber] = month.split('-').map(Number);
  const daysInMonth = new Date(Date.UTC(year, monthNumber, 0)).getUTCDate();
  const firstDayUtcWeekday = new Date(Date.UTC(year, monthNumber - 1, 1)).getUTCDay();
  const leadingEmptyCount = (firstDayUtcWeekday + 6) % 7;

  const totalsByDate = new Map<string, ExpenseCalendarDay>();
  for (const entry of days) {
    totalsByDate.set(entry.date, entry);
  }

  const cells: CalendarCell[] = [];
  for (let index = 0; index < leadingEmptyCount; index++) {
    cells.push({
      key: `${month}-pad-start-${index}`,
      date: null,
      dayOfMonth: null,
      totalAmount: 0,
      expenseCount: 0,
      isFuture: false,
      isToday: false,
    });
  }

  for (let day = 1; day <= daysInMonth; day++) {
    const date = `${month}-${String(day).padStart(2, '0')}`;
    const summary = totalsByDate.get(date);
    cells.push({
      key: date,
      date,
      dayOfMonth: day,
      totalAmount: summary ? Number(summary.totalAmount) : 0,
      expenseCount: summary ? Number(summary.expenseCount) : 0,
      isFuture: date > todayDate,
      isToday: date === todayDate,
    });
  }

  const trailingEmptyCount = (7 - (cells.length % 7)) % 7;
  for (let index = 0; index < trailingEmptyCount; index++) {
    cells.push({
      key: `${month}-pad-end-${index}`,
      date: null,
      dayOfMonth: null,
      totalAmount: 0,
      expenseCount: 0,
      isFuture: false,
      isToday: false,
    });
  }

  return cells;
}
