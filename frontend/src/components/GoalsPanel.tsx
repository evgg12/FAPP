import { useState } from 'react'
import { ApiError, api } from '../api/client'
import type { SavingsGoal } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { Async, ErrorNotice } from './Async'
import { day, money, percent, progressWidth } from '../format'

/**
 * Savings goals and how far along each one is.
 *
 * Every figure on the bar — saved, remaining, percentage, met or not — is the backend's
 * `progress` calculation. The browser only decides how wide to draw the bar, and clamps
 * that at 100% for drawing while still printing the real percentage, so saving past a
 * target reads honestly.
 */
export function GoalsPanel({ userId }: { userId: string }) {
  const [version, setVersion] = useState(0)
  const goals = useAsync(() => api.goals(userId), [userId, version])
  const reload = () => setVersion((previous) => previous + 1)

  return (
    <div className="grid grid-2">
      <section className="panel">
        <div className="panel-head">
          <h2>Savings goals</h2>
          {goals.data && <span className="muted">{goals.data.length} tracked</span>}
        </div>
        <Async state={goals} empty="No goals yet. Add one to start tracking progress." lines={4}>
          {(list) => (
            <div className="stack">
              {list.map((goal) => (
                <GoalRow key={goal.id} userId={userId} goal={goal} onChanged={reload} />
              ))}
            </div>
          )}
        </Async>
      </section>
      <NewGoal userId={userId} onCreated={reload} />
    </div>
  )
}

function GoalRow({
  userId,
  goal,
  onChanged,
}: {
  userId: string
  goal: SavingsGoal
  onChanged: () => void
}) {
  const [saved, setSaved] = useState(String(goal.currentAmount))
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  async function run(action: () => Promise<unknown>) {
    setError(null)
    setBusy(true)
    try {
      await action()
      onChanged()
    } catch (caught) {
      setError(caught as ApiError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <article className="goal">
      <div className="spread">
        <h3>{goal.name}</h3>
        <div className="row row-tight">
          <button
            type="button"
            className={goal.featured ? 'btn-star btn-star-active' : 'btn-star'}
            disabled={busy}
            aria-pressed={goal.featured}
            aria-label={goal.featured ? `Unfeature ${goal.name}` : `Feature ${goal.name}`}
            onClick={() =>
              run(() =>
                goal.featured
                  ? api.unfeatureGoal(userId, goal.id)
                  : api.featureGoal(userId, goal.id),
              )
            }
          >
            {goal.featured ? '★ Featured' : '☆ Feature'}
          </button>
          <span className={goal.achieved ? 'badge' : 'badge badge-open'}>
            {goal.achieved ? 'Met' : percent(goal.percentageComplete)}
          </span>
        </div>
      </div>
      <div className="bar-track">
        <div
          className="bar-fill bar-goal"
          style={{ width: progressWidth(goal.percentageComplete) }}
        />
      </div>
      <div className="kv">
        <span className="muted">Saved</span>
        <span>
          {money(goal.currentAmount, goal.currency)} of{' '}
          {money(goal.targetAmount, goal.currency)}
        </span>
      </div>
      <div className="kv">
        <span className="muted">Still to save</span>
        <span>{money(goal.remainingAmount, goal.currency)}</span>
      </div>
      <div className="kv">
        <span className="muted">Target date</span>
        <span>{day(goal.targetDate)}</span>
      </div>
      {error && <ErrorNotice error={error} />}
      <div className="row row-tight">
        <label>
          Amount saved
          <input
            type="number"
            step="0.01"
            min="0"
            value={saved}
            aria-label={`Amount saved towards ${goal.name}`}
            onChange={(e) => setSaved(e.target.value)}
          />
        </label>
        <button
          type="button"
          className="btn-quiet"
          disabled={busy || saved.trim() === ''}
          onClick={() =>
            run(() =>
              api.updateGoal(userId, goal.id, {
                name: goal.name,
                targetAmount: goal.targetAmount,
                currentAmount: Number(saved),
                targetDate: goal.targetDate,
              }),
            )
          }
        >
          {busy ? 'Saving…' : 'Update'}
        </button>
        <button
          type="button"
          className="btn-danger"
          disabled={busy}
          onClick={() => {
            if (window.confirm(`Delete the goal "${goal.name}"?`)) {
              run(() => api.deleteGoal(userId, goal.id))
            }
          }}
        >
          Delete
        </button>
      </div>
    </article>
  )
}

function NewGoal({ userId, onCreated }: { userId: string; onCreated: () => void }) {
  const [form, setForm] = useState({ name: '', targetAmount: '', targetDate: '' })
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await api.createGoal(userId, {
        name: form.name.trim(),
        targetAmount: Number(form.targetAmount),
        currency: 'GBP',
        targetDate: form.targetDate,
      })
      setForm({ name: '', targetAmount: '', targetDate: '' })
      onCreated()
    } catch (caught) {
      setError(caught as ApiError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>New goal</h2>
      </div>
      {error && <ErrorNotice error={error} />}
      <form onSubmit={submit} className="form-grid">
        <label>
          Name
          <input
            value={form.name}
            required
            maxLength={120}
            placeholder="Emergency fund"
            onChange={(e) => setForm({ ...form, name: e.target.value })}
          />
        </label>
        <label>
          Target amount
          <input
            type="number"
            step="0.01"
            min="0.01"
            required
            value={form.targetAmount}
            placeholder="3000.00"
            onChange={(e) => setForm({ ...form, targetAmount: e.target.value })}
          />
        </label>
        <label>
          Target date
          <input
            type="date"
            required
            value={form.targetDate}
            onChange={(e) => setForm({ ...form, targetDate: e.target.value })}
          />
        </label>
        <button type="submit" className="btn-wide" disabled={busy}>
          {busy ? 'Adding…' : 'Add goal'}
        </button>
      </form>
      <p className="muted">
        Goals hold a target and what you have saved towards it. Progress is calculated by
        the backend; the simulator can show what a change would do to the date.
      </p>
    </section>
  )
}
