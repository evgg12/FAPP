import { useState } from 'react'
import { ApiError, api } from '../api/client'
import type { RecategorisationResult } from '../api/types'
import { ErrorNotice } from './Async'

/**
 * Reapplies the current merchant rules to transactions that were imported before those
 * rules existed.
 *
 * Only transactions the adapter left uncategorised are considered, so a category set by
 * hand or by a bank's own labelling is never overwritten. The counts come back from the
 * backend and are worth showing: "examined 400, changed 0" is a useful answer.
 */
export function RecategorisePanel({
  userId,
  onDone,
}: {
  userId: string
  onDone: () => void
}) {
  const [result, setResult] = useState<RecategorisationResult | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  async function run() {
    setError(null)
    setResult(null)
    setBusy(true)
    try {
      setResult(await api.recategorise(userId))
      onDone()
    } catch (caught) {
      setError(caught as ApiError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Recategorise transactions</h2>
      </div>
      <p className="muted">
        Applies today's merchant rules to transactions imported before them. Categories
        you or your bank set are left alone.
      </p>
      {error && <ErrorNotice error={error} />}
      {result && (
        <div className="notice notice-ok">
          <strong>Done</strong>
          <span>
            {result.recategorised} of {result.examined} uncategorised transactions were
            matched to a category.
          </span>
        </div>
      )}
      <div>
        <button type="button" className="btn-quiet btn-wide" disabled={busy} onClick={run}>
          {busy ? 'Working…' : 'Recategorise now'}
        </button>
      </div>
    </section>
  )
}
