import { FormEvent, useEffect, useRef, useState } from 'react';
import {
  ApiError,
  Category,
  ExpenseCalendar,
  ExpenseDetailResponse,
  ExpensePage,
  UserOrganization,
  expenseHistoryApi,
} from './api/java';
import {
  WEEKDAY_LABELS,
  buenosAiresMonth,
  buenosAiresToday,
  buildCalendarCells,
  canNavigateNextMonth,
  formatMonthLabel,
  resolveSelectedDateForMonth,
  shiftMonth,
} from './calendarModel';

const money = (value: number) =>
  new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' }).format(value);
const message = (cause: unknown) =>
  cause instanceof Error ? cause.message : 'No se pudo completar la operación';

interface Props {
  organizationId: number;
  userId: number;
  isAdmin: boolean;
  members: UserOrganization[];
  categories: Category[];
  refreshToken: number;
}

export function ExpenseCalendarView({
  organizationId,
  userId,
  isAdmin,
  members,
  categories,
  refreshToken,
}: Props) {
  const currentMonth = buenosAiresMonth();
  const todayDate = buenosAiresToday();
  const [month, setMonth] = useState(currentMonth);
  const [selectedDate, setSelectedDate] = useState(() =>
    resolveSelectedDateForMonth(currentMonth, currentMonth, todayDate, todayDate),
  );
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [refresh, setRefresh] = useState(0);
  const [calendarResult, setCalendarResult] = useState<{ key: string; data: ExpenseCalendar } | null>(null);
  const [dayResult, setDayResult] = useState<{ key: string; data: ExpensePage } | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [detail, setDetail] = useState<ExpenseDetailResponse | null>(null);
  const [editing, setEditing] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [confirmAnnul, setConfirmAnnul] = useState(false);
  const [busy, setBusy] = useState(false);

  const detailRequest = useRef(0);
  const mounted = useRef(true);

  const calendarKey = JSON.stringify([organizationId, month, refresh, refreshToken]);
  const dayKey = JSON.stringify([organizationId, selectedDate, page, size, refresh, refreshToken]);
  const calendarData = calendarResult?.key === calendarKey ? calendarResult.data : null;
  const dayData = dayResult?.key === dayKey ? dayResult.data : null;

  const memberName = (id: number) => members.find(m => m.id === id)?.userName ?? `Membresía #${id}`;
  const categoryName = (id: number) => categories.find(c => c.id === id)?.name ?? `Categoría #${id}`;

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      detailRequest.current++;
    };
  }, []);

  useEffect(() => {
    let current = true;
    setError('');
    expenseHistoryApi
      .calendar(organizationId, month)
      .then(response => {
        if (!current) return;
        setCalendarResult({ key: calendarKey, data: response });
      })
      .catch(cause => {
        if (current) setError(message(cause));
      });
    return () => {
      current = false;
    };
  }, [calendarKey]);

  useEffect(() => {
    let current = true;
    setError('');
    expenseHistoryApi
      .page(
        organizationId,
        { fromDate: selectedDate, toDate: selectedDate, state: 'ACTIVE' },
        page,
        size,
      )
      .then(response => {
        if (!current) return;
        if (page > 0 && page >= response.totalPages) {
          setPage(Math.max(0, response.totalPages - 1));
          return;
        }
        setDayResult({ key: dayKey, data: response });
      })
      .catch(cause => {
        if (current) setError(message(cause));
      });
    return () => {
      current = false;
    };
  }, [dayKey]);

  function changeMonth(nextMonth: string) {
    if (nextMonth > currentMonth) return;
    detailRequest.current++;
    setDetail(null);
    setEditing(false);
    setConfirmAnnul(false);
    setConflict(false);
    setNotice('');
    setPage(0);
    setMonth(nextMonth);
    setSelectedDate(curr =>
      resolveSelectedDateForMonth(nextMonth, currentMonth, todayDate, curr),
    );
  }

  function selectDay(date: string) {
    if (date > todayDate) return;
    detailRequest.current++;
    setDetail(null);
    setEditing(false);
    setConfirmAnnul(false);
    setConflict(false);
    setNotice('');
    setPage(0);
    setSelectedDate(date);
  }

  async function showDetail(id: number) {
    const request = ++detailRequest.current;
    setError('');
    setBusy(true);
    try {
      const response = await expenseHistoryApi.detail(id);
      if (!mounted.current || request !== detailRequest.current) return;
      setDetail(response);
      setEditing(false);
      setConflict(false);
      setConfirmAnnul(false);
    } catch (cause) {
      if (mounted.current && request === detailRequest.current) setError(message(cause));
    } finally {
      if (mounted.current && request === detailRequest.current) setBusy(false);
    }
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!detail || conflict) return;
    const form = new FormData(event.currentTarget);
    const amount = String(form.get('amount') ?? '').trim();
    const description = String(form.get('description') ?? '').trim();
    const nextDate = String(form.get('expenseDate') ?? '');
    if (!/^\d+(\.\d{1,2})?$/.test(amount) || Number(amount) <= 0 || Number(amount) > 9999999999.99) {
      setError('El importe debe ser positivo, hasta 9.999.999.999,99 y con hasta dos decimales');
      return;
    }
    if (!description || description.length > 500) {
      setError('La descripción es obligatoria y admite hasta 500 caracteres');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const updated = await expenseHistoryApi.update(detail.expense.id, {
        amount,
        description,
        categoryId: Number(form.get('categoryId')),
        expenseDate: nextDate,
        version: detail.expense.version,
      });
      if (!mounted.current) return;
      setDetail(updated);
      setEditing(false);
      setNotice('Gasto actualizado');
      const updatedMonth = updated.expense.expenseDate.slice(0, 7);
      if (updatedMonth <= currentMonth) {
        setMonth(updatedMonth);
        setSelectedDate(updated.expense.expenseDate);
      }
      setRefresh(value => value + 1);
    } catch (cause) {
      if (!mounted.current) return;
      if (cause instanceof ApiError && cause.status === 409) {
        setConflict(true);
        setError('El gasto cambió en otra operación. Recargá el detalle antes de volver a editar.');
      } else {
        setError(message(cause));
      }
    } finally {
      if (mounted.current) setBusy(false);
    }
  }

  async function annul() {
    if (!detail) return;
    setBusy(true);
    setError('');
    try {
      await expenseHistoryApi.annul(detail.expense.id);
      if (!mounted.current) return;
      setDetail(null);
      setConfirmAnnul(false);
      setNotice('Gasto anulado');
      setRefresh(value => value + 1);
    } catch (cause) {
      if (mounted.current) setError(message(cause));
    } finally {
      if (mounted.current) setBusy(false);
    }
  }

  const cells = buildCalendarCells(month, calendarData?.days ?? [], todayDate);
  const canModify =
    detail && detail.expense.isActive && (isAdmin || detail.expense.createdByUserId === userId);

  return (
    <section aria-label="Calendario de gastos">
      <div className="calendar-toolbar">
        <div>
          <h4 className="calendar-title">{formatMonthLabel(month)}</h4>
          {calendarData ? (
            <p className="muted calendar-summary">
              {calendarData.totalExpenses} gasto(s) vigente(s) en el mes · Total mensual:{' '}
              <strong>{money(calendarData.totalAmount)}</strong>
            </p>
          ) : (
            <p className="muted calendar-summary" role="status">
              Cargando resumen mensual…
            </p>
          )}
        </div>
        <div className="calendar-nav" role="group" aria-label="Navegación de mes">
          <button
            type="button"
            className="secondary-button"
            onClick={() => changeMonth(shiftMonth(month, -1))}
          >
            ← Mes anterior
          </button>
          <button
            type="button"
            className="secondary-button"
            disabled={month === currentMonth}
            onClick={() => changeMonth(currentMonth)}
          >
            Mes actual
          </button>
          <button
            type="button"
            className="secondary-button"
            disabled={!canNavigateNextMonth(month, currentMonth)}
            onClick={() => changeMonth(shiftMonth(month, 1))}
          >
            Mes siguiente →
          </button>
        </div>
      </div>

      {error && (
        <p className="notice error" role="alert">
          {error}
        </p>
      )}
      {notice && <p role="status">{notice}</p>}

      <div className="calendar-grid" role="grid" aria-label={`Días de ${formatMonthLabel(month)}`}>
        {WEEKDAY_LABELS.map(dayLabel => (
          <div key={dayLabel} className="calendar-weekday" role="columnheader">
            {dayLabel}
          </div>
        ))}
        {cells.map(cell => {
          if (!cell.date) {
            return <div key={cell.key} className="calendar-cell empty" aria-hidden="true" />;
          }
          const isSelected = cell.date === selectedDate;
          const hasExpenses = cell.expenseCount > 0;
          return (
            <button
              key={cell.key}
              type="button"
              disabled={cell.isFuture}
              aria-pressed={isSelected}
              aria-label={`${cell.date}${hasExpenses ? `, ${cell.expenseCount} gasto(s), ${money(cell.totalAmount)}` : ', sin gastos'}`}
              className={`calendar-cell${isSelected ? ' selected' : ''}${hasExpenses ? ' has-expenses' : ''}${cell.isToday ? ' today' : ''}`}
              onClick={() => selectDay(cell.date!)}
            >
              <span className="calendar-day-number">
                {cell.dayOfMonth}
                {cell.isToday && <span className="calendar-today-badge">Hoy</span>}
              </span>
              <span className="calendar-day-amount">
                {hasExpenses ? money(cell.totalAmount) : '—'}
              </span>
              <span className="calendar-day-count">
                {hasExpenses ? `${cell.expenseCount} gasto(s)` : cell.isFuture ? 'Futuro' : 'Sin gastos'}
              </span>
            </button>
          );
        })}
      </div>

      <section className="calendar-day-panel panel" aria-label={`Gastos del ${selectedDate}`}>
        <div className="tab-pane-header">
          <h4>Gastos vigentes del {selectedDate}</h4>
          {dayData && (
            <span className="muted">
              {dayData.totalElements} gasto(s) · Total del día: <strong>{money(dayData.totalAmount)}</strong>
            </span>
          )}
        </div>

        {!dayData && !error && <p role="status">Cargando gastos del día…</p>}
        {dayData && (
          <>
            {dayData.items.length === 0 ? (
              <p className="muted">No hay gastos vigentes registrados en esta fecha.</p>
            ) : (
              <div className="table-wrapper">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Fecha</th>
                      <th>Descripción</th>
                      <th>Categoría</th>
                      <th>Titular</th>
                      <th>Importe</th>
                      <th>Acciones</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dayData.items.map(({ expense }) => (
                      <tr key={expense.id}>
                        <td>{expense.expenseDate}</td>
                        <td>{expense.description}</td>
                        <td>{categoryName(expense.categoryId)}</td>
                        <td>{memberName(expense.paidByMembershipId)}</td>
                        <td>{money(expense.amount)}</td>
                        <td>
                          <button
                            type="button"
                            className="text-button"
                            disabled={busy}
                            onClick={() => showDetail(expense.id)}
                          >
                            Ver detalle
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {dayData.totalPages > 1 && (
              <nav className="history-pagination" aria-label="Páginas del día">
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage(value => value - 1)}
                >
                  Anterior
                </button>
                <span>
                  Página {page + 1} de {dayData.totalPages}
                </span>
                <button
                  type="button"
                  disabled={page + 1 >= dayData.totalPages}
                  onClick={() => setPage(value => value + 1)}
                >
                  Siguiente
                </button>
                <label>
                  Por página
                  <select
                    value={size}
                    onChange={event => {
                      setPage(0);
                      setSize(Number(event.target.value));
                    }}
                  >
                    {[10, 20, 50].map(value => (
                      <option key={value}>{value}</option>
                    ))}
                  </select>
                </label>
              </nav>
            )}
          </>
        )}

        {detail && (
          <section className="expense-detail panel" aria-label="Detalle del gasto">
            <h4>Detalle: {detail.expense.description}</h4>
            <p>
              {detail.expense.expenseDate} · {categoryName(detail.expense.categoryId)} ·{' '}
              {money(detail.expense.amount)} · {detail.expense.isActive ? 'Vigente' : 'Anulado'}
            </p>
            <p>
              Titular: {memberName(detail.expense.paidByMembershipId)} · Autor:{' '}
              {members.find(m => m.userId === detail.expense.createdByUserId)?.userName ??
                `Usuario #${detail.expense.createdByUserId}`}
            </p>
            <p>
              Versión: {detail.expense.version}. Participación asignada, sin representar deuda ni pago.
            </p>
            <ul>
              {detail.splits.map(part => (
                <li key={part.id}>
                  {memberName(part.userOrganizationId)}: {money(part.amount)}
                </li>
              ))}
            </ul>
            {detail.expense.receiptUrl && <p>Comprobante registrado; descarga privada pendiente.</p>}
            {canModify && (
              <div className="history-actions">
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => {
                    setEditing(value => !value);
                    setConfirmAnnul(false);
                  }}
                >
                  Editar
                </button>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => {
                    setConfirmAnnul(true);
                    setEditing(false);
                  }}
                >
                  Anular
                </button>
              </div>
            )}
            {editing && (
              <form className="form-stack" key={detail.expense.version} onSubmit={save}>
                <label>
                  Importe ARS
                  <input
                    name="amount"
                    type="text"
                    inputMode="decimal"
                    defaultValue={String(detail.expense.amount)}
                    required
                  />
                </label>
                <label>
                  Fecha
                  <input
                    name="expenseDate"
                    type="date"
                    max={todayDate}
                    defaultValue={detail.expense.expenseDate}
                    required
                  />
                </label>
                <label>
                  Descripción
                  <input
                    name="description"
                    maxLength={500}
                    defaultValue={detail.expense.description}
                    required
                  />
                </label>
                <label>
                  Categoría
                  <select name="categoryId" defaultValue={detail.expense.categoryId}>
                    {categories
                      .filter(c => c.isActive || c.id === detail.expense.categoryId)
                      .map(c => (
                        <option key={c.id} value={c.id}>
                          {c.name}
                          {!c.isActive ? ' (inactiva)' : ''}
                        </option>
                      ))}
                  </select>
                </label>
                <p>
                  Se conservan titular y participantes históricos; el cambio de importe recalcula sus partes.
                </p>
                <button type="submit" disabled={busy || conflict}>
                  Guardar cambios
                </button>
              </form>
            )}
            {conflict && (
              <button type="button" disabled={busy} onClick={() => showDetail(detail.expense.id)}>
                Recargar detalle y descartar edición local
              </button>
            )}
            {confirmAnnul && (
              <div role="group" aria-label="Confirmación de anulación">
                <p>¿Confirmás anular este gasto? Se conserva el historial y no se puede reactivar.</p>
                <button type="button" disabled={busy} onClick={annul}>
                  Confirmar anulación
                </button>
                <button type="button" disabled={busy} onClick={() => setConfirmAnnul(false)}>
                  Cancelar
                </button>
              </div>
            )}
            <button
              type="button"
              disabled={busy}
              onClick={() => {
                detailRequest.current++;
                setDetail(null);
                setEditing(false);
                setConfirmAnnul(false);
              }}
            >
              Cerrar detalle
            </button>
          </section>
        )}
      </section>
    </section>
  );
}
