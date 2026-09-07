import { useState } from 'react'
import { ApiError, api } from '../api/client'
import type { AccountSummary } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import type { AsyncState } from '../hooks/useAsync'
import { Async, ErrorNotice } from './Async'
import { day, label, money } from '../format'

/**
 * The accounts that exist, and the one destructive action in the application.
 *
 * Removing an account removes everything imported into it, which the schema does by
 * cascade — so this asks first, and says how many transactions are about to go with it
 * rather than leaving that to be discovered afterwards.
 */
export function ManageAccounts({
  accounts,
  onRemoved,
  onStatementRemoved,
}: {
  accounts: AsyncState<AccountSummary[]>
  onRemoved: (accountId: string) => void
  onStatementRemoved: () => void
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
                <div className="kv">
                  <span className="muted">In this period</span>
                  <span>
                    {money(account.income)} in · {money(account.expenditure)} out ·{' '}
                    {account.transactionCount} transactions
                  </span>
                </div>
                <Statements accountId={account.accountId} onRemoved={onStatementRemoved} />
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
 * The months this account holds, using the period each statement reported for itself.
 *
 * Removing one takes its transactions with it and frees the month to be uploaded again —
 * which is the point: a statement exported too early can be replaced rather than
 * deduplicated against forever.
 */
function Statements({
  accountId,
  onRemoved,
}: {
  accountId: string
  onRemoved: () => void
}) {
  const [version, setVersion] = useState(0)
  const statements = useAsync(() => api.statements(accountId), [accountId, version])
  const [error, setError] = useState<ApiError | null>(null)
  const [removing, setRemoving] = useState<string | null>(null)

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

  return (
    <div className="stack">
      <span className="figure-label">Loaded statements</span>
      {error && <ErrorNotice error={error} />}
      <Async state={statements} empty="No statements loaded yet." lines={2}>
        {(list) => (
          <ul className="statements">
            {list.map((statement) => {
              const period = `${day(statement.periodStart)} – ${day(statement.periodEnd)}`
              return (
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
              )
            })}
          </ul>
        )}
      </Async>
    </div>
  )
}
