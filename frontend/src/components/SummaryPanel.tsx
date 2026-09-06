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
      <div className="panel-head">
        <h2>Summary</h2>
      </div>
      <Async state={state} empty="No transactions in this period." lines={2}>
        {(summary) => (
          <div className="figures">
            <Figure label="Income" value={money(summary.income)} />
            <Figure label="Expenditure" value={money(summary.expenditure)} />
            <Figure
              label="Net savings"
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
