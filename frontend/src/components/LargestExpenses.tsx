import type { LargestExpense } from '../api/types'
import { Async } from './Async'
import type { AsyncState } from '../hooks/useAsync'
import { day, label, money } from '../format'

/** The biggest outgoings, in the order the API returned them. */
export function LargestExpenses({ state }: { state: AsyncState<LargestExpense[]> }) {
  return (
    <section className="panel">
      <h2>Largest expenses</h2>
      <Async state={state} empty="No spending in this period.">
        {(expenses) => (
          <div className="scroll">
            <table>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Description</th>
                  <th>Category</th>
                  <th>Account</th>
                  <th className="right">Amount</th>
                </tr>
              </thead>
              <tbody>
                {expenses.map((expense) => (
                  <tr key={expense.transactionId}>
                    <td>{day(expense.bookingDate)}</td>
                    <td>{expense.merchant ?? expense.description}</td>
                    <td>{label(expense.category)}</td>
                    <td>{expense.accountName}</td>
                    <td className="right tone-down">{money(expense.amount)}</td>
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
