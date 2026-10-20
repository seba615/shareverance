import { buenosAiresMonth, shiftMonth } from './calendarModel.ts';

export function defaultAnalyticsWindow(now = new Date()) {
  const toMonth = shiftMonth(buenosAiresMonth(now), -1);
  return { fromMonth: shiftMonth(toMonth, -5), toMonth };
}

export function analyticsWindowError(from: string, to: string, current = buenosAiresMonth()): string {
  const valid = (month: string) => /^[0-9]{4}-(0[1-9]|1[0-2])$/.test(month) && !month.startsWith('0000');
  if (!valid(from) || !valid(to)) return 'Elegí ambos meses con formato válido.';
  const index = (month: string) => Number(month.slice(0, 4)) * 12 + Number(month.slice(5));
  const length = index(to) - index(from) + 1;
  if (length < 1 || length > 60) return 'Elegí un período ordenado de hasta 60 meses.';
  if (to >= current) return 'La analítica requiere meses cerrados.';
  return '';
}

// Formato exacto desde centavos enteros seguros, incluso cerca del límite JSON.
export function formatMinor(value: number | null): string {
  if (value === null) return 'No disponible';
  const minor = BigInt(value);
  const magnitude = minor < 0n ? -minor : minor;
  const pesos = new Intl.NumberFormat('es-AR').format(magnitude / 100n);
  return `${minor < 0n ? '−' : ''}$ ${pesos},${String(magnitude % 100n).padStart(2, '0')}`;
}

export function formatAnalyticsMonth(month: string): string {
  const date = new Date('2000-01-15T12:00:00Z');
  date.setUTCFullYear(Number(month.slice(0, 4)), Number(month.slice(5)) - 1, 15);
  return new Intl.DateTimeFormat('es-AR', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(date);
}

export function createAnalyticsLoader<T>(fetchData: (org: number, from: string, to: string) => Promise<T>,
  publish: (key: string, data: T | null, error: unknown | null) => void) {
  let generation = 0;
  return {
    invalidate() { generation++; },
    async load(key: string, org: number, from: string, to: string) {
      const request = ++generation;
      try {
        const data = await fetchData(org, from, to);
        if (generation === request) publish(key, data, null);
      } catch (error) {
        if (generation === request) publish(key, null, error);
      }
    },
  };
}
