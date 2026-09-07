import type { SavingsPot } from '../api/types'
import { Async } from './Async'
import type { AsyncState } from '../hooks/useAsync'
import { money } from '../format'

/**
 * The savings pot, kept apart from the spending breakdown.
 *
 * Money moved into a pot is not spending, so counting it under a category would overstate
 * what was spent. The backend files those movements under their own Savings category,
 * leaves them out of the category analytics, and reports them here instead.
 */
export function SavingsPotPanel({ state }: { state: AsyncState<SavingsPot> }) {
  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Savings pot</h2>
      </div>
      <Async state={state} empty="No savings movements in this period." lines={2}>
        {(pot) => (
          <div className="figures">
            <div className="figure">
              <span className="figure-label">Set aside</span>
              <span className={pot.balance < 0 ? 'figure-value tone-down' : 'figure-value tone-up'}>
                {money(pot.balance)}
              </span>
            </div>
            <div className="figure">
              <span className="figure-label">Paid in</span>
              <span className="figure-value">{money(pot.paidIn)}</span>
            </div>
            <div className="figure">
              <span className="figure-label">Taken out</span>
              <span className="figure-value">{money(pot.withdrawn)}</span>
            </div>
            <div className="figure">
              <span className="figure-label">Movements</span>
              <span className="figure-value">{pot.transactionCount}</span>
            </div>
          </div>
        )}
      </Async>
    </section>
  )
}
