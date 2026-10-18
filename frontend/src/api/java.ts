export interface User {
  id: number;
  fullName: string;
  email: string;
  createdAt: string;
  isActive: boolean;
}

export type OrganizationType = 'HOUSEHOLD' | 'CONDO' | 'UNIVERSITY';

export interface Organization {
  id: number;
  name: string;
  type: OrganizationType;
  currency: 'ARS';
  createdAt: string;
  isActive: boolean;
}

interface LoginRequest {
  email: string;
  password: string;
}

interface RegisterRequest extends LoginRequest {
  fullName: string;
}

interface OrganizationRequest {
  name: string;
  type: OrganizationType;
  currency: 'ARS';
}

interface SessionResponse {
  expiresIn: number;
  user: User;
}

interface CsrfResponse {
  csrfToken: string;
}

export class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
    this.name = 'ApiError';
  }
}

let csrfToken: string | null = null;
let csrfRequest: Promise<string> | null = null;

async function request<T>(
  path: string,
  options: RequestInit = {},
  csrf = false,
): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body !== undefined) headers.set('Content-Type', 'application/json');
  if (csrf) headers.set('X-XSRF-TOKEN', await authApi.csrf());

  const response = await fetch(path, {
    ...options,
    headers,
    credentials: 'same-origin',
  });
  const bodyText = await response.text();
  let body: unknown;
  if (bodyText) {
    try {
      body = JSON.parse(bodyText);
    } catch {
      throw new ApiError('Java devolvió una respuesta que no es JSON válido.', response.status);
    }
  }

  if (!response.ok) {
    const payload = typeof body === 'object' && body !== null
      ? body as { message?: unknown; error?: unknown }
      : null;
    const message = typeof payload?.message === 'string'
      ? payload.message
      : typeof payload?.error === 'string'
        ? payload.error
        : `Java respondió HTTP ${response.status}.`;
    throw new ApiError(message, response.status);
  }

  if (response.status === 204) return undefined as T;
  if (body === undefined) {
    throw new ApiError('Java devolvió una respuesta vacía inesperada.', response.status);
  }
  return body as T;
}

export const authApi = {
  csrf(): Promise<string> {
    if (csrfToken) return Promise.resolve(csrfToken);
    if (!csrfRequest) {
      csrfRequest = request<CsrfResponse>('/api/v1/auth/csrf')
        .then(({ csrfToken: token }) => {
          if (!token) throw new ApiError('Java no devolvió el token CSRF.', 500);
          csrfToken = token;
          return token;
        })
        .finally(() => {
          csrfRequest = null;
        });
    }
    return csrfRequest;
  },

  async login(payload: LoginRequest): Promise<SessionResponse> {
    return request<SessionResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, true);
  },

  async register(payload: RegisterRequest): Promise<SessionResponse> {
    return request<SessionResponse>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, true);
  },

  currentUser(): Promise<User> {
    return request<User>('/api/v1/auth/me');
  },

  async logout(): Promise<void> {
    await request<void>('/api/v1/auth/logout', { method: 'POST' }, true);
    csrfToken = null;
  },
};

export const organizationApi = {
  list(): Promise<Organization[]> {
    return request<Organization[]>('/api/v1/organizations');
  },

  create(payload: OrganizationRequest): Promise<Organization> {
    return request<Organization>('/api/v1/organizations', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, true);
  },
};
