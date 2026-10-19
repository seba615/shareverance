import { FormEvent, useEffect, useRef, useState } from 'react';
import { ApiError, Category, ExpenseDetailResponse, ExpenseFilter, ExpensePage, UserOrganization, expenseHistoryApi } from './api/java';

const money = (value: number) => new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' }).format(value);
const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Argentina/Buenos_Aires' }).format(new Date());
const message = (cause: unknown) => cause instanceof Error ? cause.message : 'No se pudo completar la operación';

interface Props {
  organizationId: number;
  userId: number;
  isAdmin: boolean;
  members: UserOrganization[];
  categories: Category[];
  refreshToken: number;
}
export function ExpenseHistory({ organizationId, userId, isAdmin, members, categories, refreshToken }: Props) {
  const [filter, setFilter] = useState<ExpenseFilter>({ state: 'ACTIVE' });
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [refresh, setRefresh] = useState(0);
  const [result, setResult] = useState<{ key: string; data: ExpensePage } | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [detail, setDetail] = useState<ExpenseDetailResponse | null>(null);
  const [editing, setEditing] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [confirmAnnul, setConfirmAnnul] = useState(false);
  const [busy, setBusy] = useState(false);
  const detailRequest = useRef(0);
  const mounted = useRef(true);
  const key = JSON.stringify([organizationId, filter, page, size, refresh, refreshToken]);
  const data = result?.key === key ? result.data : null;
  const memberName = (id: number) => members.find(m => m.id === id)?.userName ?? `Membresía #${id}`;
  const categoryName = (id: number) => categories.find(c => c.id === id)?.name ?? `Categoría #${id}`;
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; detailRequest.current++; }; }, []);
  useEffect(() => {
    let current = true;
    setError('');
    expenseHistoryApi.page(organizationId, filter, page, size).then(response => {
      if (!current) return;
      if (page > 0 && page >= response.totalPages) { setPage(Math.max(0, response.totalPages - 1)); return; }
      setResult({ key, data: response });
    }).catch(cause => { if (current) setError(message(cause)); });
    return () => { current = false; };
  }, [key]);

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const fromDate = String(form.get('fromDate') ?? '');
    const toDate = String(form.get('toDate') ?? '');
    if (fromDate && toDate && fromDate > toDate) { setError('La fecha inicial no puede ser posterior a la final'); return; }
    setPage(0);
    setFilter({ fromDate, toDate, categoryId: String(form.get('categoryId') ?? ''), state: String(form.get('state')) as ExpenseFilter['state'] });
    setRefresh(value => value + 1);
  }
  async function showDetail(id: number) {
    const request = ++detailRequest.current;
    setError(''); setBusy(true);
    try {
      const response = await expenseHistoryApi.detail(id);
      if (!mounted.current || request !== detailRequest.current) return;
      setDetail(response); setEditing(false); setConflict(false); setConfirmAnnul(false);
    } catch (cause) { if (mounted.current && request === detailRequest.current) setError(message(cause)); }
    finally { if (mounted.current && request === detailRequest.current) setBusy(false); }
  }
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!detail || conflict) return;
    const form = new FormData(event.currentTarget);
    const amount = String(form.get('amount') ?? '').trim();
    const description = String(form.get('description') ?? '').trim();
    if (!/^\d+(\.\d{1,2})?$/.test(amount) || Number(amount) <= 0 || Number(amount) > 9999999999.99) {
      setError('El importe debe ser positivo, hasta 9.999.999.999,99 y con hasta dos decimales'); return;
    }
    if (!description || description.length > 500) { setError('La descripción es obligatoria y admite hasta 500 caracteres'); return; }
    setBusy(true); setError('');
    try {
      const updated = await expenseHistoryApi.update(detail.expense.id, { amount, description,
        categoryId: Number(form.get('categoryId')), expenseDate: String(form.get('expenseDate')), version: detail.expense.version });
      if (!mounted.current) return;
      setDetail(updated); setEditing(false); setNotice('Gasto actualizado'); setRefresh(value => value + 1);
    } catch (cause) {
      if (!mounted.current) return;
      if (cause instanceof ApiError && cause.status === 409) {
        setConflict(true); setError('El gasto cambió en otra operación. Recargá el detalle antes de volver a editar.');
      } else setError(message(cause));
    } finally { if (mounted.current) setBusy(false); }
  }
  async function annul() {
    if (!detail) return;
    setBusy(true); setError('');
    try {
      await expenseHistoryApi.annul(detail.expense.id);
      if (!mounted.current) return;
      setDetail(null); setConfirmAnnul(false); setNotice('Gasto anulado'); setRefresh(value => value + 1);
    } catch (cause) { if (mounted.current) setError(message(cause)); }
    finally { if (mounted.current) setBusy(false); }
  }
  const canModify = detail && detail.expense.isActive && (isAdmin || detail.expense.createdByUserId === userId);
  return <section aria-label="Historial de gastos">
    <h4>Historial</h4>
    <form className="form-grid history-filters" onSubmit={applyFilters}>
      <label>Desde<input type="date" name="fromDate" /></label>
      <label>Hasta<input type="date" name="toDate" /></label>
      <label>Categoría<select name="categoryId" defaultValue=""><option value="">Todas</option>{categories.map(c => <option key={c.id} value={c.id}>{c.name}{!c.isActive ? ' (inactiva)' : ''}</option>)}</select></label>
      <label>Estado<select name="state" defaultValue="ACTIVE"><option value="ACTIVE">Vigentes</option><option value="ANNULLED">Anulados</option><option value="ALL">Todos</option></select></label>
      <button type="submit" className="secondary-button">Aplicar filtros</button>
    </form>
    {error && <p className="notice error" role="alert">{error}</p>}
    {notice && <p role="status">{notice}</p>}
    {!data && !error && <p role="status">Cargando historial…</p>}
    {data && <>
      <p>{data.totalElements} gasto(s) · Total del filtro: {money(data.totalAmount)}</p>
      {data.items.length === 0 ? <p>No hay gastos para estos filtros.</p> : <div className="table-wrapper"><table className="data-table"><thead><tr><th>Fecha</th><th>Descripción</th><th>Categoría</th><th>Importe</th><th>Estado</th><th>Acciones</th></tr></thead><tbody>
        {data.items.map(({ expense }) => <tr key={expense.id}><td>{expense.expenseDate}</td><td>{expense.description}</td><td>{categoryName(expense.categoryId)}</td><td>{money(expense.amount)}</td><td>{expense.isActive ? 'Vigente' : 'Anulado'}</td><td><button className="text-button" disabled={busy} onClick={() => showDetail(expense.id)}>Ver detalle</button></td></tr>)}
      </tbody></table></div>}
      <nav className="history-pagination" aria-label="Páginas del historial"><button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Anterior</button><span>Página {data.totalPages === 0 ? 0 : page + 1} de {data.totalPages}</span><button disabled={page + 1 >= data.totalPages} onClick={() => setPage(value => value + 1)}>Siguiente</button><label>Por página<select value={size} onChange={event => { setPage(0); setSize(Number(event.target.value)); }}>{[10,20,50].map(value => <option key={value}>{value}</option>)}</select></label></nav>
    </>}
    {detail && <section className="expense-detail panel" aria-label="Detalle del gasto">
      <h4>Detalle: {detail.expense.description}</h4>
      <p>{detail.expense.expenseDate} · {categoryName(detail.expense.categoryId)} · {money(detail.expense.amount)} · {detail.expense.isActive ? 'Vigente' : 'Anulado'}</p>
      <p>Titular: {memberName(detail.expense.paidByMembershipId)} · Autor: {members.find(m => m.userId === detail.expense.createdByUserId)?.userName ?? `Usuario #${detail.expense.createdByUserId}`}</p>
      <p>Versión: {detail.expense.version}. Participación asignada, sin representar deuda ni pago.</p>
      <ul>{detail.splits.map(part => <li key={part.id}>{memberName(part.userOrganizationId)}: {money(part.amount)}</li>)}</ul>
      {detail.expense.receiptUrl && <p>Comprobante registrado; descarga privada pendiente.</p>}
      {canModify && <div className="history-actions"><button disabled={busy} onClick={() => { setEditing(value => !value); setConfirmAnnul(false); }}>Editar</button><button disabled={busy} onClick={() => { setConfirmAnnul(true); setEditing(false); }}>Anular</button></div>}
      {editing && <form className="form-stack" key={detail.expense.version} onSubmit={save}>
        <label>Importe ARS<input name="amount" type="text" inputMode="decimal" defaultValue={String(detail.expense.amount)} required /></label>
        <label>Fecha<input name="expenseDate" type="date" max={today()} defaultValue={detail.expense.expenseDate} required /></label>
        <label>Descripción<input name="description" maxLength={500} defaultValue={detail.expense.description} required /></label>
        <label>Categoría<select name="categoryId" defaultValue={detail.expense.categoryId}>{categories.filter(c => c.isActive || c.id === detail.expense.categoryId).map(c => <option key={c.id} value={c.id}>{c.name}{!c.isActive ? ' (inactiva)' : ''}</option>)}</select></label>
        <p>Se conservan titular y participantes históricos; el cambio de importe recalcula sus partes.</p>
        <button type="submit" disabled={busy || conflict}>Guardar cambios</button>
      </form>}
      {conflict && <button disabled={busy} onClick={() => showDetail(detail.expense.id)}>Recargar detalle y descartar edición local</button>}
      {confirmAnnul && <div role="group" aria-label="Confirmación de anulación"><p>¿Confirmás anular este gasto? Se conserva el historial y no se puede reactivar.</p><button disabled={busy} onClick={annul}>Confirmar anulación</button><button disabled={busy} onClick={() => setConfirmAnnul(false)}>Cancelar</button></div>}
      <button disabled={busy} onClick={() => { detailRequest.current++; setDetail(null); setEditing(false); setConfirmAnnul(false); }}>Cerrar detalle</button>
    </section>}
  </section>;
}
