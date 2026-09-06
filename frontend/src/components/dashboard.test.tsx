import { act, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { AsyncState } from '../hooks/useAsync'
import type { MonthlySummary, SavingsGoal, SimulationResult } from '../api/types'
import { PERIOD_SCALES, percent, progressWidth, scaleRange, shortMonth } from '../format'
import { PeriodPicker } from './PeriodPicker'
import { TrendChart } from './TrendChart'
import { SimulatorPanel } from './SimulatorPanel'

function loaded<T>(data: T): AsyncState<T> {
  return { loading: false, data }
}

const MONTHS: MonthlySummary[] = [
  { month: '2026-07', income: 2000, expenditure: 1200, netSavings: 800, transactionCount: 20 },
  { month: '2026-08', income: 2000, expenditure: 2300, netSavings: -300, transactionCount: 24 },
]

describe('the trend chart', () => {
  it('draws a bar pair and a net point per month', () => {
    render(<TrendChart state={loaded(MONTHS)} />)

    const chart = screen.getByRole('img')
    // Two months, two bars each, plus a dot on the net line for each.
    expect(chart.querySelectorAll('rect')).toHaveLength(4)
    expect(chart.querySelectorAll('circle')).toHaveLength(2)
  })

  it('scales below zero so a negative net is not drawn as zero', () => {
    render(<TrendChart state={loaded(MONTHS)} />)

    const dots = screen.getByRole('img').querySelectorAll('circle')
    const above = Number(dots[0].getAttribute('cy'))
    const below = Number(dots[1].getAttribute('cy'))
    // Larger y is further down the chart, so the losing month sits below the winning one.
    expect(below).toBeGreaterThan(above)
  })

  it('says so when the period holds no months', () => {
    render(<TrendChart state={loaded<MonthlySummary[]>([])} />)

    expect(screen.getByText('No months in this period.')).toBeTruthy()
  })
})

describe('period scales', () => {
  it('produces a usable range for every named scale', () => {
    for (const { scale } of PERIOD_SCALES) {
      const range = scaleRange(scale)
      expect(range.from < range.to).toBe(true)
    }
  })

  it('starts this month on the first and ends after today', () => {
    const range = scaleRange('month')

    expect(range.from.endsWith('-01')).toBe(true)
    expect(range.to > new Date().toISOString().slice(0, 10)).toBe(true)
  })

  it('offers the scales as buttons and marks the chosen one', () => {
    const onChange = vi.fn()
    render(
      <PeriodPicker scale="month" range={{ from: '2026-08-01', to: '2026-09-01' }} onChange={onChange} />,
    )

    const chosen = screen.getByRole('button', { name: 'This month' })
    expect(chosen.getAttribute('aria-pressed')).toBe('true')
    expect(screen.getByRole('button', { name: 'Custom' }).getAttribute('aria-pressed')).toBe('false')
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

  it('shortens a month for an axis label', () => {
    expect(shortMonth('2026-08')).toBe('Aug 26')
  })
})

describe('the simulator', () => {
  const RESULT: SimulationResult = {
    baselinePeriod: { from: '2026-01-01', to: '2027-01-01' },
    monthsOfHistory: 12,
    horizonMonths: 12,
    baseline: { income: 2000, expenditure: 1500, net: 500 },
    scenario: { income: 2000, expenditure: 1350, net: 650 },
    monthlyNetChange: 150,
    baselineHorizonNet: 6000,
    scenarioHorizonNet: 7800,
    horizonNetChange: 1800,
  }

  /** Mounting loads the goal list, so the render is awaited to let it settle. */
  async function mount(): Promise<void> {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify([] as SavingsGoal[]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    await act(async () => {
      render(
        <SimulatorPanel
          userId="u1"
          range={{ from: '2026-01-01', to: '2027-01-01' }}
          accountId={null}
        />,
      )
    })
  }

  it('asks for a scenario before showing a projection', async () => {
    await mount()

    expect(screen.getByText('Describe a change and run it to see the projection.')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Run simulation' })).toBeTruthy()
  })

  it('states plainly that a simulation is not saved', async () => {
    await mount()

    expect(screen.getByText(/never touches your transactions/)).toBeTruthy()
  })

  it('reports the horizon figures the API returned, unaltered', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(JSON.stringify([] as SavingsGoal[]), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify(RESULT), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )

    await act(async () => {
      render(
        <SimulatorPanel
          userId="u1"
          range={{ from: '2026-01-01', to: '2027-01-01' }}
          accountId={null}
        />,
      )
    })
    await act(async () => {
      screen.getByRole('button', { name: 'Run simulation' }).click()
    })

    // Per-month baseline and scenario net, then the horizon totals — no arithmetic here.
    expect(screen.getByText('£500.00')).toBeTruthy()
    expect(screen.getByText('£650.00')).toBeTruthy()
    expect(screen.getByText('£6,000.00')).toBeTruthy()
    expect(screen.getByText('£7,800.00')).toBeTruthy()
    expect(
      screen.getByText('£150.00 a month, and £1,800.00 across 12 months.'),
    ).toBeTruthy()
  })
})
