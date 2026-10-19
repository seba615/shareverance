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

export type MembershipRole = 'ADMIN' | 'USER';

export interface UserOrganization {
  id: number;
  userId: number;
  organizationId: number;
  role: MembershipRole;
  joinedAt: string;
  isActive: boolean;
  userName?: string | null;
  userEmail?: string | null;
}

export interface AddMemberRequest {
  organizationId: number;
  userId?: number;
  email?: string;
  role?: MembershipRole;
}

export const membershipApi = {
  listByOrganization(organizationId: number): Promise<UserOrganization[]> {
    return request<UserOrganization[]>(`/api/v1/memberships/organization/${organizationId}`);
  },

  addMember(payload: AddMemberRequest): Promise<UserOrganization> {
    return request<UserOrganization>('/api/v1/memberships', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, true);
  },

  updateRole(id: number, role: MembershipRole): Promise<UserOrganization> {
    return request<UserOrganization>(`/api/v1/memberships/${id}/role?role=${role}`, {
      method: 'PATCH',
    }, true);
  },

  deactivate(id: number): Promise<UserOrganization> {
    return request<UserOrganization>(`/api/v1/memberships/${id}`, {
      method: 'DELETE',
    }, true);
  },

  reactivate(id: number): Promise<UserOrganization> {
    return request<UserOrganization>(`/api/v1/memberships/${id}/reactivate`, {
      method: 'PATCH',
    }, true);
  },
};

export interface Category {
  id: number;
  organizationId: number;
  name: string;
  description?: string;
  color?: string;
  createdAt: string;
  isActive: boolean;
}

export interface CategoryRequest {
  name: string;
  description?: string;
  color?: string;
}

export const categoryApi = {
  listByOrganization(organizationId: number): Promise<Category[]> {
    return request<Category[]>(`/api/v1/categories/organization/${organizationId}`);
  },

  create(organizationId: number, payload: CategoryRequest): Promise<Category> {
    return request<Category>(`/api/v1/categories/organization/${organizationId}`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }, true);
  },

  update(id: number, payload: CategoryRequest): Promise<Category> {
    return request<Category>(`/api/v1/categories/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }, true);
  },

  deactivate(id: number): Promise<Category> {
    return request<Category>(`/api/v1/categories/${id}`, {
      method: 'DELETE',
    }, true);
  },
};

export type SplitMode = 'INDIVIDUAL' | 'ALL_MEMBERS' | 'BETWEEN_TWO' | 'SPECIFIC_GROUP';

export interface Expense {
  id: number;
  organizationId: number;
  createdByUserId: number;
  paidByMembershipId: number;
  categoryId: number;
  amount: number;
  expenseDate: string;
  description: string;
  receiptUrl?: string;
  splitType: string;
  createdAt: string;
  isActive: boolean;
  version: number;
}

export interface ExpenseSplit {
  id: number;
  expenseId: number;
  userOrganizationId: number;
  amount: number;
}

export interface ExpenseDetailResponse {
  expense: Expense;
  splits: ExpenseSplit[];
}

export interface ExpenseCreateRequest {
  organizationId: number;
  paidByMembershipId: number;
  categoryId: number;
  amount: string;
  expenseDate: string;
  description: string;
  receiptUrl?: string;
  splitMode?: SplitMode;
  splitMemberIds?: number[];
}

export const expenseApi = {
  listByOrganization(organizationId: number): Promise<ExpenseDetailResponse[]> {
    return request<ExpenseDetailResponse[]>(`/api/v1/expenses/organization/${organizationId}`);
  },

  create(payload: ExpenseCreateRequest): Promise<ExpenseDetailResponse> {
    return request<ExpenseDetailResponse>('/api/v1/expenses', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, true);
  },
};

export interface ExpenseFilter {
  fromDate?: string;
  toDate?: string;
  categoryId?: string;
  state: 'ACTIVE' | 'ANNULLED' | 'ALL';
}
export interface ExpensePage {
  items: ExpenseDetailResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  totalAmount: number;
}
export const expenseHistoryApi = {
  page(organizationId: number, filter: ExpenseFilter, page: number, size: number): Promise<ExpensePage> {
    const query = new URLSearchParams({ state: filter.state, page: String(page), size: String(size) });
    if (filter.fromDate) query.set('fromDate', filter.fromDate);
    if (filter.toDate) query.set('toDate', filter.toDate);
    if (filter.categoryId) query.set('categoryId', filter.categoryId);
    return request<ExpensePage>(`/api/v1/expenses/organization/${organizationId}/page?${query}`);
  },
  detail(id: number): Promise<ExpenseDetailResponse> { return request(`/api/v1/expenses/${id}`); },
  update(id: number, payload: { categoryId: number; amount: string; expenseDate: string; description: string; version: number }): Promise<ExpenseDetailResponse> {
    return request(`/api/v1/expenses/${id}`, { method: 'PUT', body: JSON.stringify(payload) }, true);
  },
  annul(id: number): Promise<Expense> { return request(`/api/v1/expenses/${id}`, { method: 'DELETE' }, true); },
};
