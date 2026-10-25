import { FormEvent, useEffect, useState } from 'react';
import { ApiError, individualSummaryApi, type IndividualSummary, type UserOrganization } from './api/java';
import { createPredictionLoader } from './predictionModel';

const money = (amount: number) => new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' }).format(amount);

export function IndividualSummaryResults({ data, own = true }: { data: IndividualSummary; own?: boolean }) {
  return <>
    <p>Período consultado: {data.fromDate ?? 'Desde el primer gasto'} — {data.toDate ?? 'hasta el último gasto'}.</p>
    <div className="analytics-summary">
      <div><span>{own ? 'Mi participación total' : 'Participación total'}</span><strong>{money(data.totalAmount)}</strong></div>
      <div><span>{own ? 'Gastos en los que participo' : 'Gastos en los que participa'}</span><strong>{data.expenseCount}</strong></div>
    </div>
    {data.categories.length === 0 ? <p role="status">{own ? 'No tenés participación en gastos vigentes para este período.' : 'No hay participación en gastos vigentes para este período.'}</p> :
      <div className="analytics-table-scroll" tabIndex={0} role="region" aria-label="Resumen individual por categoría">
        <table><caption>{own ? 'Mi participación por categoría en ARS' : 'Participación del miembro por categoría en ARS'}</caption>
          <thead><tr><th scope="col">Categoría</th><th scope="col">Gastos</th><th scope="col">{own ? 'Mi participación' : 'Participación'}</th></tr></thead>
          <tbody>{data.categories.map(category => <tr key={category.categoryId}>
            <th scope="row">{category.categoryName}{!category.categoryActive && ' (inactiva)'}</th>
            <td>{category.expenseCount}</td><td>{money(category.amount)}</td>
          </tr>)}</tbody>
        </table>
      </div>}
  </>;
}

export function IndividualSummaryPanel({ organizationId, refreshToken, members, isAdmin, currentMembershipId }: {
  organizationId: number; refreshToken: number; members: UserOrganization[];
  isAdmin: boolean; currentMembershipId?: number;
}) {
  const [selectedMember, setSelectedMember] = useState('');
  const membershipId = isAdmin && selectedMember ? Number(selectedMember) : undefined;
  const own = membershipId === undefined || membershipId === currentMembershipId;
  const memberLabel = (m: UserOrganization) => `${m.userName || m.userEmail || `Miembro #${m.id}`}${m.userName && m.userEmail ? ` (${m.userEmail})` : ''}`;
  const selected = members.find(m => m.id === membershipId);
  const selectedName = selected ? memberLabel(selected) : 'miembro seleccionado';
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [range, setRange] = useState({ from: '', to: '' });
  const [refresh, setRefresh] = useState(0);
  const [validation, setValidation] = useState('');
  const [result, setResult] = useState<{key: string; data: IndividualSummary | null; error: string} | null>(null);
  const [loader] = useState(() => createPredictionLoader(individualSummaryApi.get, (key, data, error) => {
    const message = error instanceof ApiError && error.status === 401 ? 'La sesión venció. Iniciá sesión nuevamente.'
      : error instanceof ApiError && error.status === 403 ? 'No tenés acceso al resumen de esta organización.'
      : error ? 'No se pudo consultar tu resumen. Intentá nuevamente.' : '';
    setResult({ key, data, error: message });
  }));
  const key = JSON.stringify([organizationId, range.from, range.to, membershipId, refreshToken, refresh]);
  const active = result?.key === key ? result : null;
  useEffect(() => {
    void loader.load(key, organizationId, range.from || undefined, range.to || undefined, membershipId);
    return () => loader.invalidate();
  }, [loader, key, organizationId, range.from, range.to, membershipId]);
  function submit(event: FormEvent) {
    event.preventDefault();
    if (from && to && from > to) { setValidation('La fecha Desde no puede ser posterior a Hasta.'); return; }
    setValidation(''); setRange({ from, to }); setRefresh(value => value + 1);
  }
  return <section className="individual-summary" aria-label="Resumen individual">
    <h3>{own ? 'Mi resumen' : `Resumen de ${selectedName}`}</h3>
    <p className="muted">Las partes asignadas al miembro consultado en los gastos vigentes de esta organización. No representan pagos ni deudas.</p>
    <form className="analytics-filters" onSubmit={submit}>
      {isAdmin && <label>Miembro<select value={selectedMember} onChange={e => setSelectedMember(e.target.value)}>
        <option value="">Mi participación</option>
        {members.filter(m => m.id !== currentMembershipId).map(m => <option key={m.id} value={m.id}>
          {memberLabel(m)}{!m.isActive && ' (inactivo)'}
        </option>)}
      </select></label>}
      <label>Desde<input type="date" min="0001-01-01" max="9999-12-31" value={from} onChange={e => setFrom(e.target.value)} /></label>
      <label>Hasta<input type="date" min="0001-01-01" max="9999-12-31" value={to} onChange={e => setTo(e.target.value)} /></label>
      <button className="primary-button" type="submit">Consultar</button>
      <button className="secondary-button" type="button" onClick={() => {
        setFrom(''); setTo(''); setRange({ from: '', to: '' }); setValidation(''); setRefresh(v => v + 1);
      }}>Todo el historial</button>
    </form>
    {validation && <p role="alert">{validation}</p>}
    {!active && <p role="status">Consultando tu participación…</p>}
    {active?.error && <p role="alert">{active.error}</p>}
    {active?.data && <IndividualSummaryResults data={active.data} own={own} />}
  </section>;
}
