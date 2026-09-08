import { useState } from 'react'
import { ApiError, api, clearCredentials, setCredentials } from '../api/client'
import { ErrorNotice } from './Async'

/**
 * The whole screen until someone is signed in, so nothing else has to render a
 * half-empty dashboard behind a login form.
 *
 * The backend uses HTTP Basic, so "signing in" means storing credentials and confirming
 * them against `/api/auth/me` — which also returns the user id everything else is scoped
 * by, so there is no id to paste in by hand.
 */
export function SignInScreen({ onSignedIn }: { onSignedIn: (userId: string) => void }) {
  const [registering, setRegistering] = useState(false)
  const [form, setForm] = useState({ email: '', displayName: '', password: '' })
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      if (registering) {
        await api.createUser(form.email.trim(), form.displayName.trim(), form.password)
      }
      setCredentials({ email: form.email.trim(), password: form.password })
      // Confirms the credentials work and tells us who they belong to.
      const user = await api.me()
      setForm({ email: '', displayName: '', password: '' })
      onSignedIn(user.id)
    } catch (caught) {
      clearCredentials()
      setError(caught as ApiError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="signin">
      <div className="signin-brand">
        <h1>FAPP</h1>
        <p className="signin-tagline">its never too late to</p>
      </div>

      <section className="panel signin-card">
        <div className="panel-head">
          <h2>{registering ? 'Create an account' : 'Sign in'}</h2>
        </div>
        {error && <ErrorNotice error={error} />}
        <form onSubmit={submit} className="stack">
          <label>
            Email
            <input
              type="email"
              value={form.email}
              required
              autoComplete="username"
              onChange={(e) => setForm({ ...form, email: e.target.value })}
            />
          </label>
          {registering && (
            <label>
              Display name
              <input
                value={form.displayName}
                required
                onChange={(e) => setForm({ ...form, displayName: e.target.value })}
              />
            </label>
          )}
          <label>
            Password
            <input
              type="password"
              value={form.password}
              required
              minLength={registering ? 12 : undefined}
              autoComplete={registering ? 'new-password' : 'current-password'}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
            />
          </label>
          {registering && (
            <p className="muted">At least 12 characters.</p>
          )}
          <button type="submit" disabled={busy}>
            {busy ? 'Working…' : registering ? 'Create account' : 'Sign in'}
          </button>
        </form>
        <button
          type="button"
          className="btn-quiet"
          onClick={() => {
            setError(null)
            setRegistering(!registering)
          }}
        >
          {registering ? 'I already have an account' : 'I need an account'}
        </button>
      </section>
    </main>
  )
}
