import type { Transaction } from '../api/types'
import { Async } from './Async'
import type { AsyncState } from '../hooks/useAsync'
import { day, label, money } from '../format'

/**
 * The transactions on the selected account, as the API returns them — oldest first,
 * unless a `limit` is given, in which case the most recent are shown.
 *
 * Amounts keep the stored sign, so an outgoing reads negative here while the analytics
 * panels report expenditure as a positive total. That is the backend's distinction and
 * the list reflects it rather than smoothing it over.
 *
 * On a phone the table collapses into one card per transaction — see `table.cards` in
 * the stylesheet — so no financial data ends up behind a sideways scroll.
 */
export function TransactionList({
  state,
  accountSelected,
  limit,
}: {
  state: AsyncState<Transaction[]>
  accountSelected: boolean
  limit?: number
}) {
  return (
    <section className="panel">
      <div className="panel-head">
        <h2>{limit ? 'Recent transactions' : 'Transactions'}</h2>
      </div>
      {!accountSelected ? (
        <p className="empty">Select a single account to see its transactions.</p>
      ) : (
        <Async
          state={state}
          empty="No transactions on this account yet. Import a statement."
          lines={5}
        >
          {(transactions) => {
            const shown = limit ? [...transactions].reverse().slice(0, limit) : transactions
            return (
              <div className="stack">
                <div className="scroll">
                  <table className="cards">
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
                      {shown.map((transaction) => (
                        <tr key={transaction.id}>
                          <td data-label="Date" className="card-title">
                            {day(transaction.bookingDate)}
                          </td>
                          <td data-label="Description">
                            {transaction.merchant ?? transaction.description}
                            {transaction.originalAmount !== undefined && (
                              <span className="muted">
                                {' '}
                                ({money(transaction.originalAmount, transaction.originalCurrency)})
                              </span>
                            )}
                          </td>
                          <td data-label="Category">{label(transaction.category)}</td>
                          <td data-label="Type">{label(transaction.transactionType)}</td>
                          <td
                            data-label="Amount"
                            className={transaction.amount < 0 ? 'right tone-down' : 'right tone-up'}
                          >
                            {money(transaction.amount, transaction.currency)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <p className="muted">
                  {limit && transactions.length > shown.length
                    ? `${shown.length} most recent of ${transactions.length} transactions`
                    : `${transactions.length} transactions`}
                </p>
              </div>
            )
          }}
        </Async>
      )}
    </section>
  )
}
