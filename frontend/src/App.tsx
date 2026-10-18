import { FormEvent, useEffect, useState } from 'react';
import {
  ApiError,
  Organization,
  OrganizationType,
  User,
  authApi,
  organizationApi,
} from './api/java';

type AuthMode = 'login' | 'register';

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
          setSelectedOrganizationId(memberships[0]?.id ?? null);
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
      setSelectedOrganizationId(memberships[0]?.id ?? null);
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
      setSelectedOrganizationId(organization.id);
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
      setSelectedOrganizationId(null);
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
                onClick={() => setSelectedOrganizationId(organization.id)}
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
            <div>
              <p className="eyebrow">Organización seleccionada</p>
              <h2>{selectedOrganization.name}</h2>
              <p className="muted">
                {organizationTypeLabels[selectedOrganization.type]} · {selectedOrganization.currency}
              </p>
            </div>
          </section>
        )}
      </section>
    </main>
  );
}
