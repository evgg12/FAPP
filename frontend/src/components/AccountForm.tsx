import { useState } from 'react'
import { ApiError, api } from '../api/client'
import type { AccountType } from '../api/types'
import { ErrorNotice } from './Async'
import { label } from '../format'

const ACCOUNT_TYPES: AccountType[] = ['CURRENT', 'SAVINGS', 'CREDIT_CARD', 'OTHER']

/** The banks the backend has adapters for. Anything else cannot have statements read. */
const PROVIDERS = [
  { slug: 'monzo', name: 'Monzo' },
  { slug: 'bank_of_scotland', name: 'Bank of Scotland' },
]

/**
 * Adds an account, in a modal so the accounts page is a list rather than a list plus a
 * permanently open form. The bank chosen here decides which adapter reads its statements
 * later, which is why the import form has no format selector.
 */
export function AccountForm({
  userId,
  onCreated,
  onClose,
}: {
  userId: string
  onCreated: (accountId: string) => void
  onClose: () => void
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
      onCreated(account.id)
      onClose()
    } catch (caught) {
      setError(caught as ApiError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div
      className="modal"
      role="dialog"
      aria-modal="true"
      aria-label="Create account"
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose()
        }
      }}
    >
      <section className="panel modal-card">
        <div className="panel-head">
          <h2>Create account</h2>
          <button type="button" className="btn-quiet" onClick={onClose}>
            Close
          </button>
        </div>
        {error && <ErrorNotice error={error} />}
        <form onSubmit={create} className="form-grid">
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
          <button type="submit" className="btn-wide" disabled={busy}>
            {busy ? 'Adding…' : 'Add account'}
          </button>
        </form>
        <p className="muted">
          Currency is fixed at GBP, which is what both supported banks export.
        </p>
      </section>
    </div>
  )
}
