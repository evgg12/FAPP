import { useRef, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Category, Transaction } from '../api/types'
import { CATEGORIES } from '../api/types'
import { Async, ErrorNotice } from './Async'
import { PinStarButton } from './PinStarButton'
import type { AsyncState } from '../hooks/useAsync'
import { MONTH_NAMES, day, label, money } from '../format'

/**
 * The transactions on the selected account, as the API returns them — newest first,
 * so a `limit` simply takes the first ones.
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
  showBank = false,
  limit,
  userId,
  pinnedIds = new Set(),
  onPinChanged = () => {},
  onCategoryChanged,
}: {
  state: AsyncState<Transaction[]>
  accountSelected: boolean
  /** Which bank each row is from -- only useful once several accounts are merged. */
  showBank?: boolean
  limit?: number
  userId: string
  pinnedIds?: Set<string>
  onPinChanged?: () => void
  onCategoryChanged?: () => void
}) {
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(1)
  const sectionRef = useRef<HTMLElement>(null)

  function goToPage(next: number) {
    setPage(next)
    sectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return (
    <section className="panel" ref={sectionRef}>
      <div className="panel-head">
        <h2>{limit ? 'Recent transactions' : 'Transactions'}</h2>
      </div>
      {!limit && (
        <div className="search-bar">
          <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
            <circle cx="10.5" cy="10.5" r="6.5" fill="none" stroke="currentColor" strokeWidth="1.8" />
            <line x1="15.5" y1="15.5" x2="21" y2="21" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          </svg>
          <input
            type="search"
            value={query}
            placeholder="Search by description, amount, date"
            aria-label="Search transactions"
            onChange={(e) => {
              setQuery(e.target.value)
              setPage(1)
            }}
          />
        </div>
      )}
      {!accountSelected ? (
        <p className="empty">Add an account under Accounts to see its transactions.</p>
      ) : (
        <Async
          state={state}
          empty="No transactions on this account yet. Import a statement."
          lines={5}
        >
          {(transactions) => {
            const sorted = [...transactions].sort((a, b) => b.bookingDate.localeCompare(a.bookingDate))
            const filtered = limit ? sorted : sorted.filter((transaction) => matchesSearch(transaction, query))
            if (!limit && query.trim() !== '' && filtered.length === 0) {
              return <p className="empty">No transactions match &quot;{query.trim()}&quot;.</p>
            }
            const pageCount = limit ? 1 : Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
            const currentPage = Math.min(page, pageCount)
            const shown = limit
              ? filtered.slice(0, limit)
              : filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE)
            return (
              <div className="stack transaction-list-foot">
                <div className="scroll">
                  <table className="cards">
                    <thead>
                      <tr>
                        <th>Date</th>
                        <th>Description</th>
                        <th>Category</th>
                        <th>Type</th>
                        {showBank && <th>Bank</th>}
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
                          {showBank && <td data-label="Bank">{label(transaction.provider ?? '')}</td>}
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
                    : !limit && query.trim() !== ''
                      ? `${filtered.length} of ${transactions.length} transactions matched`
                      : `${transactions.length} transactions`}
                </p>
                {!limit && pageCount > 1 && (
                  <div className="pagination">
                    <button
                      type="button"
                      className="btn-quiet"
                      disabled={currentPage === 1}
                      onClick={() => goToPage(currentPage - 1)}
                    >
                      Previous
                    </button>
                    <span className="muted">
                      Page {currentPage} of {pageCount}
                    </span>
                    <button
                      type="button"
                      className="btn-quiet"
                      disabled={currentPage === pageCount}
                      onClick={() => goToPage(currentPage + 1)}
                    >
                      Next
                    </button>
                  </div>
                )}
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

const PAGE_SIZE = 30

const SEARCH_DAY_MONTH_YEAR = /^(\d{1,2})\s+([a-z]+)\s+(\d{4})$/i
const SEARCH_MONTH_YEAR = /^([a-z]+)\s+(\d{4})$/i
const SEARCH_NUMBER = /^-?\d+(\.\d+)?$/

/** The month name/abbreviation's index (0-11), matched on its first three letters. */
function monthIndex(name: string): number | null {
  const key = name.slice(0, 3).toLowerCase()
  const index = MONTH_NAMES.findIndex((month) => month.slice(0, 3).toLowerCase() === key)
  return index === -1 ? null : index
}

/**
 * A search box that does not ask the user to say what kind of thing they typed: a date
 * ("01 Sep 2026") matches that day's booking date, a month and year on its own
 * ("Sep 2026") matches every transaction that month, a plain number matches an amount
 * (either sign, so "24.15" finds a £24.15 charge), and anything else is a
 * case-insensitive substring match against the description.
 */
function matchesSearch(transaction: Transaction, query: string): boolean {
  const trimmed = query.trim().replace(/\s+/g, ' ')
  if (trimmed === '') {
    return true
  }

  const dayMatch = trimmed.match(SEARCH_DAY_MONTH_YEAR)
  if (dayMatch) {
    const [, dd, monthName, yyyy] = dayMatch
    const month = monthIndex(monthName)
    if (month === null) {
      return false
    }
    const iso = `${yyyy}-${String(month + 1).padStart(2, '0')}-${dd.padStart(2, '0')}`
    return transaction.bookingDate === iso
  }

  const monthMatch = trimmed.match(SEARCH_MONTH_YEAR)
  if (monthMatch) {
    const [, monthName, yyyy] = monthMatch
    const month = monthIndex(monthName)
    if (month === null) {
      return false
    }
    const prefix = `${yyyy}-${String(month + 1).padStart(2, '0')}-`
    return transaction.bookingDate.startsWith(prefix)
  }

  if (SEARCH_NUMBER.test(trimmed)) {
    const target = Math.abs(Number(trimmed))
    const absAmount = Math.abs(transaction.amount)
    // A whole number is a pounds-only search ("12" finds £12.41); a typed decimal
    // is exact, since that's specific enough to mean one particular amount.
    return trimmed.includes('.') ? Math.abs(absAmount - target) < 0.005 : Math.trunc(absAmount) === target
  }

  const haystack = `${transaction.merchant ?? ''} ${transaction.description}`.toLowerCase()
  return haystack.includes(trimmed.toLowerCase())
}
