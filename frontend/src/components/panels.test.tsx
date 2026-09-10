import { act, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import type { AsyncState } from '../hooks/useAsync'
import type {
  CategorySummary,
  FinancialSummary,
  SavingsGoal,
  Transaction,
} from '../api/types'
import { CategoryBreakdown } from './CategoryBreakdown'
import { FeaturedGoalPanel } from './FeaturedGoalPanel'
import { GoalsPanel } from './GoalsPanel'
import { SummaryPanel } from './SummaryPanel'
import { TransactionList } from './TransactionList'

function loaded<T>(data: T): AsyncState<T> {
  return { loading: false, data }
}

afterEach(() => {
  vi.restoreAllMocks()
})

const SUMMARY: FinancialSummary = {
  period: { from: '2026-08-01', to: '2026-09-01' },
  income: 1919.86,
  expenditure: 845.56,
  netSavings: 1074.3,
  transactionCount: 18,
}

describe('summary panel', () => {
  it('shows the figures the API returned', () => {
    render(<SummaryPanel state={loaded(SUMMARY)} />)

    expect(screen.getByText('£1,919.86')).toBeTruthy()
    expect(screen.getByText('£845.56')).toBeTruthy()
    expect(screen.getByText('£1,074.30')).toBeTruthy()
    expect(screen.getByText('18')).toBeTruthy()
  })

  it('shows a loading state while in flight', () => {
    render(<SummaryPanel state={{ loading: true }} />)

    expect(screen.getByText('Loading…')).toBeTruthy()
  })

  it('shows the error code and message when the request failed', () => {
    const error = new ApiError(404, 'USER_NOT_FOUND', 'no user with id abc')
    render(<SummaryPanel state={{ loading: false, error }} />)

    expect(screen.getByRole('alert')).toBeTruthy()
    expect(screen.getByText('USER_NOT_FOUND')).toBeTruthy()
    expect(screen.getByText('no user with id abc')).toBeTruthy()
  })

  it('reports zeros rather than an empty panel for a quiet period', () => {
    render(
      <SummaryPanel
        state={loaded({ ...SUMMARY, income: 0, expenditure: 0, netSavings: 0, transactionCount: 0 })}
      />,
    )

    expect(screen.getAllByText('£0.00').length).toBeGreaterThan(0)
    expect(screen.getByText('0')).toBeTruthy()
  })
})

describe('category breakdown', () => {
  const categories: CategorySummary[] = [
    { category: 'BILLS', income: 0, expenditure: 500, net: -500, transactionCount: 1 },
    { category: 'UNCATEGORISED', income: 0, expenditure: 25, net: -25, transactionCount: 1 },
  ]

  it('renders each category with a readable name', () => {
    render(<CategoryBreakdown state={loaded(categories)} />)

    expect(screen.getByText('Bills')).toBeTruthy()
    expect(screen.getByText('Uncategorised')).toBeTruthy()
  })

  it('says so when there is nothing in the period', () => {
    render(<CategoryBreakdown state={loaded<CategorySummary[]>([])} />)

    expect(screen.getByText('No categorised spending in this period.')).toBeTruthy()
  })
})

describe('featured goal panel', () => {
  const GOAL: SavingsGoal = {
    id: 'g1',
    userId: 'u1',
    name: 'Car Fund',
    currency: 'GBP',
    targetAmount: 8000,
    currentAmount: 2350,
    remainingAmount: 5650,
    percentageComplete: 29.38,
    achieved: false,
    targetDate: '2030-06-01',
    featured: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  }

  it('shows the featured goal and its progress', () => {
    render(<FeaturedGoalPanel state={loaded([GOAL])} />)

    expect(screen.getByText('Car Fund')).toBeTruthy()
    expect(screen.getByText('£2,350.00 of £8,000.00')).toBeTruthy()
    expect(screen.getByText('29%')).toBeTruthy()
  })

  it('shows every featured goal when more than one is featured', () => {
    const SECOND: SavingsGoal = { ...GOAL, id: 'g2', name: 'Holiday', targetAmount: 1200, currentAmount: 1200 }

    render(<FeaturedGoalPanel state={loaded([GOAL, SECOND])} />)

    expect(screen.getByText('Car Fund')).toBeTruthy()
    expect(screen.getByText('Holiday')).toBeTruthy()
  })

  it('shows an empty state when no goal is featured', () => {
    render(<FeaturedGoalPanel state={{ loading: false, data: [] }} />)

    expect(screen.getByText('No goal featured yet. Star one on the Goals page.')).toBeTruthy()
  })
})

describe('goals panel star toggle', () => {
  const UNFEATURED: SavingsGoal = {
    id: 'g1',
    userId: 'u1',
    name: 'Car Fund',
    currency: 'GBP',
    targetAmount: 8000,
    currentAmount: 2350,
    remainingAmount: 5650,
    percentageComplete: 29.38,
    achieved: false,
    targetDate: '2030-06-01',
    featured: false,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  }

  it('features a goal when the star is clicked', async () => {
    let goal = UNFEATURED
    const fetching = vi.spyOn(globalThis, 'fetch').mockImplementation((_url, init) => {
      const method = (init as RequestInit | undefined)?.method
      if (method === 'PUT') {
        goal = { ...goal, featured: true }
        return Promise.resolve(
          new Response(JSON.stringify(goal), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
      }
      return Promise.resolve(
        new Response(JSON.stringify([goal]), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
    })

    await act(async () => {
      render(<GoalsPanel userId="u1" />)
    })

    await act(async () => {
      screen.getByRole('button', { name: 'Feature Car Fund' }).click()
    })

    expect(fetching).toHaveBeenCalledWith(
      '/api/users/u1/goals/g1/featured',
      expect.objectContaining({ method: 'PUT' }),
    )
    expect(await screen.findByRole('button', { name: 'Unfeature Car Fund' })).toBeTruthy()
  })
})

describe('transaction list', () => {
  const transactions: Transaction[] = [
    {
      id: 't1',
      bookingDate: '2026-08-03',
      amount: -24.15,
      currency: 'GBP',
      description: 'GREENFIELD GROCERS 4821',
      merchant: 'Greenfield Grocers',
      category: 'GROCERIES',
      categorySource: 'ADAPTER',
      transactionType: 'CARD_PAYMENT',
    },
    {
      id: 't2',
      bookingDate: '2026-08-11',
      amount: -18.62,
      currency: 'GBP',
      originalAmount: -21.9,
      originalCurrency: 'EUR',
      description: 'BAHNHOF BUCHHANDLUNG',
      category: 'SHOPPING',
      categorySource: 'ADAPTER',
      transactionType: 'CARD_PAYMENT',
    },
  ]

  it('renders a row per transaction with its signed amount', () => {
    render(<TransactionList state={loaded(transactions)} accountSelected userId="u1" />)

    expect(screen.getByText('Greenfield Grocers')).toBeTruthy()
    expect(screen.getByText('-£24.15')).toBeTruthy()
    // The category is a dropdown now, so it is read off the control's value.
    expect(
      (screen.getByLabelText('Category for Greenfield Grocers') as HTMLSelectElement).value,
    ).toBe('GROCERIES')
    // Both fixture rows are card payments.
    expect(screen.getAllByText('Card payment')).toHaveLength(2)
    expect(screen.getByText('2 transactions')).toBeTruthy()
  })

  it('shows the foreign amount alongside a converted purchase', () => {
    render(<TransactionList state={loaded(transactions)} accountSelected userId="u1" />)

    expect(screen.getByText('(-€21.90)')).toBeTruthy()
  })

  it('asks for an account before showing anything', () => {
    render(<TransactionList state={{ loading: false }} accountSelected={false} userId="u1" />)

    expect(screen.getByText('Add an account under Accounts to see its transactions.')).toBeTruthy()
  })

  it('says so when the account has no transactions yet', () => {
    render(<TransactionList state={loaded<Transaction[]>([])} accountSelected userId="u1" />)

    expect(
      screen.getByText('No transactions on this account yet. Import a statement.'),
    ).toBeTruthy()
  })
})
