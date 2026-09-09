import { useEffect, useRef, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { AccountSummary } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import type { AsyncState } from '../hooks/useAsync'
import { Async, ErrorNotice } from './Async'
import { MONTH_NAMES, currentYearMonth, day, label, monthName, monthRange, money } from '../format'

/**
 * The accounts that exist, and the one destructive action in the application.
 *
 * Removing an account removes everything imported into it, which the schema does by
 * cascade — so this asks first, and says how many transactions are about to go with it
 * rather than leaving that to be discovered afterwards.
 */
export function ManageAccounts({
  userId,
  accounts,
  onRemoved,
  onStatementRemoved,
  onSelectAccount,
}: {
  userId: string
  accounts: AsyncState<AccountSummary[]>
  onRemoved: (accountId: string) => void
  onStatementRemoved: () => void
  onSelectAccount?: (accountId: string) => void
}) {
  const [error, setError] = useState<ApiError | null>(null)
  const [removing, setRemoving] = useState<string | null>(null)

  async function remove(account: AccountSummary) {
    const confirmed = window.confirm(
      `Remove "${account.accountName}" and everything imported into it? This cannot be undone.`,
    )
    if (!confirmed) {
      return
    }
    setError(null)
    setRemoving(account.accountId)
    try {
      await api.deleteAccount(account.accountId)
      onRemoved(account.accountId)
    } catch (caught) {
      setError(caught as ApiError)
    } finally {
      setRemoving(null)
    }
  }

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Your accounts</h2>
      </div>
      {error && <ErrorNotice error={error} />}
      <Async state={accounts} empty="No accounts yet. Add one above." lines={3}>
        {(list) => (
          <div className="stack">
            {list.map((account) => (
              <div className="goal" key={account.accountId}>
                <div className="spread">
                  <h3>{account.accountName}</h3>
                  <span className="muted">{label(account.provider)}</span>
                </div>
                <AccountStatement
                  userId={userId}
                  accountId={account.accountId}
                  onRemoved={onStatementRemoved}
                  onSelectAccount={onSelectAccount}
                />
                <div>
                  <button
                    type="button"
                    className="btn-danger btn-wide"
                    disabled={removing !== null}
                    onClick={() => remove(account)}
                  >
                    {removing === account.accountId ? 'Removing…' : 'Remove account'}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </Async>
    </section>
  )
}

/**
 * The statement this account has for one chosen calendar month, found from the period
 * each statement reported for itself rather than a dedicated lookup — the backend already
 * returns every statement, so the month picker just narrows down to the one that matches.
 *
 * Removing it takes its transactions with it and frees the month to be uploaded again —
 * which is the point: a statement exported too early can be replaced rather than
 * deduplicated against forever.
 */
function AccountStatement({
  userId,
  accountId,
  onRemoved,
  onSelectAccount,
}: {
  userId: string
  accountId: string
  onRemoved: () => void
  onSelectAccount?: (accountId: string) => void
}) {
  const [version, setVersion] = useState(0)
  const statements = useAsync(() => api.statements(accountId), [accountId, version])
  const [error, setError] = useState<ApiError | null>(null)
  const [removing, setRemoving] = useState<string | null>(null)
  const [selectedMonth, setSelectedMonth] = useState(currentYearMonth)
  const hasChosen = useRef(false)

  useEffect(() => {
    if (hasChosen.current || !statements.data || statements.data.length === 0) {
      return
    }
    const latest = statements.data.map((statement) => statement.periodStart.slice(0, 7)).sort().at(-1)!
    setSelectedMonth(latest)
  }, [statements.data])

  async function remove(importId: string, period: string) {
    if (!window.confirm(`Remove the statement covering ${period} and its transactions?`)) {
      return
    }
    setError(null)
    setRemoving(importId)
    try {
      await api.deleteStatement(importId)
      setVersion((previous) => previous + 1)
      onRemoved()
    } catch (caught) {
      setError(caught as ApiError)
    } finally {
      setRemoving(null)
    }
  }

  function chooseMonth(next: string) {
    hasChosen.current = true
    setSelectedMonth(next)
  }

  const [year, monthNumber] = selectedMonth.split('-')
  const years = statements.data
    ? [...new Set([...statements.data.map((s) => Number(s.periodStart.slice(0, 4))), new Date().getUTCFullYear()])].sort(
        (a, b) => b - a,
      )
    : [new Date().getUTCFullYear()]
  if (!years.includes(Number(year))) {
    years.unshift(Number(year))
    years.sort((a, b) => b - a)
  }

  const summary = useAsync(
    () => api.summary(userId, monthRange(selectedMonth), accountId),
    [userId, accountId, selectedMonth],
  )

  return (
    <div className="stack">
      <span className="figure-label">Statement period</span>
      {error && <ErrorNotice error={error} />}
      <div className="row row-tight">
        <label>
          Month
          <select
            value={monthNumber}
            aria-label="Statement month"
            onChange={(e) => chooseMonth(`${year}-${e.target.value}`)}
          >
            {MONTH_NAMES.map((name, index) => (
              <option key={name} value={String(index + 1).padStart(2, '0')}>
                {name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Year
          <select
            value={year}
            aria-label="Statement year"
            onChange={(e) => chooseMonth(`${e.target.value}-${monthNumber}`)}
          >
            {years.map((option) => (
              <option key={option} value={String(option)}>
                {option}
              </option>
            ))}
          </select>
        </label>
      </div>
      <Async state={summary} empty="Nothing for this period." lines={1}>
        {(figures) => (
          <div className="kv">
            <span className="muted">In this period</span>
            <span>
              {money(figures.income)} in · {money(figures.expenditure)} out ·{' '}
              {figures.transactionCount} transactions
            </span>
          </div>
        )}
      </Async>
      <Async state={statements} empty="No statements loaded yet." lines={2}>
        {(list) => {
          const statement = list.find((candidate) => candidate.periodStart.slice(0, 7) === selectedMonth)
          if (!statement) {
            return (
              <div className="stack">
                <p className="empty">No statement uploaded for {monthName(selectedMonth)}.</p>
                {onSelectAccount && (
                  <button type="button" className="btn-quiet" onClick={() => onSelectAccount(accountId)}>
                    Import a statement for this account
                  </button>
                )}
              </div>
            )
          }
          const period = `${day(statement.periodStart)} – ${day(statement.periodEnd)}`
          return (
            <ul className="statements">
              <li key={statement.id}>
                <span>
                  {period}
                  <span className="muted"> · {statement.importedCount} transactions</span>
                </span>
                <button
                  type="button"
                  className="btn-quiet"
                  disabled={removing !== null}
                  onClick={() => remove(statement.id, period)}
                >
                  {removing === statement.id ? 'Removing…' : 'Remove'}
                </button>
              </li>
            </ul>
          )
        }}
      </Async>
    </div>
  )
}
