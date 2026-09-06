import { useRef, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { StatementImport } from '../api/types'
import { ErrorNotice } from './Async'
import { day } from '../format'

/**
 * Uploads a CSV to the selected account.
 *
 * Which bank's format it is read as comes from the account, not from anything chosen
 * here — that is how the backend works and the reason there is no format selector.
 *
 * The result is worth showing in full: re-uploading an overlapping statement is a normal
 * thing to do, and the imported/duplicate counts are what tell the user it was
 * reconciled rather than doubled.
 */
export function StatementUpload({
  accountId,
  onImported,
}: {
  accountId: string
  onImported: () => void
}) {
  const input = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [result, setResult] = useState<StatementImport | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  async function upload(event: React.FormEvent) {
    event.preventDefault()
    if (!file) {
      return
    }
    setError(null)
    setResult(null)
    setBusy(true)
    try {
      setResult(await api.uploadStatement(accountId, file))
      setFile(null)
      if (input.current) {
        input.current.value = ''
      }
      onImported()
    } catch (caught) {
      setError(caught as ApiError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Import a statement</h2>
      </div>
      <form onSubmit={upload} className="form-grid">
        <input
          ref={input}
          type="file"
          accept=".csv,text/csv"
          aria-label="Statement CSV"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
        />
        <button type="submit" className="btn-wide" disabled={busy || !file}>
          {busy ? 'Importing…' : 'Import'}
        </button>
      </form>

      {error && <ErrorNotice error={error} />}

      {result && (
        <div className="notice notice-ok">
          <strong>Imported</strong>
          <span>
            {result.importedCount} new, {result.duplicateCount} already held, of{' '}
            {result.rowCount} rows — covering {day(result.periodStart)} to{' '}
            {day(result.periodEnd)}.
          </span>
        </div>
      )}
    </section>
  )
}
