import type { SavingsGoal } from '../api/types'
import { Async } from './Async'
import type { AsyncState } from '../hooks/useAsync'
import { money, percent, progressWidth } from '../format'

/**
 * The user's explicitly featured goals, if they have marked any.
 *
 * There is no fallback goal to show instead: featuring is a deliberate choice, so having
 * made none is a normal state rather than something to work around. Any number may be
 * featured at once; the list scrolls past a cap via `.featured-goal-scroll` rather than
 * growing the panel, matching how `.pinned-scroll` caps the Pinned panel.
 */
export function FeaturedGoalPanel({ state }: { state: AsyncState<SavingsGoal[]> }) {
  return (
    <section className="panel panel-fixed-account">
      <div className="panel-head">
        <h2>Featured goals</h2>
      </div>
      <div className="featured-goal-scroll">
        <Async state={state} empty="No goal featured yet. Star one on the Goals page." lines={3}>
          {(featuredGoals) => (
            <div className="stack">
              {featuredGoals.map((goal) => (
                <div key={goal.id} className="stack featured-goal-card">
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
              ))}
            </div>
          )}
        </Async>
      </div>
    </section>
  )
}
