import type { MonthlySummary } from '../api/types'
import { Async } from './Async'
import type { AsyncState } from '../hooks/useAsync'
import { money, monthName, share } from '../format'

/**
 * Month by month. The API returns every month the period touches, quiet ones included,
 * so a gap in the data shows as a month of zeros rather than disappearing from the list.
 */
export function MonthlyBreakdown({ state }: { state: AsyncState<MonthlySummary[]> }) {
  return (
    <section className="panel">
      <h2>By month</h2>
      <Async state={state} empty="No months in this period.">
        {(months) => {
          const largest = Math.max(...months.map((m) => Math.max(m.income, m.expenditure)), 0)
          return (
            <ul className="bars">
              {months.map((month) => (
                <li key={month.month}>
                  <div className="bar-head">
                    <span>{monthName(month.month)}</span>
                    <span className="muted">
                      {month.transactionCount === 0
                        ? 'nothing'
                        : `${money(month.income)} in · ${money(month.expenditure)} out · net ${money(month.netSavings)}`}
                    </span>
                  </div>
                  <div className="bar-track">
                    <div className="bar-fill bar-in" style={{ width: share(month.income, largest) }} />
                    <div
                      className="bar-fill bar-out"
                      style={{ width: share(month.expenditure, largest) }}
                    />
                  </div>
                </li>
              ))}
            </ul>
          )
        }}
      </Async>
    </section>
  )
}
