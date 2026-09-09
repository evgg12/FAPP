import { act, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { clearCredentials, setCredentials } from './api/client'

/**
 * Node's own experimental global `localStorage` (backed by a file) shadows jsdom's
 * implementation in this test environment and leaves it without `getItem`. App reads
 * `localStorage` for the remembered account id, so it needs a working in-memory stand-in
 * regardless of which one wins outside this file.
 */
function memoryLocalStorage(): Storage {
  const store = new Map<string, string>()
  return {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => store.set(key, value),
    removeItem: (key: string) => store.delete(key),
    clear: () => store.clear(),
    key: (index: number) => Array.from(store.keys())[index] ?? null,
    get length() {
      return store.size
    },
  }
}

beforeEach(() => {
  vi.stubGlobal('localStorage', memoryLocalStorage())
})

afterEach(() => {
  clearCredentials()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

/**
 * Enough of the API surface for the dashboard to render with one account and nothing
 * else particular going on, so the test can focus on composition rather than figures.
 */
function mockSignedInFetch() {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((url) => {
    const href = String(url)
    if (href.includes('/auth/me')) {
      return Promise.resolve(
        jsonResponse({ id: 'u1', email: 'owner@example.com', displayName: 'Owner', createdAt: '2026-01-01' }),
      )
    }
    if (href.includes('/analytics/accounts')) {
      return Promise.resolve(
        jsonResponse([
          {
            accountId: 'a1',
            accountName: 'Monzo Current',
            provider: 'monzo',
            income: 100,
            expenditure: 40,
            netSavings: 60,
            transactionCount: 3,
          },
        ]),
      )
    }
    if (href.includes('/analytics/summary')) {
      return Promise.resolve(
        jsonResponse({
          period: { from: '2026-08-01', to: '2026-09-01' },
          income: 100,
          expenditure: 40,
          netSavings: 60,
          transactionCount: 3,
        }),
      )
    }
    if (href.includes('/analytics/savings-pot')) {
      return Promise.resolve(
        jsonResponse({ period: { from: '2026-08-01', to: '2026-09-01' }, paidIn: 0, withdrawn: 0, balance: 0, transactionCount: 0 }),
      )
    }
    if (href.includes('/goals/featured')) {
      return Promise.resolve(jsonResponse([]))
    }
    if (href.includes('/pinned-groups')) {
      return Promise.resolve(jsonResponse([]))
    }
    // Categories, largest-expenses, monthly, and anything else that just needs an array.
    return Promise.resolve(jsonResponse([]))
  })
}

describe('dashboard composition', () => {
  it('has no separate Pinned navigation tab', async () => {
    mockSignedInFetch()
    setCredentials({ email: 'owner@example.com', password: 'irrelevant' })

    await act(async () => {
      render(<App />)
    })

    expect(await screen.findByRole('button', { name: 'Dashboard' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Transactions' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Goals' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Accounts' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Pinned' })).toBeNull()
  })

  it('shows the Pinned panel directly on the dashboard, alongside summary and savings pot', async () => {
    mockSignedInFetch()
    setCredentials({ email: 'owner@example.com', password: 'irrelevant' })

    await act(async () => {
      render(<App />)
    })

    // All present without navigating away from the default Dashboard view.
    expect(await screen.findByRole('heading', { name: 'Pinned' })).toBeTruthy()
    expect(screen.getByText('Summary')).toBeTruthy()
    expect(screen.getByText('Savings pot')).toBeTruthy()
    expect(screen.getByText('Featured goals')).toBeTruthy()
  })

  it('lays out Summary as a 2x2 grid and Savings Pot as one row of four', async () => {
    mockSignedInFetch()
    setCredentials({ email: 'owner@example.com', password: 'irrelevant' })

    await act(async () => {
      render(<App />)
    })
    await screen.findByText('Summary')

    const summaryHeading = screen.getByText('Summary')
    const summaryPanel = summaryHeading.closest('section')!
    expect(summaryPanel.querySelector('.figures-2x2')).toBeTruthy()

    const potHeading = screen.getByText('Savings pot')
    const potPanel = potHeading.closest('section')!
    expect(potPanel.querySelector('.figures-2x2')).toBeNull()
    expect(potPanel.querySelector('.figures')).toBeTruthy()
  })

  it('positions Pinned above Net savings in the upper right column, and Featured goals alongside By account below', async () => {
    mockSignedInFetch()
    setCredentials({ email: 'owner@example.com', password: 'irrelevant' })

    await act(async () => {
      render(<App />)
    })
    await screen.findByRole('heading', { name: 'Pinned' })

    const headings = Array.from(document.querySelectorAll('h2')).map((h) => h.textContent)
    const pinnedIndex = headings.indexOf('Pinned')
    const chartIndex = headings.indexOf('Net savings, last 12 months')
    const accountIndex = headings.indexOf('By account')
    const goalIndex = headings.indexOf('Featured goals')

    expect(pinnedIndex).toBeGreaterThanOrEqual(0)
    expect(pinnedIndex).toBeLessThan(chartIndex)
    expect(accountIndex).toBeLessThan(goalIndex)

    const goalPanel = screen.getByText('Featured goals').closest('section')!
    const accountPanel = screen.getByText('By account').closest('section')!
    expect(goalPanel.closest('.stack')).toBe(accountPanel.closest('.stack'))
  })

  it('keeps the lower dashboard sections out of the upper two-column area', async () => {
    mockSignedInFetch()
    setCredentials({ email: 'owner@example.com', password: 'irrelevant' })

    await act(async () => {
      render(<App />)
    })
    await screen.findByText('Summary')

    // The upper area (Summary/Savings pot left, Pinned/Net savings right) is its
    // own grid, separate from Category, Account, and Largest expenses, which are
    // not trapped inside its left column.
    const summaryPanel = screen.getByText('Summary').closest('section')!
    const upperGrid = summaryPanel.closest('.grid.grid-2')!
    expect(upperGrid.querySelector('h2')?.textContent).not.toBeNull()
    expect(upperGrid.textContent).not.toContain('By category')
    expect(upperGrid.textContent).not.toContain('Largest expenses')

    expect(screen.getByText('By category')).toBeTruthy()
    expect(screen.getByText('By account')).toBeTruthy()
    expect(screen.getByText('Largest expenses')).toBeTruthy()
  })

  it('stretches Pinned and Net savings to fill and evenly split the left column\'s height', async () => {
    mockSignedInFetch()
    setCredentials({ email: 'owner@example.com', password: 'irrelevant' })

    await act(async () => {
      render(<App />)
    })
    await screen.findByText('Summary')

    // The upper grid must not size each column to its own content -- that leaves
    // Pinned/Net savings shorter than Summary/Savings pot and positioned wherever
    // their own content happens to end, instead of matching and evenly splitting
    // the left column's total height.
    const upperGrid = screen.getByText('Summary').closest('section')!.closest('.grid.grid-2')!
    expect(upperGrid.classList.contains('grid-top')).toBe(false)

    const pinnedPanel = screen.getByText('Pinned').closest('section')!
    const rightStack = pinnedPanel.closest('.stack')!
    expect(rightStack.classList.contains('stack-fill')).toBe(true)
    expect(rightStack.contains(screen.getByText('Net savings, last 12 months'))).toBe(true)
  })

  it('stretches By account and Featured goals to fill and evenly split By category\'s height', async () => {
    mockSignedInFetch()
    setCredentials({ email: 'owner@example.com', password: 'irrelevant' })

    await act(async () => {
      render(<App />)
    })
    await screen.findByText('By category')

    const lowerGrid = screen.getByText('By category').closest('section')!.closest('.grid.grid-2')!
    expect(lowerGrid.classList.contains('grid-top')).toBe(false)

    const accountPanel = screen.getByText('By account').closest('section')!
    const rightStack = accountPanel.closest('.stack')!
    expect(rightStack.classList.contains('stack-fill')).toBe(true)
    expect(rightStack.contains(screen.getByText('Featured goals'))).toBe(true)
  })
})
