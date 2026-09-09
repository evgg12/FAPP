import { useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Category, Transaction } from '../api/types'
import { CATEGORIES } from '../api/types'
import { Async, ErrorNotice } from './Async'
import { PinStarButton } from './PinStarButton'
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
  userId,
  pinnedIds = new Set(),
  onPinChanged = () => {},
  onCategoryChanged,
}: {
  state: AsyncState<Transaction[]>
  accountSelected: boolean
  limit?: number
  userId: string
  pinnedIds?: Set<string>
  onPinChanged?: () => void
  onCategoryChanged?: () => void
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
                        <th></th>
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
                          <td data-label="Category">
                            <CategoryCell
                              transaction={transaction}
                              onChanged={onCategoryChanged}
                            />
                          </td>
                          <td data-label="Type">{label(transaction.transactionType)}</td>
                          <td
                            data-label="Amount"
                            className={transaction.amount < 0 ? 'right tone-down' : 'right tone-up'}
                          >
                            {money(transaction.amount, transaction.currency)}
                          </td>
                          <td data-label="">
                            <PinStarButton
                              userId={userId}
                              transactionId={transaction.id}
                              label={transaction.merchant ?? transaction.description}
                              pinned={pinnedIds.has(transaction.id)}
                              onChanged={onPinChanged}
                            />
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

/**
 * The category, editable where it is shown.
 *
 * A native select is used deliberately: it is the one control that a phone renders as a
 * full-screen picker and a desktop as a dropdown, with no custom touch handling to get
 * wrong. Choosing "Custom…" reveals a name field, and the change is saved through the
 * API — the category is the only field a user is allowed to change, and it is recorded
 * as their choice so a later import's rules will not overwrite it.
 */
function CategoryCell({
  transaction,
  onChanged,
}: {
  transaction: Transaction
  onChanged?: () => void
}) {
  const [naming, setNaming] = useState(false)
  const [name, setName] = useState(transaction.customCategory ?? '')
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  async function save(category: Category, customCategory?: string) {
    setError(null)
    setBusy(true)
    try {
      await api.setCategory(transaction.id, category, customCategory)
      setNaming(false)
      onChanged?.()
    } catch (caught) {
      setError(caught as ApiError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <span className="category-cell">
      <select
        className="category-select"
        value={transaction.category}
        disabled={busy}
        aria-label={`Category for ${transaction.merchant ?? transaction.description}`}
        onChange={(event) => {
          const chosen = event.target.value as Category
          if (chosen === 'CUSTOM') {
            setNaming(true)
          } else {
            void save(chosen)
          }
        }}
      >
        {CATEGORIES.map((category) => (
          <option key={category} value={category}>
            {label(category)}
          </option>
        ))}
        <option value="CUSTOM">
          {transaction.customCategory ? transaction.customCategory : 'Custom…'}
        </option>
      </select>
      {naming && (
        <span className="row row-tight">
          <input
            value={name}
            maxLength={40}
            placeholder="Category name"
            aria-label="Custom category name"
            onChange={(event) => setName(event.target.value)}
          />
          <button
            type="button"
            className="btn-quiet"
            disabled={busy || name.trim() === ''}
            onClick={() => void save('CUSTOM', name.trim())}
          >
            Save
          </button>
        </span>
      )}
      {error && <ErrorNotice error={error} />}
    </span>
  )
}
