import { useMemo, useRef, useState } from 'react'
import type { Transaction } from '../api/types'
import { Async } from './Async'
import { PinStarButton } from './PinStarButton'
import type { AsyncState } from '../hooks/useAsync'
import { day, money } from '../format'

export function TransactionLookup({
  state,
  accountSelected,
  userId,
  pinnedIds = new Set(),
  onPinChanged = () => {},
  searchQuery: initialQuery = '',
  onClose,
  isModal = false,
}: {
  state: AsyncState<Transaction[]>
  accountSelected: boolean
  userId: string
  pinnedIds?: Set<string>
  onPinChanged?: () => void
  searchQuery?: string
  onClose?: () => void
  isModal?: boolean
}) {
  const [searchQuery, setSearchQuery] = useState(initialQuery)
  const sectionRef = useRef<HTMLElement>(null)

  const content = (
    <section className={isModal ? "panel lookup-modal-content" : "panel"} ref={sectionRef}>
      <div className="panel-head">
        <h2>{isModal ? searchQuery : 'Transaction Lookup'}</h2>
        {isModal && onClose && (
          <button
            type="button"
            className="btn-quiet"
            onClick={onClose}
            aria-label="Close lookup"
          >
            ✕
          </button>
        )}
      </div>
      {!accountSelected ? (
        <p className="empty">Add an account under Accounts to see its transactions.</p>
      ) : (
        <Async
          state={state}
          empty="No transactions on this account yet. Import a statement."
          lines={5}
        >
          {(transactions) => {
            return (
              <div className="lookup-container">
                <div className="search-bar">
                  <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
                    <circle cx="10.5" cy="10.5" r="6.5" fill="none" stroke="currentColor" strokeWidth="1.8" />
                    <line x1="15.5" y1="15.5" x2="21" y2="21" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
                  </svg>
                  <input
                    type="search"
                    value={searchQuery}
                    placeholder="Search by person or merchant name"
                    aria-label="Search transactions by person"
                    onChange={(e) => setSearchQuery(e.target.value)}
                  />
                </div>

                {isModal ? (
                  <LookupResults
                    transactions={transactions}
                    searchQuery={searchQuery}
                    userId={userId}
                    pinnedIds={pinnedIds}
                    onPinChanged={onPinChanged}
                  />
                ) : searchQuery.trim() === '' ? (
                  <p className="muted" style={{ textAlign: 'center', paddingBlock: '2rem' }}>
                    Enter a name to see all transactions with that person
                  </p>
                ) : (
                  <LookupResults
                    transactions={transactions}
                    searchQuery={searchQuery}
                    userId={userId}
                    pinnedIds={pinnedIds}
                    onPinChanged={onPinChanged}
                  />
                )}
              </div>
            )
          }}
        </Async>
      )}
    </section>
  )

  if (isModal) {
    return (
      <div className="lookup-modal-overlay" onClick={onClose}>
        <div className="lookup-modal-wrapper" onClick={(e) => e.stopPropagation()}>
          {content}
        </div>
      </div>
    )
  }

  return content
}

function LookupResults({
  transactions,
  searchQuery,
  userId,
  pinnedIds,
  onPinChanged,
}: {
  transactions: Transaction[]
  searchQuery: string
  userId: string
  pinnedIds: Set<string>
  onPinChanged: () => void
}) {
  const trimmedQuery = searchQuery.trim().toLowerCase()

  const filtered = useMemo(() => {
    return transactions.filter((t) => {
      const haystack = `${t.merchant ?? ''} ${t.description}`.toLowerCase()
      return haystack.includes(trimmedQuery)
    })
  }, [transactions, trimmedQuery])

  const { totalIn, totalOut, incoming, outgoing } = useMemo(() => {
    let in_sum = 0
    let out_sum = 0
    const inc: Transaction[] = []
    const out: Transaction[] = []

    filtered.forEach((t) => {
      if (t.amount > 0) {
        in_sum += t.amount
        inc.push(t)
      } else {
        out_sum += Math.abs(t.amount)
        out.push(t)
      }
    })

    return {
      totalIn: in_sum,
      totalOut: out_sum,
      incoming: inc.sort((a, b) => b.bookingDate.localeCompare(a.bookingDate)),
      outgoing: out.sort((a, b) => b.bookingDate.localeCompare(a.bookingDate)),
    }
  }, [filtered])

  if (filtered.length === 0) {
    return <p className="empty">No transactions found for &quot;{searchQuery.trim()}&quot;.</p>
  }

  return (
    <div className="lookup-results">
      <div className="lookup-header">
        <h3>{searchQuery.trim()}</h3>
      </div>

      <div className="lookup-summary">
        <div className="summary-card money-sent">
          <div className="summary-label">Money Sent</div>
          <div className="summary-amount tone-down numeric">{money(totalOut, transactions[0]?.currency || 'GBP')}</div>
          <div className="summary-count muted">{outgoing.length} transaction{outgoing.length !== 1 ? 's' : ''}</div>
        </div>
        <div className="summary-card money-received">
          <div className="summary-label">Money Received</div>
          <div className="summary-amount tone-up numeric">{money(totalIn, transactions[0]?.currency || 'GBP')}</div>
          <div className="summary-count muted">{incoming.length} transaction{incoming.length !== 1 ? 's' : ''}</div>
        </div>
      </div>

      {outgoing.length > 0 && (
        <div className="lookup-section">
          <h4>Money Sent ({outgoing.length})</h4>
          <div className="scroll">
            <table className="cards">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Description</th>
                  <th className="right">Amount</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {outgoing.map((transaction) => (
                  <tr key={transaction.id}>
                    <td data-label="Date" className="card-title">
                      {day(transaction.bookingDate)}
                    </td>
                    <td data-label="Description">
                      {transaction.merchant ?? transaction.description}
                    </td>
                    <td data-label="Amount" className="right tone-down numeric">
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
        </div>
      )}

      {incoming.length > 0 && (
        <div className="lookup-section">
          <h4>Money Received ({incoming.length})</h4>
          <div className="scroll">
            <table className="cards">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Description</th>
                  <th className="right">Amount</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {incoming.map((transaction) => (
                  <tr key={transaction.id}>
                    <td data-label="Date" className="card-title">
                      {day(transaction.bookingDate)}
                    </td>
                    <td data-label="Description">
                      {transaction.merchant ?? transaction.description}
                    </td>
                    <td data-label="Amount" className="right tone-up numeric">
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
        </div>
      )}
    </div>
  )
}
