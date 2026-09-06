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
 * A placeholder shaped roughly like the content that is coming, so a panel keeps its
 * height while loading instead of the page jumping as each request lands.
 */
export function Skeleton({ lines = 3 }: { lines?: number }) {
  return (
    <div className="skeleton" aria-hidden="true">
      {Array.from({ length: lines }, (_, index) => (
        <div
          key={index}
          className="skeleton-line"
          style={{ width: `${100 - index * 12}%` }}
        />
      ))}
    </div>
  )
}

/**
 * Renders whichever of the three states applies, so no panel has to repeat the
 * loading/failed/empty handling. An empty array is shown as a message rather than as an
 * empty table, because a period with nothing in it is a normal answer.
 *
 * The visually hidden "Loading…" keeps the state announced to a screen reader — and
 * asserted in the tests — while sighted users get the skeleton.
 */
export function Async<T>({
  state,
  empty = 'Nothing for this period.',
  lines,
  children,
}: {
  state: AsyncState<T>
  empty?: string
  lines?: number
  children: (data: T) => ReactNode
}) {
  if (state.loading) {
    return (
      <div>
        <p className="muted" role="status">
          Loading…
        </p>
        <Skeleton lines={lines} />
      </div>
    )
  }
  if (state.error) {
    return <ErrorNotice error={state.error} />
  }
  if (state.data === undefined) {
    return <p className="empty">{empty}</p>
  }
  if (Array.isArray(state.data) && state.data.length === 0) {
    return <p className="empty">{empty}</p>
  }
  return <>{children(state.data)}</>
}
