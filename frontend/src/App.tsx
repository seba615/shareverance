import { FormEvent, useEffect, useState } from 'react';
import {
  ApiError,
  Category,
  Organization,
  OrganizationType,
  SplitMode,
  User,
  UserOrganization,
  authApi,
  categoryApi,
  expenseApi,
  membershipApi,
  organizationApi,
} from './api/java';

import { ExpenseCalendarView } from './ExpenseCalendar';
import { ExpenseHistory } from './ExpenseHistory';
import { buenosAiresToday } from './calendarModel';
import { createOrganizationLoader } from './organizationLoader';

type AuthMode = 'login' | 'register';
type OrgTab = 'expenses' | 'members' | 'categories';
type ExpenseViewMode = 'history' | 'calendar';

const organizationTypeLabels: Record<OrganizationType, string> = {
  HOUSEHOLD: 'Hogar',
  CONDO: 'Consorcio',
  UNIVERSITY: 'Universidad',
};

function messageFrom(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'La sesión venció. Iniciá sesión nuevamente.';
    if (error.status === 403) return 'No tenés permisos para realizar esta acción.';
    return error.message;
  }
  return error instanceof Error ? error.message : 'Ocurrió un error inesperado.';
}

export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [selectedOrganizationId, setSelectedOrganizationId] = useState<number | null>(null);
  const [authMode, setAuthMode] = useState<AuthMode>('login');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [creatingOrganization, setCreatingOrganization] = useState(false);
  const [error, setError] = useState('');

  // Estados de datos de la organización activa
  const [activeTab, setActiveTab] = useState<OrgTab>('expenses');
  const [expenseView, setExpenseView] = useState<ExpenseViewMode>('history');
  const [organizationData, setOrganizationData] = useState<{
    organizationId: number;
    members: UserOrganization[];
    categories: Category[];
  } | null>(null);
  const activeData = organizationData?.organizationId === selectedOrganizationId ? organizationData : null;
  const members = activeData?.members ?? [];
  const categories = activeData?.categories ?? [];
  const [historyRefresh, setHistoryRefresh] = useState(0);
  const [loadingOrgData, setLoadingOrgData] = useState(false);

  // Estados de formularios secundarios
  const [showAddMember, setShowAddMember] = useState(false);
  const [showAddCategory, setShowAddCategory] = useState(false);
  const [showAddExpense, setShowAddExpense] = useState(false);

  // Estado del reparto de gasto
  const [splitMode, setSplitMode] = useState<SplitMode>('INDIVIDUAL');
  const [selectedMemberIds, setSelectedMemberIds] = useState<number[]>([]);

  const currentMembership = members.find(m => m.userId === user?.id && m.isActive);
  const isAdmin = currentMembership?.role === 'ADMIN';

  function getMemberLabel(m: UserOrganization): string {
    const isSelf = m.userId === user?.id;
    const name = m.userName ? m.userName : `Usuario #${m.userId}`;
    return `${name}${isSelf ? ' (Vos)' : ''}`;
  }

  const [organizationLoader] = useState(() => createOrganizationLoader({
    async fetchData(orgId: number) {
      const [members, categories] = await Promise.all([
        membershipApi.listByOrganization(orgId),
        categoryApi.listByOrganization(orgId),
      ]);
      return { members, categories };
    },
    onData: (organizationId, data) => { setOrganizationData({ organizationId, ...data }); setHistoryRefresh(value => value + 1); },
    onError: cause => setError(messageFrom(cause)),
    onLoading: setLoadingOrgData,
  }));
  const loadOrganizationData = organizationLoader.load;

  function selectOrganization(organizationId: number | null) {
    if (organizationId === selectedOrganizationId) return;
    // Invalidar inmediatamente: no esperar al siguiente efecto de React.
    organizationLoader.select(organizationId);
    setSelectedOrganizationId(organizationId);
    setOrganizationData(null);
    setLoadingOrgData(organizationId !== null);
    setError('');
    setShowAddMember(false);
    setShowAddCategory(false);
    setShowAddExpense(false);
    setSelectedMemberIds([]);
    setSplitMode('INDIVIDUAL');
    setActiveTab('expenses');
  }

  useEffect(() => {
    organizationLoader.select(selectedOrganizationId);
    if (selectedOrganizationId !== null) void loadOrganizationData(selectedOrganizationId);
    return () => organizationLoader.select(null);
  }, [selectedOrganizationId, organizationLoader]);

  async function handleAddMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedOrganizationId) return;
    const isCurrentOrganization = organizationLoader.scope(selectedOrganizationId);
    setBusy(true);
    setError('');
    const form = new FormData(event.currentTarget);
    const email = String(form.get('email') ?? '').trim();
    const role = (String(form.get('role') ?? 'USER')) as 'ADMIN' | 'USER';
    try {
      await membershipApi.addMember({ organizationId: selectedOrganizationId, email, role });
      if (!isCurrentOrganization()) return;
      setShowAddMember(false);
      await loadOrganizationData(selectedOrganizationId);
    } catch (cause) {
      if (isCurrentOrganization()) setError(messageFrom(cause));
    } finally {
      setBusy(false);
    }
  }

  async function handleToggleRole(m: UserOrganization) {
    if (!selectedOrganizationId) return;
    const isCurrentOrganization = organizationLoader.scope(selectedOrganizationId);
    setBusy(true);
    setError('');
    const newRole = m.role === 'ADMIN' ? 'USER' : 'ADMIN';
    try {
      await membershipApi.updateRole(m.id, newRole);
      if (!isCurrentOrganization()) return;
      await loadOrganizationData(selectedOrganizationId);
    } catch (cause) {
      if (isCurrentOrganization()) setError(messageFrom(cause));
    } finally {
      setBusy(false);
    }
  }

  async function handleDeactivateMember(memberId: number) {
    if (!selectedOrganizationId) return;
    const isCurrentOrganization = organizationLoader.scope(selectedOrganizationId);
    setBusy(true);
    setError('');
    try {
      await membershipApi.deactivate(memberId);
      if (!isCurrentOrganization()) return;
      await loadOrganizationData(selectedOrganizationId);
    } catch (cause) {
      if (isCurrentOrganization()) setError(messageFrom(cause));
    } finally {
      setBusy(false);
    }
  }

  async function handleReactivateMember(memberId: number) {
    if (!selectedOrganizationId) return;
    const isCurrentOrganization = organizationLoader.scope(selectedOrganizationId);
    setBusy(true);
    setError('');
    try {
      await membershipApi.reactivate(memberId);
      if (!isCurrentOrganization()) return;
      await loadOrganizationData(selectedOrganizationId);
    } catch (cause) {
      if (isCurrentOrganization()) setError(messageFrom(cause));
    } finally {
      setBusy(false);
    }
  }

  async function handleCreateCategory(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedOrganizationId) return;
    const isCurrentOrganization = organizationLoader.scope(selectedOrganizationId);
    setBusy(true);
    setError('');
    const form = new FormData(event.currentTarget);
    const name = String(form.get('name') ?? '').trim();
    const description = String(form.get('description') ?? '').trim() || undefined;
    try {
      await categoryApi.create(selectedOrganizationId, { name, description });
      if (!isCurrentOrganization()) return;
      setShowAddCategory(false);
      await loadOrganizationData(selectedOrganizationId);
    } catch (cause) {
      if (isCurrentOrganization()) setError(messageFrom(cause));
    } finally {
      setBusy(false);
    }
  }

  async function handleDeactivateCategory(categoryId: number) {
    if (!selectedOrganizationId) return;
    const isCurrentOrganization = organizationLoader.scope(selectedOrganizationId);
    setBusy(true);
    setError('');
    try {
      await categoryApi.deactivate(categoryId);
      if (!isCurrentOrganization()) return;
      await loadOrganizationData(selectedOrganizationId);
    } catch (cause) {
      if (isCurrentOrganization()) setError(messageFrom(cause));
    } finally {
      setBusy(false);
    }
  }

  async function handleCreateExpense(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedOrganizationId) return;
    const isCurrentOrganization = organizationLoader.scope(selectedOrganizationId);
    setBusy(true);
    setError('');
    const form = new FormData(event.currentTarget);
    const description = String(form.get('description') ?? '').trim();
    const amount = String(form.get('amount') ?? '').trim();
    const expenseDate = String(form.get('expenseDate') ?? '').trim();
    const categoryId = Number(form.get('categoryId'));
    const paidByMembershipId = form.get('paidByMembershipId')
      ? Number(form.get('paidByMembershipId'))
      : currentMembership?.id ?? 0;

    try {
      await expenseApi.create({
        organizationId: selectedOrganizationId,
        paidByMembershipId,
        categoryId,
        amount,
        expenseDate,
        description,
        splitMode,
        splitMemberIds: splitMode === 'SPECIFIC_GROUP' ? selectedMemberIds : undefined,
      });
      if (!isCurrentOrganization()) return;
      setShowAddExpense(false);
      setSelectedMemberIds([]);
      await loadOrganizationData(selectedOrganizationId);
    } catch (cause) {
      if (isCurrentOrganization()) setError(messageFrom(cause));
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    let mounted = true;

    async function restoreSession() {
      try {
        await authApi.csrf();
        const restoredUser = await authApi.currentUser();
        const memberships = await organizationApi.list();
        if (mounted) {
          setUser(restoredUser);
          setOrganizations(memberships);
          selectOrganization(memberships[0]?.id ?? null);
        }
      } catch (cause) {
        if (mounted && !(cause instanceof ApiError && cause.status === 401)) {
          setError(messageFrom(cause));
        }
      } finally {
        if (mounted) setLoading(false);
      }
    }

    void restoreSession();
    return () => {
      mounted = false;
    };
  }, []);

  async function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError('');
    const form = new FormData(event.currentTarget);
    const email = String(form.get('email') ?? '').trim();
    const password = String(form.get('password') ?? '');

    try {
      await authApi.csrf();
      if (authMode === 'register') {
        await authApi.register({
          fullName: String(form.get('fullName') ?? '').trim(),
          email,
          password,
        });
      } else {
        await authApi.login({ email, password });
      }
      const currentUser = await authApi.currentUser();
      const memberships = await organizationApi.list();
      setUser(currentUser);
      setOrganizations(memberships);
      selectOrganization(memberships[0]?.id ?? null);
    } catch (cause) {
      setError(messageFrom(cause));
    } finally {
      setBusy(false);
    }
  }

  async function createOrganization(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError('');
    const form = new FormData(event.currentTarget);

    try {
      const organization = await organizationApi.create({
        name: String(form.get('name') ?? '').trim(),
        type: String(form.get('type') ?? 'HOUSEHOLD') as OrganizationType,
        currency: 'ARS',
      });
      setOrganizations((current) => [...current, organization]);
      selectOrganization(organization.id);
      setCreatingOrganization(false);
    } catch (cause) {
      setError(messageFrom(cause));
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    setBusy(true);
    setError('');
    try {
      await authApi.logout();
      setUser(null);
      setOrganizations([]);
      selectOrganization(null);
      setAuthMode('login');
    } catch (cause) {
      setError(messageFrom(cause));
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return (
      <main className="page-shell">
        <div className="loading-card" role="status">Conectando con Shareverance…</div>
      </main>
    );
  }

  if (!user) {
    return (
      <main className="page-shell auth-page">
        <section className="auth-card" aria-labelledby="auth-title">
          <div className="brand-mark" aria-hidden="true">Sh</div>
          <p className="eyebrow">Gestión compartida, clara y simple</p>
          <h1 id="auth-title">{authMode === 'login' ? 'Te damos la bienvenida' : 'Creá tu cuenta'}</h1>
          <p className="muted">
            {authMode === 'login'
              ? 'Ingresá para administrar los gastos de tus organizaciones.'
              : 'Registrate para empezar a organizar los gastos compartidos.'}
          </p>

          <form className="form-stack" onSubmit={submitAuth}>
            {authMode === 'register' && (
              <label>
                Nombre y apellido
                <input name="fullName" type="text" autoComplete="name" maxLength={100} required />
              </label>
            )}
            <label>
              Correo electrónico
              <input name="email" type="email" autoComplete="email" maxLength={255} required />
            </label>
            <label>
              Contraseña
              <input
                name="password"
                type="password"
                autoComplete={authMode === 'login' ? 'current-password' : 'new-password'}
                minLength={8}
                required
              />
              {authMode === 'register' && (
                <span className="field-hint">Usá al menos 8 caracteres.</span>
              )}
            </label>
            {error && <p className="notice error" role="alert">{error}</p>}
            <button className="primary-button full-width" type="submit" disabled={busy}>
              {busy
                ? 'Procesando…'
                : authMode === 'login'
                  ? 'Iniciar sesión'
                  : 'Crear cuenta'}
            </button>
          </form>

          <p className="auth-switch">
            {authMode === 'login' ? '¿Todavía no tenés cuenta?' : '¿Ya tenés una cuenta?'}{' '}
            <button
              className="text-button"
              type="button"
              onClick={() => {
                setAuthMode(authMode === 'login' ? 'register' : 'login');
                setError('');
              }}
            >
              {authMode === 'login' ? 'Registrate' : 'Iniciá sesión'}
            </button>
          </p>
        </section>
      </main>
    );
  }

  const selectedOrganization = organizations.find(
    (organization) => organization.id === selectedOrganizationId,
  );

  return (
    <main className="page-shell workspace-page">
      <header className="topbar">
        <a className="brand" href="/" aria-label="Shareverance, inicio">
          <span className="brand-mark small" aria-hidden="true">Sh</span>
          <span>Shareverance</span>
        </a>
        <div className="account-menu">
          <span className="account-name">{user.fullName}</span>
          <button className="secondary-button" type="button" onClick={logout} disabled={busy}>
            Cerrar sesión
          </button>
        </div>
      </header>

      <section className="workspace-content">
        <div className="welcome-copy">
          <p className="eyebrow">Tu espacio de trabajo</p>
          <h1>Hola, {user.fullName.split(' ')[0]}</h1>
          <p className="muted">Elegí una organización para continuar o creá una nueva.</p>
        </div>

        {error && <p className="notice error" role="alert">{error}</p>}

        <div className="organization-heading">
          <h2>Mis organizaciones</h2>
          <button
            className="primary-button"
            type="button"
            onClick={() => {
              setCreatingOrganization((current) => !current);
              setError('');
            }}
          >
            {creatingOrganization ? 'Cancelar' : '＋ Nueva organización'}
          </button>
        </div>

        {creatingOrganization && (
          <form className="organization-form panel" onSubmit={createOrganization}>
            <h3>Crear organización</h3>
            <div className="form-grid">
              <label>
                Nombre
                <input name="name" type="text" maxLength={100} required autoFocus />
              </label>
              <label>
                Tipo
                <select name="type" defaultValue="HOUSEHOLD">
                  <option value="HOUSEHOLD">Hogar</option>
                  <option value="CONDO">Consorcio</option>
                  <option value="UNIVERSITY">Universidad</option>
                </select>
              </label>
            </div>
            <button className="primary-button" type="submit" disabled={busy}>
              {busy ? 'Creando…' : 'Crear organización'}
            </button>
          </form>
        )}

        {organizations.length === 0 && !creatingOrganization ? (
          <div className="empty-state panel">
            <div className="empty-icon" aria-hidden="true">＋</div>
            <h3>Todavía no hay organizaciones</h3>
            <p className="muted">Creá una para empezar a organizar los gastos compartidos.</p>
            <button className="primary-button" type="button" onClick={() => setCreatingOrganization(true)}>
              Crear primera organización
            </button>
          </div>
        ) : (
          <div className="organization-grid">
            {organizations.map((organization) => (
              <button
                className={`organization-card${organization.id === selectedOrganizationId ? ' selected' : ''}`}
                key={organization.id}
                type="button"
                onClick={() => selectOrganization(organization.id)}
                aria-pressed={organization.id === selectedOrganizationId}
              >
                <span className="organization-icon" aria-hidden="true">
                  {organization.type === 'HOUSEHOLD' ? '⌂' : organization.type === 'CONDO' ? '▥' : '▤'}
                </span>
                <span className="organization-name">{organization.name}</span>
                <span className="organization-meta">
                  {organizationTypeLabels[organization.type]} · {organization.currency}
                </span>
              </button>
            ))}
          </div>
        )}

        {selectedOrganization && (
          <section className="selected-organization panel" aria-live="polite">
            <div className="org-header-row">
              <div>
                <p className="eyebrow">Organización activa</p>
                <h2>{selectedOrganization.name}</h2>
                <p className="muted">
                  {organizationTypeLabels[selectedOrganization.type]} · {selectedOrganization.currency}
                  {currentMembership && ` · Tu rol: ${currentMembership.role}`}
                </p>
              </div>
              <nav className="tab-nav" aria-label="Secciones de la organización">
                <button
                  className={`tab-btn${activeTab === 'expenses' ? ' active' : ''}`}
                  type="button"
                  onClick={() => { setActiveTab('expenses'); setError(''); }}
                >
                  Gastos
                </button>
                <button
                  className={`tab-btn${activeTab === 'members' ? ' active' : ''}`}
                  type="button"
                  onClick={() => { setActiveTab('members'); setError(''); }}
                >
                  Miembros ({members.length})
                </button>
                <button
                  className={`tab-btn${activeTab === 'categories' ? ' active' : ''}`}
                  type="button"
                  onClick={() => { setActiveTab('categories'); setError(''); }}
                >
                  Categorías ({categories.length})
                </button>
              </nav>
            </div>

            {loadingOrgData ? (
              <p className="muted" role="status">Cargando datos de la organización…</p>
            ) : (
              <>
                {activeTab === 'expenses' && (
                  <div className="tab-pane">
                    <div className="tab-pane-header">
                      <div className="expense-header-controls">
                        <h3>Gastos de la organización</h3>
                        <div className="view-switch" role="group" aria-label="Vista de gastos">
                          <button
                            className={`view-switch-btn${expenseView === 'history' ? ' active' : ''}`}
                            type="button"
                            aria-pressed={expenseView === 'history'}
                            onClick={() => { setExpenseView('history'); setError(''); }}
                          >
                            Historial
                          </button>
                          <button
                            className={`view-switch-btn${expenseView === 'calendar' ? ' active' : ''}`}
                            type="button"
                            aria-pressed={expenseView === 'calendar'}
                            onClick={() => { setExpenseView('calendar'); setError(''); }}
                          >
                            Calendario
                          </button>
                        </div>
                      </div>
                      <button
                        className="primary-button"
                        type="button"
                        onClick={() => { setShowAddExpense(curr => !curr); setError(''); }}
                      >
                        {showAddExpense ? 'Cancelar' : '＋ Cargar gasto'}
                      </button>
                    </div>

                    {showAddExpense && (
                      <form className="subform panel" onSubmit={handleCreateExpense}>
                        <h4>Nuevo gasto</h4>
                        <div className="form-grid-expense">
                          <label>
                            Descripción
                            <input name="description" type="text" maxLength={500} required placeholder="Ej. Compra supermercado" />
                          </label>

                          <label>
                            Importe (ARS)
                            <input
                              name="amount"
                              type="number"
                              step="0.01"
                              min="0.01"
                              max="9999999999.99"
                              required
                              placeholder="0.00"
                            />
                          </label>

                          <label>
                            Fecha
                            <input
                              name="expenseDate"
                              type="date"
                              max={buenosAiresToday()}
                              defaultValue={buenosAiresToday()}
                              required
                            />
                          </label>

                          <label>
                            Categoría
                            <select name="categoryId" required defaultValue={categories.find(c => c.isActive)?.id ?? ''}>
                              {categories.filter(c => c.isActive).map(c => (
                                <option key={c.id} value={c.id}>{c.name}</option>
                              ))}
                            </select>
                          </label>

                          {isAdmin && (
                            <label>
                              Titular / Pagador
                              <select name="paidByMembershipId" defaultValue={currentMembership?.id ?? ''} required>
                                {members.filter(m => m.isActive).map(m => (
                                  <option key={m.id} value={m.id}>
                                    {getMemberLabel(m)} ({m.role})
                                  </option>
                                ))}
                              </select>
                            </label>
                          )}

                          <label>
                            Modalidad de reparto
                            <select
                              value={splitMode}
                              onChange={(e) => setSplitMode(e.target.value as SplitMode)}
                            >
                              <option value="INDIVIDUAL">Individual (100% titular)</option>
                              <option value="ALL_MEMBERS">Partes iguales: Todos los miembros activos</option>
                              <option value="SPECIFIC_GROUP">Partes iguales: Grupo específico</option>
                            </select>
                          </label>
                        </div>

                        {splitMode === 'SPECIFIC_GROUP' && (
                          <div className="member-picker">
                            <span className="field-hint">Seleccioná los participantes:</span>
                            <div className="checkbox-list">
                              {members.filter(m => m.isActive).map(m => (
                                <label key={m.id} className="checkbox-item">
                                  <input
                                    type="checkbox"
                                    checked={selectedMemberIds.includes(m.id)}
                                    onChange={(e) => {
                                      if (e.target.checked) {
                                        setSelectedMemberIds(curr => [...curr, m.id]);
                                      } else {
                                        setSelectedMemberIds(curr => curr.filter(id => id !== m.id));
                                      }
                                    }}
                                  />
                                  <span>{getMemberLabel(m)}</span>
                                </label>
                              ))}
                            </div>
                          </div>
                        )}

                        <button className="primary-button" type="submit" disabled={busy}>
                          {busy ? 'Guardando…' : 'Registrar gasto'}
                        </button>
                      </form>
                    )}

                    {expenseView === 'history' ? (
                      <ExpenseHistory
                        key={`history-${selectedOrganization.id}`}
                        organizationId={selectedOrganization.id}
                        userId={user.id}
                        isAdmin={isAdmin}
                        members={members}
                        categories={categories}
                        refreshToken={historyRefresh}
                      />
                    ) : (
                      <ExpenseCalendarView
                        key={`calendar-${selectedOrganization.id}`}
                        organizationId={selectedOrganization.id}
                        userId={user.id}
                        isAdmin={isAdmin}
                        members={members}
                        categories={categories}
                        refreshToken={historyRefresh}
                      />
                    )}
                  </div>
                )}

                {activeTab === 'members' && (
                  <div className="tab-pane">
                    <div className="tab-pane-header">
                      <h3>Miembros de la organización</h3>
                      {isAdmin && (
                        <button
                          className="primary-button"
                          type="button"
                          onClick={() => { setShowAddMember(curr => !curr); setError(''); }}
                        >
                          {showAddMember ? 'Cancelar' : '＋ Incorporar miembro'}
                        </button>
                      )}
                    </div>

                    {showAddMember && isAdmin && (
                      <form className="subform panel" onSubmit={handleAddMember}>
                        <h4>Agregar miembro por email exacto</h4>
                        <div className="form-grid">
                          <label>
                            Correo electrónico
                            <input name="email" type="email" required placeholder="usuario@ejemplo.com" />
                          </label>
                          <label>
                            Rol inicial
                            <select name="role" defaultValue="USER">
                              <option value="USER">USER</option>
                              <option value="ADMIN">ADMIN</option>
                            </select>
                          </label>
                        </div>
                        <button className="primary-button" type="submit" disabled={busy}>
                          {busy ? 'Incorporando…' : 'Incorporar'}
                        </button>
                      </form>
                    )}

                    <div className="table-wrapper">
                      <table className="data-table">
                        <thead>
                          <tr>
                            <th>ID Membresía</th>
                            <th>Miembro</th>
                            <th>Rol</th>
                            <th>Estado</th>
                            {isAdmin && <th>Acciones</th>}
                          </tr>
                        </thead>
                        <tbody>
                          {members.map(m => (
                            <tr key={m.id} className={!m.isActive ? 'row-inactive' : ''}>
                              <td>#{m.id}</td>
                              <td>
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                                  <strong style={{ color: '#172b4d' }}>{getMemberLabel(m)}</strong>
                                  <span style={{ fontSize: '0.8rem', color: '#62728f' }}>
                                    {m.userEmail ? m.userEmail : `Usuario #${m.userId}`}
                                  </span>
                                </div>
                              </td>
                              <td>
                                <span className={`badge ${m.role === 'ADMIN' ? 'badge-admin' : ''}`}>
                                  {m.role}
                                </span>
                              </td>
                              <td>{m.isActive ? 'Activo' : 'Inactivo'}</td>
                              {isAdmin && (
                                <td>
                                  {m.isActive ? (
                                    <div className="action-buttons">
                                      <button
                                        className="text-button"
                                        type="button"
                                        disabled={busy}
                                        onClick={() => handleToggleRole(m)}
                                      >
                                        Cambiar a {m.role === 'ADMIN' ? 'USER' : 'ADMIN'}
                                      </button>
                                      <button
                                        className="text-button danger"
                                        type="button"
                                        disabled={busy}
                                        onClick={() => handleDeactivateMember(m.id)}
                                      >
                                        Desactivar
                                      </button>
                                    </div>
                                  ) : (
                                    <div className="action-buttons">
                                      <button
                                        className="text-button"
                                        type="button"
                                        disabled={busy}
                                        onClick={() => handleReactivateMember(m.id)}
                                      >
                                        Reactivar
                                      </button>
                                    </div>
                                  )}
                                </td>
                              )}
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}

                {activeTab === 'categories' && (
                  <div className="tab-pane">
                    <div className="tab-pane-header">
                      <h3>Categorías</h3>
                      {isAdmin && (
                        <button
                          className="primary-button"
                          type="button"
                          onClick={() => { setShowAddCategory(curr => !curr); setError(''); }}
                        >
                          {showAddCategory ? 'Cancelar' : '＋ Nueva categoría'}
                        </button>
                      )}
                    </div>

                    {showAddCategory && isAdmin && (
                      <form className="subform panel" onSubmit={handleCreateCategory}>
                        <h4>Crear categoría</h4>
                        <div className="form-grid">
                          <label>
                            Nombre
                            <input name="name" type="text" maxLength={100} required placeholder="Ej. Servicios, Alquiler" />
                          </label>
                          <label>
                            Descripción
                            <input name="description" type="text" maxLength={500} placeholder="Opcional" />
                          </label>
                        </div>
                        <button className="primary-button" type="submit" disabled={busy}>
                          {busy ? 'Creando…' : 'Crear categoría'}
                        </button>
                      </form>
                    )}

                    <div className="table-wrapper">
                      <table className="data-table">
                        <thead>
                          <tr>
                            <th>Nombre</th>
                            <th>Descripción</th>
                            <th>Estado</th>
                            {isAdmin && <th>Acciones</th>}
                          </tr>
                        </thead>
                        <tbody>
                          {categories.map(c => (
                            <tr key={c.id} className={!c.isActive ? 'row-inactive' : ''}>
                              <td><strong>{c.name}</strong></td>
                              <td>{c.description || '—'}</td>
                              <td>{c.isActive ? 'Activa' : 'Inactiva (histórica)'}</td>
                              {isAdmin && (
                                <td>
                                  {c.isActive ? (
                                    <button
                                      className="text-button danger"
                                      type="button"
                                      disabled={busy}
                                      onClick={() => handleDeactivateCategory(c.id)}
                                    >
                                      Desactivar
                                    </button>
                                  ) : (
                                    <span className="muted">—</span>
                                  )}
                                </td>
                              )}
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}
              </>
            )}
          </section>
        )}
      </section>
    </main>
  );
}
