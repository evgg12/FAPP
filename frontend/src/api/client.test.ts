import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, analyticsQuery, api, clearCredentials, currentEmail, setCredentials } from './client'
import type { FinancialSummary } from './types'

/** A stand-in for fetch, declared with fetch's own signature so its calls are typed. */
function respondWith(body: unknown, init: { status?: number } = {}) {
  const status = init.status ?? 200
  return vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
    Promise.resolve(
      new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
      }),
    ),
  )
}

type FetchMock = ReturnType<typeof respondWith>

function calledUrl(fetchMock: FetchMock): string {
  return String(fetchMock.mock.calls[0][0])
}

function calledInit(fetchMock: FetchMock): RequestInit {
  return fetchMock.mock.calls[0][1] as RequestInit
}

afterEach(() => {
  vi.unstubAllGlobals()
  clearCredentials()
})

describe('reading a successful response', () => {
  it('returns the parsed body', async () => {
    const summary: FinancialSummary = {
      period: { from: '2026-08-01', to: '2026-09-01' },
      income: 1919.86,
      expenditure: 845.56,
      netSavings: 1074.3,
      transactionCount: 18,
    }
    vi.stubGlobal('fetch', respondWith(summary))

    const loaded = await api.summary('user-1', { from: '2026-08-01', to: '2026-09-01' })

    expect(loaded.income).toBe(1919.86)
    expect(loaded.netSavings).toBe(1074.3)
    expect(loaded.transactionCount).toBe(18)
  })

  it('reads a list endpoint as an array', async () => {
    vi.stubGlobal('fetch', respondWith([{ month: '2026-08', income: 1, expenditure: 2, netSavings: -1, transactionCount: 3 }]))

    const months = await api.monthly('user-1', { from: '2026-08-01', to: '2026-09-01' })

    expect(months).toHaveLength(1)
    expect(months[0].month).toBe('2026-08')
  })
})

describe('surfacing an API error', () => {
  it('carries the code, message and status through', async () => {
    vi.stubGlobal(
      'fetch',
      respondWith(
        { code: 'ACCOUNT_NOT_FOUND', message: 'no account with id x for this user', timestamp: 'now' },
        { status: 404 },
      ),
    )

    const failure = await api
      .summary('user-1', { from: '2026-08-01', to: '2026-09-01' }, 'account-x')
      .catch((error: unknown) => error as ApiError)

    expect(failure).toBeInstanceOf(ApiError)
    expect((failure as ApiError).status).toBe(404)
    expect((failure as ApiError).code).toBe('ACCOUNT_NOT_FOUND')
    expect((failure as ApiError).message).toContain('no account with id')
  })

  it('keeps per-field messages from a rejected request body', async () => {
    vi.stubGlobal(
      'fetch',
      respondWith(
        {
          code: 'VALIDATION_FAILED',
          message: 'one or more fields were rejected',
          fields: { email: 'must be a well-formed email address' },
          timestamp: 'now',
        },
        { status: 400 },
      ),
    )

    const failure = (await api.createUser('nope', '', 'short').catch((error: unknown) => error)) as ApiError

    expect(failure.code).toBe('VALIDATION_FAILED')
    expect(failure.fields?.email).toContain('well-formed')
  })

  it('still reports something useful when the failure is not our JSON shape', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response('<html>502</html>', { status: 502 }))))

    const failure = (await api
      .transactions('account-1')
      .catch((error: unknown) => error)) as ApiError

    expect(failure.status).toBe(502)
    expect(failure.code).toBe('UNEXPECTED_ERROR')
    expect(failure.message).toContain('502')
  })

  it('says the backend is unreachable when the request cannot be made', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))))

    const failure = (await api
      .transactions('account-1')
      .catch((error: unknown) => error)) as ApiError

    expect(failure.code).toBe('NETWORK_UNREACHABLE')
    expect(failure.message).toContain('backend running')
  })
})

describe('request building', () => {
  it('always sends both dates, and the account only when narrowing', () => {
    expect(analyticsQuery({ from: '2026-08-01', to: '2026-09-01' })).toBe(
      'from=2026-08-01&to=2026-09-01',
    )
    expect(analyticsQuery({ from: '2026-08-01', to: '2026-09-01' }, 'account-1')).toBe(
      'from=2026-08-01&to=2026-09-01&accountId=account-1',
    )
    expect(analyticsQuery({ from: '2026-08-01', to: '2026-09-01' }, undefined, 3)).toBe(
      'from=2026-08-01&to=2026-09-01&limit=3',
    )
  })

  it('asks for the range it was given, so changing the dates changes the request', async () => {
    const fetchMock = respondWith([])
    vi.stubGlobal('fetch', fetchMock)

    await api.categories('user-1', { from: '2026-08-01', to: '2026-09-01' })
    expect(calledUrl(fetchMock)).toBe(
      '/api/users/user-1/analytics/categories?from=2026-08-01&to=2026-09-01',
    )

    fetchMock.mockClear()
    await api.categories('user-1', { from: '2026-01-01', to: '2027-01-01' }, 'account-9')
    expect(calledUrl(fetchMock)).toBe(
      '/api/users/user-1/analytics/categories?from=2026-01-01&to=2027-01-01&accountId=account-9',
    )
  })

  it('uploads the statement as the file part the backend expects', async () => {
    const fetchMock = respondWith({ id: 'import-1' })
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['Transaction ID,Date\n'], 'statement.csv', { type: 'text/csv' })

    await api.uploadStatement('account-1', file)

    expect(calledUrl(fetchMock)).toBe('/api/accounts/account-1/statements')
    const init = calledInit(fetchMock)
    expect(init.method).toBe('POST')
    const form = init.body as FormData
    expect(form.get('file')).toBeInstanceOf(File)
    expect((form.get('file') as File).name).toBe('statement.csv')
    // No Content-Type is set by hand: the browser adds the multipart boundary.
    expect((init.headers ?? {}) as Record<string, string>).not.toHaveProperty('Content-Type')
  })

  it('posts a user as JSON', async () => {
    const fetchMock = respondWith({ id: 'user-1' })
    vi.stubGlobal('fetch', fetchMock)

    await api.createUser('owner@example.com', 'Owner', 'correct-horse-battery-staple')

    const init = calledInit(fetchMock)
    expect(JSON.parse(String(init.body))).toEqual({
      email: 'owner@example.com',
      displayName: 'Owner',
      password: 'correct-horse-battery-staple',
    })
  })
})

describe('authentication', () => {
  it('sends no Authorization header before signing in', async () => {
    const fetchMock = respondWith({ id: 'user-1' })
    vi.stubGlobal('fetch', fetchMock)

    await api.createUser('new@example.com', 'New', 'correct-horse-battery-staple')

    const headers = (calledInit(fetchMock).headers ?? {}) as Record<string, string>
    expect(headers.Authorization).toBeUndefined()
  })

  it('sends the stored credentials as HTTP Basic on every request', async () => {
    const fetchMock = respondWith([])
    vi.stubGlobal('fetch', fetchMock)
    setCredentials({ email: 'owner@example.com', password: 'correct-horse-battery-staple' })

    await api.transactions('account-1')

    const headers = (calledInit(fetchMock).headers ?? {}) as Record<string, string>
    expect(headers.Authorization).toBe(
      `Basic ${btoa('owner@example.com:correct-horse-battery-staple')}`,
    )
  })

  it('reports and forgets who is signed in', () => {
    expect(currentEmail()).toBeNull()

    setCredentials({ email: 'owner@example.com', password: 'secret-enough-password' })
    expect(currentEmail()).toBe('owner@example.com')

    clearCredentials()
    expect(currentEmail()).toBeNull()
  })

  it('surfaces a rejected credential as an unauthorized error', async () => {
    vi.stubGlobal('fetch', respondWith({}, { status: 401 }))
    setCredentials({ email: 'owner@example.com', password: 'wrong' })

    const failure = (await api.me().catch((error: unknown) => error)) as ApiError

    expect(failure.status).toBe(401)
  })
})

describe('reading a user and their accounts', () => {
  it('asks the dedicated endpoints', async () => {
    const fetchMock = respondWith([])
    vi.stubGlobal('fetch', fetchMock)

    await api.accountsOf('user-1')

    expect(calledUrl(fetchMock)).toBe('/api/users/user-1/accounts')
  })
})
