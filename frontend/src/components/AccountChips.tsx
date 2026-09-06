import type { AccountSummary } from '../api/types'
import type { AsyncState } from '../hooks/useAsync'
import { ErrorNotice } from './Async'
import { label } from '../format'

/**
 * Which account the figures are narrowed to. "All accounts" is the default because
 * seeing two banks in one set of totals is the point of the product.
 *
 * The list comes from the analytics endpoint rather than a separate accounts call, so
 * an account with nothing in the period still appears — it is returned with zeros.
 */
export function AccountChips({
  accounts,
  selectedId,
  onSelect,
}: {
  accounts: AsyncState<AccountSummary[]>
  selectedId: string | null
  onSelect: (accountId: string | null) => void
}) {
  if (accounts.error) {
    return <ErrorNotice error={accounts.error} />
  }
  const list = accounts.data ?? []
  if (list.length === 0) {
    return null
  }
  return (
    <ul className="choices">
      <li>
        <button
          type="button"
          className={selectedId === null ? 'chip chip-on' : 'chip'}
          aria-pressed={selectedId === null}
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
            aria-pressed={selectedId === account.accountId}
            onClick={() => onSelect(account.accountId)}
          >
            {account.accountName}
            <span className="muted"> · {label(account.provider)}</span>
          </button>
        </li>
      ))}
    </ul>
  )
}
