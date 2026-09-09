import type { SavingsGoal } from '../api/types'
import { Async } from './Async'
import type { AsyncState } from '../hooks/useAsync'
import { money, percent, progressWidth } from '../format'

/**
 * The user's explicitly featured goal, if they have marked one.
 *
 * There is no fallback goal to show instead: featuring is a deliberate choice, so having
 * made none is a normal state rather than something to work around.
 */
export function FeaturedGoalPanel({ state }: { state: AsyncState<SavingsGoal> }) {
  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Featured goal</h2>
      </div>
      <Async state={state} empty="No goal featured yet. Star one on the Goals page." lines={3}>
        {(goal) => (
          <div className="stack">
            <div className="spread">
              <h3>{goal.name}</h3>
              <span className={goal.achieved ? 'badge' : 'badge badge-open'}>
                {goal.achieved ? 'Met' : percent(goal.percentageComplete)}
              </span>
            </div>
            <div className="bar-track">
              <div
                className="bar-fill bar-goal"
                style={{ width: progressWidth(goal.percentageComplete) }}
              />
            </div>
            <div className="kv">
              <span className="muted">Saved</span>
              <span>
                {money(goal.currentAmount, goal.currency)} of{' '}
                {money(goal.targetAmount, goal.currency)}
              </span>
            </div>
          </div>
        )}
      </Async>
    </section>
  )
}
