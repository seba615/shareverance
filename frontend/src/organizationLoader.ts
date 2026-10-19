interface OrganizationLoaderOptions<T> {
  fetchData: (organizationId: number) => Promise<T>;
  onData: (organizationId: number, data: T) => void;
  onError: (error: unknown) => void;
  onLoading: (loading: boolean) => void;
}

// Una selección invalida también errores y recargas de operaciones anteriores.
export function createOrganizationLoader<T>(options: OrganizationLoaderOptions<T>) {
  let selectedId: number | null = null;
  let selection = 0;
  let request = 0;

  return {
    select(organizationId: number | null) {
      selectedId = organizationId;
      selection++;
      request++;
    },
    scope(organizationId: number) {
      const currentSelection = selection;
      return () => selectedId === organizationId && selection === currentSelection;
    },
    async load(organizationId: number) {
      if (selectedId !== organizationId) return;
      const currentRequest = ++request;
      const isCurrent = () => request === currentRequest && selectedId === organizationId;
      options.onLoading(true);
      try {
        const data = await options.fetchData(organizationId);
        if (isCurrent()) options.onData(organizationId, data);
      } catch (error) {
        if (isCurrent()) options.onError(error);
      } finally {
        if (isCurrent()) options.onLoading(false);
      }
    },
  };
}
