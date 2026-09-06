import { useState } from 'react'
import { ApiError, api } from '../api/client'
import type { AccountSummary, AccountType, DateRange } from '../api/types'
import { Async, ErrorNotice } from './Async'
import type { AsyncState } from '../hooks/useAsync'
import { label } from '../format'

const ACCOUNT_TYPES: AccountType[] = ['CURRENT', 'SAVINGS', 'CREDIT_CARD', 'OTHER']

/** The banks the backend has adapters for. Anything else cannot have statements read. */
const PROVIDERS = [
  { slug: 'monzo', name: 'Monzo' },
  { slug: 'bank_of_scotland', name: 'Bank of Scotland' },
]

export function AccountPicker({
  userId,
  range,
  accounts,
  selectedId,
  onSelect,
  onCreated,
}: {
  userId: string
  range: DateRange
  accounts: AsyncState<AccountSummary[]>
  selectedId: string | null
  onSelect: (accountId: string | null) => void
  onCreated: () => void
}) {
  const [provider, setProvider] = useState(PROVIDERS[0].slug)
  const [displayName, setDisplayName] = useState('')
  const [accountType, setAccountType] = useState<AccountType>('CURRENT')
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  async function create(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const account = await api.createAccount({
        userId,
        provider,
        displayName: displayName.trim(),
        accountType,
        currency: 'GBP',
      })
      setDisplayName('')
      onCreated()
      onSelect(account.id)
    } catch (caught) {
      setError(caught as ApiError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="panel">
      <h2>Accounts</h2>
      <Async state={accounts} empty="No accounts yet. Add one below.">
        {(list) => (
          <ul className="choices">
            <li>
              <button
                type="button"
                className={selectedId === null ? 'chip chip-on' : 'chip'}
                onClick={() => onSelect(null)}
              >
                All accounts
              </button>
            </li>
            {list.map((account) => (
              <li key={account.accountId}>
                <button
                  type="button"
                  className={selectedId === account.accountId ? 'chip chip-on' : 'chip'}
                  onClick={() => onSelect(account.accountId)}
                >
                  {account.accountName}
                  <span className="muted"> · {label(account.provider)}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </Async>

      {error && <ErrorNotice error={error} />}
      <form onSubmit={create} className="row wrap">
        <label>
          Bank
          <select value={provider} onChange={(e) => setProvider(e.target.value)}>
            {PROVIDERS.map((bank) => (
              <option key={bank.slug} value={bank.slug}>
                {bank.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Name
          <input
            value={displayName}
            required
            placeholder="Monzo Current"
            onChange={(e) => setDisplayName(e.target.value)}
          />
        </label>
        <label>
          Type
          <select
            value={accountType}
            onChange={(e) => setAccountType(e.target.value as AccountType)}
          >
            {ACCOUNT_TYPES.map((type) => (
              <option key={type} value={type}>
                {label(type)}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" disabled={busy}>
          {busy ? 'Adding…' : 'Add account'}
        </button>
      </form>
      <p className="muted">
        Accounts are listed for {range.from} to {range.to}. Currency is fixed at GBP,
        which is what both supported banks export.
      </p>
    </section>
  )
}
