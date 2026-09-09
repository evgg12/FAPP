import type { LargestExpense } from '../api/types'
import { Async } from './Async'
import { PinStarButton } from './PinStarButton'
import type { AsyncState } from '../hooks/useAsync'
import { day, label, money } from '../format'

/** The biggest outgoings, in the order the API returned them. */
export function LargestExpenses({
  state,
  userId,
  pinnedIds = new Set(),
  onPinChanged = () => {},
}: {
  state: AsyncState<LargestExpense[]>
  userId: string
  pinnedIds?: Set<string>
  onPinChanged?: () => void
}) {
  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Largest expenses</h2>
      </div>
      <Async state={state} empty="No spending in this period." lines={4}>
        {(expenses) => (
          <div className="scroll">
            <table className="cards">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Description</th>
                  <th>Category</th>
                  <th>Account</th>
                  <th className="right">Amount</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {expenses.map((expense) => (
                  <tr key={expense.transactionId}>
                    <td data-label="Date" className="card-title">{day(expense.bookingDate)}</td>
                    <td data-label="Description">{expense.merchant ?? expense.description}</td>
                    <td data-label="Category">{label(expense.category)}</td>
                    <td data-label="Account">{expense.accountName}</td>
                    <td data-label="Amount" className="right tone-down">{money(expense.amount)}</td>
                    <td data-label="">
                      <PinStarButton
                        userId={userId}
                        transactionId={expense.transactionId}
                        label={expense.merchant ?? expense.description}
                        pinned={pinnedIds.has(expense.transactionId)}
                        onChanged={onPinChanged}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Async>
    </section>
  )
}
