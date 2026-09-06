import type { CategorySummary } from '../api/types'
import { Async } from './Async'
import type { AsyncState } from '../hooks/useAsync'
import { label, money, share } from '../format'

/**
 * Where the money went. The API already returns these biggest-spend-first, so the order
 * is the backend's and the bar widths are relative to the largest in the list — a
 * proportion for reading, not a figure to rely on.
 *
 * Income and expenditure are shown separately because a category can hold both: a
 * refund lands under whatever it reverses.
 */
export function CategoryBreakdown({ state }: { state: AsyncState<CategorySummary[]> }) {
  return (
    <section className="panel">
      <div className="panel-head">
        <h2>By category</h2>
      </div>
      <Async state={state} empty="No categorised spending in this period." lines={5}>
        {(categories) => {
          const largest = Math.max(...categories.map((c) => Math.max(c.expenditure, c.income)))
          return (
            <ul className="bars">
              {categories.map((category) => (
                <li key={category.category}>
                  <div className="bar-head">
                    <span>{label(category.category)}</span>
                    <span className="muted">
                      {category.expenditure > 0 && money(category.expenditure)}
                      {category.expenditure > 0 && category.income > 0 && ' out · '}
                      {category.income > 0 && `${money(category.income)} in`}
                      {' · '}
                      {category.transactionCount}
                    </span>
                  </div>
                  <div className="bar-track">
                    <div
                      className="bar-fill bar-out"
                      style={{ width: share(category.expenditure, largest) }}
                    />
                    <div
                      className="bar-fill bar-in"
                      style={{ width: share(category.income, largest) }}
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
