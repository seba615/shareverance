import { FormEvent, useEffect, useState } from 'react';
import { analyticsApi, AnalyticsResponse, ApiError, Category } from './api/java';
import { analyticsWindowError, createAnalyticsLoader, defaultAnalyticsWindow, formatMinor, formatAnalyticsMonth as formatMonthLabel } from './analyticsModel';
import { buenosAiresMonth, shiftMonth } from './calendarModel';

interface Props { organizationId: number; categories: Category[]; refreshToken: number }

export function AnalyticsPanel({ organizationId, categories, refreshToken }: Props) {
  const [range, setRange] = useState(defaultAnalyticsWindow);
  const [draft, setDraft] = useState(range);
  const [validation, setValidation] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [result, setResult] = useState<{ key: string; data: AnalyticsResponse | null; error: string } | null>(null);
  const [loader] = useState(() => createAnalyticsLoader(analyticsApi.monthly, (key, data, error) => {
    const message = error instanceof ApiError && error.status === 401 ? 'La sesión venció. Iniciá sesión nuevamente.'
      : error instanceof ApiError && error.status === 403 ? 'No tenés acceso a la analítica de esta organización.'
      : error ? 'No se pudo consultar la analítica. Intentá nuevamente.' : '';
    setResult({ key, data, error: message });
  }));
  const key = JSON.stringify([organizationId, range, refreshToken, refresh]);
  const active = result?.key === key ? result : null;
  const data = active?.data;
  useEffect(() => {
    void loader.load(key, organizationId, range.fromMonth, range.toMonth);
    return () => loader.invalidate();
  }, [key, loader]);

  function apply(event: FormEvent) {
    event.preventDefault();
    const error = analyticsWindowError(draft.fromMonth, draft.toMonth);
    setValidation(error);
    if (!error) { setRange({ ...draft }); setRefresh(value => value + 1); }
  }
  const categoryName = (id: number) => {
    const category = categories.find(item => item.id === id);
    return category ? `${category.name}${category.isActive ? '' : ' (inactiva)'}` : `Categoría #${id}`;
  };
  const last = data?.evolution.at(-1);
  const monthCategories = last?.covered ? last.category_totals : [];
  const maxEvolution = Math.max(1, ...(data?.evolution.map(month => month.total_amount_minor ?? 0) ?? []));
  const maxCategory = Math.max(1, ...monthCategories.map(item => item.amount_minor));
  const percentage = (value: number | null) => value === null ? 'No disponible'
    : `${new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 }).format(value)} %`;

  return <section className="analytics-panel" aria-label="Panel analítico">
    <h3>Panel de gastos de la organización</h3>
    <p className="muted">Importes en ARS. Gastos vigentes por fecha del gasto; no representa deudas ni pagos.</p>
    <form className="analytics-filters" onSubmit={apply}>
      <label>Desde<input type="month" required min="0001-01" max={draft.toMonth} value={draft.fromMonth}
        onChange={event => { setDraft({ ...draft, fromMonth: event.target.value }); setValidation(''); }} /></label>
      <label>Hasta<input type="month" required min={draft.fromMonth} max={shiftMonth(buenosAiresMonth(), -1)} value={draft.toMonth}
        onChange={event => { setDraft({ ...draft, toMonth: event.target.value }); setValidation(''); }} /></label>
      <button className="primary-button" type="submit">Consultar</button>
      <button className="secondary-button" type="button" onClick={() => setRefresh(value => value + 1)}>Actualizar</button>
    </form>
    {validation && <p role="alert">{validation}</p>}
    {!active && <p role="status">Cargando analítica…</p>}
    {active?.error && <p role="alert">{active.error}</p>}
    {data && <>
      <p>Período consultado: {formatMonthLabel(range.fromMonth)} a {formatMonthLabel(range.toMonth)}.</p>
      {data.status === 'INSUFFICIENT_HISTORY' && <p role="status">Historial insuficiente para comparar los dos últimos meses del período. Un mes sin cobertura confirmada no equivale a gasto cero.</p>}
      <div className="analytics-summary">
        <div><span>Total de {formatMonthLabel(range.toMonth)}</span><strong>{last?.covered ? formatMinor(last.total_amount_minor) : 'Sin cobertura confirmada'}</strong></div>
        <div><span>Variación mensual</span><strong>{formatMinor(data.organization.change_amount_minor)}</strong></div>
        <div><span>Variación porcentual</span><strong>{percentage(data.organization.change_percentage)}</strong></div>
      </div>
      {data.comparison && <p>Comparación: {formatMonthLabel(data.comparison.previous_month)} → {formatMonthLabel(data.comparison.current_month)}. Base anterior: {formatMinor(data.organization.previous_amount_minor)}.</p>}
      {data.organization.status === 'OK' && data.organization.previous_amount_minor === 0 && <p>El porcentaje no está disponible porque el mes anterior tiene gasto cero confirmado.</p>}

      <h4>Evolución mensual</h4>
      <p className="muted">Los meses sin cobertura aparecen en la tabla y no se dibujan como barras de cero.</p>
      <div className="analytics-chart-scroll" tabIndex={0} role="region" aria-label="Gráfico mensual desplazable"><svg className="analytics-chart" style={{ minWidth: Math.max(360, data.evolution.length * 60) }} viewBox={`0 0 ${Math.max(360, data.evolution.length * 60)} 210`} role="img" aria-label="Evolución del gasto mensual; valores exactos y cobertura en la tabla siguiente">
        {data.evolution.map((month, index) => <g key={month.month}>
          <title>{formatMonthLabel(month.month)}: {month.covered ? formatMinor(month.total_amount_minor) : 'Sin cobertura confirmada'}</title>
          {month.covered && <rect x={index * 60 + 12} y={170 - (month.total_amount_minor ?? 0) / maxEvolution * 145}
            width="30" height={Math.max(1, (month.total_amount_minor ?? 0) / maxEvolution * 145)} fill="#2563eb" />}
          <text x={index * 60 + 27} y="195" textAnchor="middle">{month.month.slice(2)}</text>
        </g>)}
      </svg></div>
      <div className="analytics-table-scroll" tabIndex={0} role="region" aria-label="Tabla mensual desplazable"><table>
        <caption>Evolución mensual en ARS</caption>
        <thead><tr><th scope="col">Mes</th><th scope="col">Cobertura</th><th scope="col">Total</th></tr></thead>
        <tbody>{data.evolution.map(month => <tr key={month.month}><th scope="row">{formatMonthLabel(month.month)}</th>
          <td>{month.covered ? 'Confirmada' : 'Sin confirmar'}</td><td>{month.covered ? formatMinor(month.total_amount_minor) : 'No disponible'}</td></tr>)}</tbody>
      </table></div>

      <h4>Categorías de {formatMonthLabel(range.toMonth)}</h4>
      {!last?.covered ? <p>Sin cobertura confirmada para mostrar categorías de este mes.</p>
        : !monthCategories.some(item => item.amount_minor > 0) ? <p>Mes confirmado sin gastos vigentes.</p> :
        <div className="analytics-chart-scroll" tabIndex={0} role="region" aria-label="Gráfico de categorías desplazable"><svg className="analytics-chart analytics-category-chart" viewBox={`0 0 600 ${monthCategories.length * 66 + 10}`} role="img" aria-label="Gasto por categoría; nombres e importes exactos en la tabla siguiente">
          {monthCategories.map((item, index) => <g key={item.category_id}>
            <title>{categoryName(item.category_id)}: {formatMinor(item.amount_minor)}</title>
            <text x="8" y={index * 66 + 20}>{categoryName(item.category_id).length > 70
              ? `${categoryName(item.category_id).slice(0, 67)}…` : categoryName(item.category_id)}</text>
            <rect x="8" y={index * 66 + 30} width={item.amount_minor / maxCategory * 580} height="24" fill="#0f766e" />
          </g>)}
        </svg></div>}
      <div className="analytics-table-scroll" tabIndex={0} role="region" aria-label="Tabla de categorías desplazable"><table>
        <caption>Categorías en ARS: total del último mes y comparación mensual</caption>
        <thead><tr><th scope="col">Categoría</th><th scope="col">Mes anterior</th><th scope="col">Último mes</th><th scope="col">Variación</th><th scope="col">Porcentaje</th></tr></thead>
        <tbody>{data.categories.map(category => <tr key={category.category_id}>
          <th scope="row">{categoryName(category.category_id)}</th>
          <td>{formatMinor(category.previous_amount_minor)}</td>
          <td>{formatMinor(last?.covered ? last.category_totals.find(item => item.category_id === category.category_id)?.amount_minor ?? 0 : null)}</td>
          <td>{formatMinor(category.change_amount_minor)}</td><td>{percentage(category.change_percentage)}</td>
        </tr>)}</tbody>
      </table></div>
      {data.categories.length === 0 && <p>No hay categorías con gastos en los meses confirmados del período.</p>}
    </>}
  </section>;
}
