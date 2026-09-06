import type { ReactNode } from 'react'
import type { ApiError } from '../api/client'
import type { AsyncState } from '../hooks/useAsync'

export function ErrorNotice({ error }: { error: ApiError }) {
  return (
    <div className="notice notice-error" role="alert">
      <strong>{error.code}</strong>
      <span>{error.message}</span>
      {error.fields && (
        <ul>
          {Object.entries(error.fields).map(([field, message]) => (
            <li key={field}>
              <code>{field}</code>: {message}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/**
 * Renders whichever of the three states applies, so no panel has to repeat the
 * loading/failed/empty handling. An empty array is shown as a message rather than as an
 * empty table, because a period with nothing in it is a normal answer.
 */
export function Async<T>({
  state,
  empty = 'Nothing for this period.',
  children,
}: {
  state: AsyncState<T>
  empty?: string
  children: (data: T) => ReactNode
}) {
  if (state.loading) {
    return <p className="muted">Loading…</p>
  }
  if (state.error) {
    return <ErrorNotice error={state.error} />
  }
  if (state.data === undefined) {
    return <p className="muted">{empty}</p>
  }
  if (Array.isArray(state.data) && state.data.length === 0) {
    return <p className="muted">{empty}</p>
  }
  return <>{children(state.data)}</>
}
