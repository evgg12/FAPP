import type { Transaction } from '../api/types'
import { Async } from './Async'
import type { AsyncState } from '../hooks/useAsync'
import { day, label, money } from '../format'

/**
 * The transactions on the selected account, oldest first, as the API returns them.
 *
 * Amounts keep the stored sign, so an outgoing reads negative here while the analytics
 * panels report expenditure as a positive total. That is the backend's distinction and
 * the list reflects it rather than smoothing it over.
 */
export function TransactionList({
  state,
  accountSelected,
}: {
  state: AsyncState<Transaction[]>
  accountSelected: boolean
}) {
  return (
    <section className="panel">
      <h2>Transactions</h2>
      {!accountSelected ? (
        <p className="muted">Select a single account to see its transactions.</p>
      ) : (
        <Async state={state} empty="No transactions on this account yet. Import a statement.">
          {(transactions) => (
            <div className="scroll">
              <table>
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Description</th>
                    <th>Category</th>
                    <th>Type</th>
                    <th className="right">Amount</th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.map((transaction) => (
                    <tr key={transaction.id}>
                      <td>{day(transaction.bookingDate)}</td>
                      <td>
                        {transaction.merchant ?? transaction.description}
                        {transaction.originalAmount !== undefined && (
                          <span className="muted">
                            {' '}
                            ({money(transaction.originalAmount, transaction.originalCurrency)})
                          </span>
                        )}
                      </td>
                      <td>{label(transaction.category)}</td>
                      <td>{label(transaction.transactionType)}</td>
                      <td className={transaction.amount < 0 ? 'right tone-down' : 'right tone-up'}>
                        {money(transaction.amount, transaction.currency)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <p className="muted">{transactions.length} transactions</p>
            </div>
          )}
        </Async>
      )}
    </section>
  )
}
