import { useState } from 'react'
import { ApiError, api } from '../api/client'
import type { AccountSummary } from '../api/types'
import type { AsyncState } from '../hooks/useAsync'
import { Async, ErrorNotice } from './Async'
import { label, money } from '../format'

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
}: {
  accounts: AsyncState<AccountSummary[]>
  onRemoved: (accountId: string) => void
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
