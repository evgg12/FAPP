import { useState } from 'react'
import { ApiError, api } from '../api/client'
import { ErrorNotice } from './Async'

/**
 * Chooses whose data the dashboard shows.
 *
 * The API has no authentication and no way to list users, so identity is an explicit id
 * here: create a user, or paste one you already have. The chosen id is remembered in
 * the browser so a reload does not start over. This is a stand-in for signing in, not a
 * substitute for it.
 */
export function UserPicker({
  userId,
  onChange,
}: {
  userId: string | null
  onChange: (userId: string | null) => void
}) {
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [pasted, setPasted] = useState('')
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  async function create(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const user = await api.createUser(email.trim(), displayName.trim())
      onChange(user.id)
      setEmail('')
      setDisplayName('')
    } catch (caught) {
      setError(caught as ApiError)
    } finally {
      setBusy(false)
    }
  }

  if (userId) {
    return (
      <section className="panel">
        <h2>User</h2>
        <p className="row">
          <code>{userId}</code>
          <button type="button" onClick={() => onChange(null)}>
            Switch user
          </button>
        </p>
      </section>
    )
  }

  return (
    <section className="panel">
      <h2>User</h2>
      <p className="muted">
        There is no sign-in yet, so pick a user by id. Create one, or paste an id you
        already have.
      </p>
      {error && <ErrorNotice error={error} />}
      <form onSubmit={create} className="stack">
        <label>
          Email
          <input
            type="email"
            value={email}
            required
            placeholder="you@example.com"
            onChange={(e) => setEmail(e.target.value)}
          />
        </label>
        <label>
          Display name
          <input
            value={displayName}
            required
            placeholder="Your name"
            onChange={(e) => setDisplayName(e.target.value)}
          />
        </label>
        <button type="submit" disabled={busy}>
          {busy ? 'Creating…' : 'Create user'}
        </button>
      </form>
      <form
        className="stack"
        onSubmit={(event) => {
          event.preventDefault()
          if (pasted.trim()) {
            onChange(pasted.trim())
          }
        }}
      >
        <label>
          Or use an existing user id
          <input
            value={pasted}
            placeholder="00000000-0000-0000-0000-000000000000"
            onChange={(e) => setPasted(e.target.value)}
          />
        </label>
        <button type="submit">Use this id</button>
      </form>
    </section>
  )
}
