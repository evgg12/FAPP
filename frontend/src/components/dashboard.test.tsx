import { act, fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { AccountSummary } from '../api/types'
import type { AsyncState } from '../hooks/useAsync'
import {
  PERIOD_SCALES,
  latestMonthWithData,
  monthRange,
  percent,
  progressWidth,
  scaleRange,
  yearsWithData,
} from '../format'
import { ManageAccounts } from './ManageAccounts'
import { TransactionList } from './TransactionList'
import { PeriodPicker } from './PeriodPicker'

function loaded<T>(data: T): AsyncState<T> {
  return { loading: false, data }
}

describe('periods', () => {
  it('turns a chosen month into the half-open range the API expects', () => {
    expect(monthRange('2026-08')).toEqual({ from: '2026-08-01', to: '2026-09-01' })
    // December has to roll the year over.
    expect(monthRange('2026-12')).toEqual({ from: '2026-12-01', to: '2027-01-01' })
  })

  it('offers exactly Month and Annual', () => {
    expect(PERIOD_SCALES.map((option) => option.label)).toEqual(['Month', 'Annual'])
  })

  it('reads Annual as this year so far', () => {
    const range = scaleRange('year')

    expect(range.from.endsWith('-01-01')).toBe(true)
    expect(range.to > new Date().toISOString().slice(0, 10)).toBe(true)
  })

  it('defaults to the last month that holds transactions, not the real-world month', () => {
    const months = [
      { month: '2026-06', transactionCount: 12 },
      { month: '2026-07', transactionCount: 8 },
      { month: '2026-08', transactionCount: 0 },
    ]

    expect(latestMonthWithData(months)).toBe('2026-07')
    expect(latestMonthWithData([{ month: '2026-08', transactionCount: 0 }])).toBeNull()
    expect(yearsWithData([{ month: '2024-03', transactionCount: 4 }])).toContain(2024)
  })

  it('picks a month from dropdowns rather than a typed date', () => {
    render(
      <PeriodPicker
        scale="month"
        month="2026-08"
        years={[2026, 2025]}
        range={{ from: '2026-08-01', to: '2026-09-01' }}
        onChange={vi.fn()}
      />,
    )

    const months = screen.getByLabelText('Month') as HTMLSelectElement
    expect(months.tagName).toBe('SELECT')
    expect(months.options).toHaveLength(12)
    expect(months.value).toBe('08')
    expect((screen.getByLabelText('Year') as HTMLSelectElement).value).toBe('2026')
  })

  it('reports the chosen month back to its caller', () => {
    const onChange = vi.fn()
    render(
      <PeriodPicker
        scale="month"
        month="2026-08"
        years={[2026]}
        range={{ from: '2026-08-01', to: '2026-09-01' }}
        onChange={onChange}
      />,
    )

    fireEvent.change(screen.getByLabelText('Month'), { target: { value: '11' } })
    expect(onChange).toHaveBeenCalledWith('month', '2026-11')

    screen.getByRole('button', { name: 'Annual' }).click()
    expect(onChange).toHaveBeenCalledWith('year', '2026-08')
  })
})

describe('progress formatting', () => {
  it('clamps the drawn width without touching the reported figure', () => {
    expect(progressWidth(142.5)).toBe('100%')
    expect(progressWidth(-3)).toBe('0%')
    expect(progressWidth(41.25)).toBe('41.25%')
    expect(percent(142.5)).toBe('143%')
    expect(percent(4.25)).toBe('4.3%')
  })
})

describe('accounts and their statements', () => {
  const ACCOUNTS: AccountSummary[] = [
    {
      accountId: 'a1',
      accountName: 'Monzo Current',
      provider: 'monzo',
      income: 100,
      expenditure: 40,
      netSavings: 60,
      transactionCount: 3,
    },
  ]

  const STATEMENTS = [
    {
      id: 'i1',
      accountId: 'a1',
      provider: 'monzo',
      periodStart: '2026-08-03',
      periodEnd: '2026-08-29',
      rowCount: 18,
      importedCount: 18,
      duplicateCount: 0,
      importedAt: '2026-09-01T10:00:00Z',
    },
  ]

  const SUMMARIES: Record<string, { income: number; expenditure: number; transactionCount: number }> = {
    '2026-08-01': { income: 100, expenditure: 40, transactionCount: 3 },
    '2026-01-01': { income: 0, expenditure: 0, transactionCount: 0 },
  }

  function withStatements() {
    return vi.spyOn(globalThis, 'fetch').mockImplementation((url, init) => {
      if ((init as RequestInit | undefined)?.method === 'DELETE') {
        return Promise.resolve(new Response(null, { status: 204 }))
      }
      const href = String(url)
      if (href.includes('/analytics/summary')) {
        const from = new URL(href, 'http://localhost').searchParams.get('from')!
        const figures = SUMMARIES[from] ?? { income: 0, expenditure: 0, transactionCount: 0 }
        return Promise.resolve(
          new Response(
            JSON.stringify({ period: { from, to: from }, netSavings: 0, ...figures }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          ),
        )
      }
      return Promise.resolve(
        new Response(JSON.stringify(href.includes('/statements') ? STATEMENTS : []), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
    })
  }

  it('lists the period each loaded statement covers, defaulting to its month', async () => {
    withStatements()
    await act(async () => {
      render(
        <ManageAccounts
          userId="u1"
          accounts={loaded(ACCOUNTS)}
          onRemoved={vi.fn()}
          onStatementRemoved={vi.fn()}
        />,
      )
    })

    // Defaults to the latest statement's month without the user having to pick it.
    expect((screen.getByLabelText('Statement month') as HTMLSelectElement).value).toBe('08')
    expect((screen.getByLabelText('Statement year') as HTMLSelectElement).value).toBe('2026')
    // The dates the statement itself reported, not when it was uploaded.
    expect(screen.getByText(/03 Aug 2026 – 29 Aug 2026/)).toBeTruthy()
    expect(screen.getByText(/£100\.00 in · £40\.00 out · 3 transactions/)).toBeTruthy()
  })

  it('updates the "In this period" figures when the selected month changes', async () => {
    withStatements()
    await act(async () => {
      render(
        <ManageAccounts
          userId="u1"
          accounts={loaded(ACCOUNTS)}
          onRemoved={vi.fn()}
          onStatementRemoved={vi.fn()}
        />,
      )
    })

    expect(screen.getByText(/£100\.00 in · £40\.00 out · 3 transactions/)).toBeTruthy()

    await act(async () => {
      fireEvent.change(screen.getByLabelText('Statement month'), { target: { value: '01' } })
    })

    expect(screen.getByText(/£0\.00 in · £0\.00 out · 0 transactions/)).toBeTruthy()
    expect(screen.queryByText(/£100\.00 in · £40\.00 out · 3 transactions/)).toBeNull()
  })

  it('never overwrites a month the user already chose, even once statement data reloads', async () => {
    // Two statements so there is a "latest" (August) distinct from the one the user
    // picks (July), and a delete-triggered refetch to prove the choice survives a reload.
    let currentStatements = [
      {
        id: 'i2',
        accountId: 'a1',
        provider: 'monzo',
        periodStart: '2026-07-05',
        periodEnd: '2026-07-28',
        rowCount: 10,
        importedCount: 10,
        duplicateCount: 0,
        importedAt: '2026-08-01T10:00:00Z',
      },
      STATEMENTS[0],
    ]
    vi.spyOn(globalThis, 'fetch').mockImplementation((url, init) => {
      const request = init as RequestInit | undefined
      if (request?.method === 'DELETE') {
        const removedId = String(url).split('/').pop()
        currentStatements = currentStatements.filter((statement) => statement.id !== removedId)
        return Promise.resolve(new Response(null, { status: 204 }))
      }
      const href = String(url)
      if (href.includes('/analytics/summary')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({ period: { from: '', to: '' }, income: 0, expenditure: 0, netSavings: 0, transactionCount: 0 }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          ),
        )
      }
      return Promise.resolve(
        new Response(JSON.stringify(href.includes('/statements') ? currentStatements : []), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    await act(async () => {
      render(
        <ManageAccounts
          userId="u1"
          accounts={loaded(ACCOUNTS)}
          onRemoved={vi.fn()}
          onStatementRemoved={vi.fn()}
        />,
      )
    })

    // Defaults to August, the latest statement.
    expect((screen.getByLabelText('Statement month') as HTMLSelectElement).value).toBe('08')

    // The user deliberately picks July instead.
    await act(async () => {
      fireEvent.change(screen.getByLabelText('Statement month'), { target: { value: '07' } })
    })
    expect(screen.getByText(/05 Jul 2026 – 28 Jul 2026/)).toBeTruthy()

    // Deleting the July statement forces the statement list to reload.
    await act(async () => {
      screen.getByRole('button', { name: 'Remove' }).click()
    })

    // The picker still shows July — the newer August statement never silently took over.
    expect((screen.getByLabelText('Statement month') as HTMLSelectElement).value).toBe('07')
    expect(screen.getByText(/No statement uploaded for July 2026/)).toBeTruthy()
  })

  it('shows no statement for a month nothing was uploaded for, and offers to import one', async () => {
    withStatements()
    const onSelectAccount = vi.fn()

    await act(async () => {
      render(
        <ManageAccounts
          userId="u1"
          accounts={loaded(ACCOUNTS)}
          onRemoved={vi.fn()}
          onStatementRemoved={vi.fn()}
          onSelectAccount={onSelectAccount}
        />,
      )
    })
    await act(async () => {
      fireEvent.change(screen.getByLabelText('Statement month'), { target: { value: '01' } })
    })

    expect(screen.getByText(/No statement uploaded for January 2026/)).toBeTruthy()
    screen.getByRole('button', { name: 'Import a statement for this account' }).click()
    expect(onSelectAccount).toHaveBeenCalledWith('a1')
  })

  it('removes one statement without removing the account', async () => {
    const fetching = withStatements()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const onStatementRemoved = vi.fn()

    await act(async () => {
      render(
        <ManageAccounts
          userId="u1"
          accounts={loaded(ACCOUNTS)}
          onRemoved={vi.fn()}
          onStatementRemoved={onStatementRemoved}
        />,
      )
    })
    await act(async () => {
      screen.getByRole('button', { name: 'Remove' }).click()
    })

    expect(fetching).toHaveBeenCalledWith('/api/imports/i1', expect.objectContaining({ method: 'DELETE' }))
    expect(onStatementRemoved).toHaveBeenCalled()
  })

  it('asks before removing an account, and does nothing if the answer is no', async () => {
    const fetching = withStatements()
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const onRemoved = vi.fn()

    await act(async () => {
      render(
        <ManageAccounts userId="u1" accounts={loaded(ACCOUNTS)} onRemoved={onRemoved} onStatementRemoved={vi.fn()} />,
      )
    })
    await act(async () => {
      screen.getByRole('button', { name: 'Remove account' }).click()
    })

    expect(fetching).not.toHaveBeenCalledWith(
      '/api/accounts/a1',
      expect.objectContaining({ method: 'DELETE' }),
    )
    expect(onRemoved).not.toHaveBeenCalled()
  })

  it('deletes the account and tells its caller which one went', async () => {
    const fetching = withStatements()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const onRemoved = vi.fn()

    await act(async () => {
      render(
        <ManageAccounts userId="u1" accounts={loaded(ACCOUNTS)} onRemoved={onRemoved} onStatementRemoved={vi.fn()} />,
      )
    })
    await act(async () => {
      screen.getByRole('button', { name: 'Remove account' }).click()
    })

    expect(fetching).toHaveBeenCalledWith(
      '/api/accounts/a1',
      expect.objectContaining({ method: 'DELETE' }),
    )
    expect(onRemoved).toHaveBeenCalledWith('a1')
  })
})

describe('editing a category', () => {
  const TRANSACTION = {
    id: 't1',
    bookingDate: '2026-08-03',
    amount: -24.15,
    currency: 'GBP',
    description: 'GREENFIELD GROCERS 4821',
    merchant: 'Greenfield Grocers',
    category: 'GROCERIES' as const,
    categorySource: 'ADAPTER' as const,
    transactionType: 'CARD_PAYMENT' as const,
  }

  it('saves a different category straight from the list', async () => {
    const fetching = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify(TRANSACTION), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }))
    const onCategoryChanged = vi.fn()

    render(
      <TransactionList
        state={loaded([TRANSACTION])}
        accountSelected
        onCategoryChanged={onCategoryChanged}
      />,
    )
    const select = screen.getByLabelText('Category for Greenfield Grocers')
    await act(async () => {
      fireEvent.change(select, { target: { value: 'RESTAURANTS' } })
    })

    expect(fetching).toHaveBeenCalledWith(
      '/api/transactions/t1/category',
      expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ category: 'RESTAURANTS' }) }),
    )
    expect(onCategoryChanged).toHaveBeenCalled()
  })

  it('asks for a name when the category is Custom, and sends it', async () => {
    const fetching = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify(TRANSACTION), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }))

    render(<TransactionList state={loaded([TRANSACTION])} accountSelected />)
    await act(async () => {
      fireEvent.change(screen.getByLabelText('Category for Greenfield Grocers'), {
        target: { value: 'CUSTOM' },
      })
    })
    // Choosing Custom saves nothing until it has been named.
    expect(fetching).not.toHaveBeenCalled()

    fireEvent.change(screen.getByLabelText('Custom category name'), { target: { value: 'Gym' } })
    await act(async () => {
      screen.getByRole('button', { name: 'Save' }).click()
    })

    expect(fetching).toHaveBeenCalledWith(
      '/api/transactions/t1/category',
      expect.objectContaining({
        body: JSON.stringify({ category: 'CUSTOM', customCategory: 'Gym' }),
      }),
    )
  })
})
