import type { FinancialSummary } from '../api/types'
import { Async } from './Async'
import type { AsyncState } from '../hooks/useAsync'
import { money } from '../format'

/**
 * The four headline figures. Every one comes from the API; nothing is added up here.
 * Net savings is coloured by sign only, which is the one thing the number alone does not
 * make obvious at a glance.
 */
export function SummaryPanel({ state }: { state: AsyncState<FinancialSummary> }) {
  return (
    <section className="panel">
      <h2>Summary</h2>
      <Async state={state} empty="No transactions in this period.">
        {(summary) => (
          <div className="figures">
            <Figure label="Money in" value={money(summary.income)} />
            <Figure label="Money out" value={money(summary.expenditure)} />
            <Figure
              label="Net"
              value={money(summary.netSavings)}
              tone={summary.netSavings < 0 ? 'down' : 'up'}
            />
            <Figure label="Transactions" value={String(summary.transactionCount)} />
          </div>
        )}
      </Async>
    </section>
  )
}

function Figure({ label, value, tone }: { label: string; value: string; tone?: 'up' | 'down' }) {
  return (
    <div className="figure">
      <span className="figure-label">{label}</span>
      <span className={tone ? `figure-value tone-${tone}` : 'figure-value'}>{value}</span>
    </div>
  )
}
