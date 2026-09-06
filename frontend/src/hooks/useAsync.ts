import { useEffect, useState } from 'react'
import { ApiError } from '../api/client'

export interface AsyncState<T> {
  loading: boolean
  data?: T
  error?: ApiError
}

/**
 * Loads something from the API and tracks the three states the UI has to show:
 * in flight, failed, and loaded.
 *
 * Pass `null` to hold off entirely — used before a user or account has been chosen,
 * so the page does not fire requests it cannot fill in the ids for. A reload triggered
 * while one is in flight discards the earlier answer rather than letting it arrive last
 * and overwrite the newer one.
 */
export function useAsync<T>(
  load: (() => Promise<T>) | null,
  deps: unknown[],
): AsyncState<T> & { reload: () => void } {
  const [state, setState] = useState<AsyncState<T>>({ loading: load !== null })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!load) {
      setState({ loading: false })
      return
    }
    let discarded = false
    setState({ loading: true })
    load()
      .then((data) => {
        if (!discarded) {
          setState({ loading: false, data })
        }
      })
      .catch((error: unknown) => {
        if (!discarded) {
          setState({
            loading: false,
            error:
              error instanceof ApiError
                ? error
                : new ApiError(0, 'UNEXPECTED_ERROR', 'Something went wrong loading this.'),
          })
        }
      })
    return () => {
      discarded = true
    }
    // The loader closes over the deps it needs, so they are listed explicitly.
  }, [...deps, attempt])

  return { ...state, reload: () => setAttempt((previous) => previous + 1) }
}
