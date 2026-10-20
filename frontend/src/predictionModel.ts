import { formatMinor } from './analyticsModel.ts';

export { formatMinor };

export function validatePredictionTargetMonth(targetMonth: string): string {
  const trimmed = targetMonth.trim();
  if (!trimmed) return '';
  const valid = /^[0-9]{4}-(0[1-9]|1[0-2])$/.test(trimmed) && !trimmed.startsWith('0000');
  if (!valid) {
    return 'Ingresá un mes válido con formato YYYY-MM (años 0001 a 9999).';
  }
  return '';
}

export function formatPredictionMonth(month: string): string {
  if (!/^[0-9]{4}-(0[1-9]|1[0-2])$/.test(month)) return month;
  const date = new Date('2000-01-15T12:00:00Z');
  date.setUTCFullYear(Number(month.slice(0, 4)), Number(month.slice(5)) - 1, 15);
  return new Intl.DateTimeFormat('es-AR', {
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(date);
}

export function formatPredictionMethod(method: string, simulated: boolean): string {
  if (simulated) {
    return 'Simulado (mock)';
  }
  if (method === 'simple_exponential_smoothing') {
    return 'Suavizado Exponencial Simple (SES)';
  }
  if (method === 'mock') {
    return 'Simulado (mock)';
  }
  return method;
}

export function createPredictionLoader<T>(
  fetchData: (organizationId: number, targetMonth?: string) => Promise<T>,
  publish: (key: string, data: T | null, error: unknown | null) => void,
) {
  let generation = 0;
  return {
    invalidate() {
      generation++;
    },
    async load(key: string, organizationId: number, targetMonth?: string) {
      const request = ++generation;
      try {
        const data = await fetchData(organizationId, targetMonth);
        if (generation === request) {
          publish(key, data, null);
        }
      } catch (error) {
        if (generation === request) {
          publish(key, null, error);
        }
      }
    },
  };
}
