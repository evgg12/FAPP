import type {
  Account,
  AccountSummary,
  RecategorisationResult,
  SavingsGoal,
  AccountType,
  ApiErrorBody,
  CategorySummary,
  DateRange,
  FinancialSummary,
  LargestExpense,
  MonthlySummary,
  StatementImport,
  Transaction,
  User,
} from './types'

/**
 * A failed request, carrying the API's stable error code so the UI can say what went
 * wrong rather than "something went wrong".
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fields?: Record<string, string>,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/**
 * The credentials sent with every request.
 *
 * <p>The backend uses HTTP Basic, so a password has to travel on each call. It is held in
 * `sessionStorage` rather than `localStorage` deliberately: it is gone when the tab
 * closes, and the browser never keeps it beyond the session. Holding a password at all is
 * the cost of Basic auth — a token or session scheme removes the need, and is the right
 * change for Phase 3.
 */
const CREDENTIALS_KEY = 'fapp.credentials'

interface Credentials {
  email: string
  password: string
}

export function setCredentials(credentials: Credentials): void {
  sessionStorage.setItem(CREDENTIALS_KEY, JSON.stringify(credentials))
}

export function clearCredentials(): void {
  sessionStorage.removeItem(CREDENTIALS_KEY)
}

export function currentEmail(): string | null {
  return readCredentials()?.email ?? null
}

function readCredentials(): Credentials | null {
  const stored = sessionStorage.getItem(CREDENTIALS_KEY)
  if (!stored) {
    return null
  }
  try {
    return JSON.parse(stored) as Credentials
  } catch {
    return null
  }
}

function authorization(): Record<string, string> {
  const credentials = readCredentials()
  if (!credentials) {
    return {}
  }
  // btoa handles the ASCII case; encodeURIComponent/escape keeps non-ASCII correct.
  const encoded = btoa(
    unescape(encodeURIComponent(`${credentials.email}:${credentials.password}`)),
  )
  return { Authorization: `Basic ${encoded}` }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      ...init,
      headers: { ...authorization(), ...(init.headers ?? {}) },
    })
  } catch {
    // The browser could not reach the API at all, which usually means the backend is
    // not running. Worth saying so plainly rather than reporting a network trace.
    throw new ApiError(0, 'NETWORK_UNREACHABLE', 'Could not reach the FAPP API. Is the backend running?')
  }

  if (!response.ok) {
    let body: Partial<ApiErrorBody> = {}
    try {
      body = (await response.json()) as Partial<ApiErrorBody>
    } catch {
      // Not every failure comes back as our JSON shape; a proxy error might not.
    }
    throw new ApiError(
      response.status,
      body.code ?? 'UNEXPECTED_ERROR',
      body.message ?? `The API responded with ${response.status}.`,
      body.fields,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

function json(body: unknown): RequestInit {
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }
}

/** Query string for the analytics endpoints; `from` and `to` are always required. */
export function analyticsQuery(range: DateRange, accountId?: string, limit?: number): string {
  const params = new URLSearchParams({ from: range.from, to: range.to })
  if (accountId) {
    params.set('accountId', accountId)
  }
  if (limit !== undefined) {
    params.set('limit', String(limit))
  }
  return params.toString()
}

export const api = {
  /** Registration. The only call that needs no credential, because it creates one. */
  createUser(email: string, displayName: string, password: string): Promise<User> {
    return request<User>('/api/users', json({ email, displayName, password }))
  },

  /** Who the stored credentials belong to. A 401 from here means they are wrong. */
  me(): Promise<User> {
    return request<User>('/api/auth/me')
  },

  /** The user's accounts. Its own endpoint now, rather than the analytics breakdown. */
  accountsOf(userId: string): Promise<Account[]> {
    return request<Account[]>(`/api/users/${userId}/accounts`)
  },

  createAccount(input: {
    userId: string
    provider: string
    displayName: string
    accountType: AccountType
    currency: string
  }): Promise<Account> {
    return request<Account>('/api/accounts', json(input))
  },

  /** Uploads a statement as the `file` part, exactly as the backend expects. */
  /** Removes an account and everything imported into it. Not reversible. */
  deleteAccount(accountId: string): Promise<void> {
    return request<void>(`/api/accounts/${accountId}`, { method: 'DELETE' })
  },

  uploadStatement(accountId: string, file: File): Promise<StatementImport> {
    const form = new FormData()
    form.append('file', file)
    return request<StatementImport>(`/api/accounts/${accountId}/statements`, {
      method: 'POST',
      body: form,
    })
  },

  transactions(accountId: string): Promise<Transaction[]> {
    return request<Transaction[]>(`/api/accounts/${accountId}/transactions`)
  },

  summary(userId: string, range: DateRange, accountId?: string): Promise<FinancialSummary> {
    return request<FinancialSummary>(
      `/api/users/${userId}/analytics/summary?${analyticsQuery(range, accountId)}`,
    )
  },

  categories(userId: string, range: DateRange, accountId?: string): Promise<CategorySummary[]> {
    return request<CategorySummary[]>(
      `/api/users/${userId}/analytics/categories?${analyticsQuery(range, accountId)}`,
    )
  },

  monthly(userId: string, range: DateRange, accountId?: string): Promise<MonthlySummary[]> {
    return request<MonthlySummary[]>(
      `/api/users/${userId}/analytics/monthly?${analyticsQuery(range, accountId)}`,
    )
  },

  /**
   * The per-account breakdown. It reports every account the user holds, including any
   * with no activity in the period, so it doubles as the way to list accounts — the API
   * has no separate account-listing endpoint and this task does not add one.
   */
  accounts(userId: string, range: DateRange): Promise<AccountSummary[]> {
    return request<AccountSummary[]>(
      `/api/users/${userId}/analytics/accounts?${analyticsQuery(range)}`,
    )
  },

  goals(userId: string): Promise<SavingsGoal[]> {
    return request<SavingsGoal[]>(`/api/users/${userId}/goals`)
  },

  createGoal(
    userId: string,
    goal: { name: string; targetAmount: number; currency: string; targetDate: string },
  ): Promise<SavingsGoal> {
    return request<SavingsGoal>(`/api/users/${userId}/goals`, json(goal))
  },

  updateGoal(
    userId: string,
    goalId: string,
    goal: {
      name: string
      targetAmount: number
      currentAmount: number
      targetDate: string
    },
  ): Promise<SavingsGoal> {
    return request<SavingsGoal>(`/api/users/${userId}/goals/${goalId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(goal),
    })
  },

  deleteGoal(userId: string, goalId: string): Promise<void> {
    return request<void>(`/api/users/${userId}/goals/${goalId}`, { method: 'DELETE' })
  },

  /** Reapplies today's merchant rules to transactions imported before them. */
  recategorise(userId: string): Promise<RecategorisationResult> {
    return request<RecategorisationResult>(
      `/api/users/${userId}/transactions/recategorise`,
      { method: 'POST' },
    )
  },

  largestExpenses(
    userId: string,
    range: DateRange,
    accountId?: string,
    limit = 10,
  ): Promise<LargestExpense[]> {
    return request<LargestExpense[]>(
      `/api/users/${userId}/analytics/largest-expenses?${analyticsQuery(range, accountId, limit)}`,
    )
  },
}
