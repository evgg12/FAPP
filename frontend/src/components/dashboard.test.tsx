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

describe('removing an account', () => {
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

  it('asks before removing, and does nothing if the answer is no', async () => {
    const fetching = vi.spyOn(globalThis, 'fetch')
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const onRemoved = vi.fn()

    render(<ManageAccounts accounts={loaded(ACCOUNTS)} onRemoved={onRemoved} />)
    await act(async () => {
      screen.getByRole('button', { name: 'Remove account' }).click()
    })

    expect(fetching).not.toHaveBeenCalled()
    expect(onRemoved).not.toHaveBeenCalled()
  })

  it('deletes the account and tells its caller which one went', async () => {
    const fetching = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(null, { status: 204 }))
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const onRemoved = vi.fn()

    render(<ManageAccounts accounts={loaded(ACCOUNTS)} onRemoved={onRemoved} />)
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
