// La navegación invalida efectos de UI, sin cancelar escrituras ya enviadas a Java.
export function createCalendarOperations(onBusy: (busy: boolean) => void) {
  let active = true;
  let generation = 0;

  return {
    activate() { active = true; },
    dispose() { active = false; generation++; },
    invalidate() {
      generation++;
      if (active) onBusy(false);
    },
    async run<T>(work: () => Promise<T>, onSuccess: (value: T) => void, onError: (cause: unknown) => void) {
      if (!active) return;
      const request = ++generation;
      const isCurrent = () => active && request === generation;
      onBusy(true);
      try {
        const value = await work();
        if (isCurrent()) onSuccess(value);
      } catch (cause) {
        if (isCurrent()) onError(cause);
      } finally {
        if (isCurrent()) onBusy(false);
      }
    },
  };
}
