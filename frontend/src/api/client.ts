import type {
  Account,
  AccountSummary,
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

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, init)
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
  createUser(email: string, displayName: string): Promise<User> {
    return request<User>('/api/users', json({ email, displayName }))
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
