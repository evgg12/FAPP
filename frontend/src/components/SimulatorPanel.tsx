import { useState } from 'react'
import { ApiError, api } from '../api/client'
import type { DateRange, SimulationResult } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { ErrorNotice } from './Async'
import { day, money } from '../format'

/**
 * What-if. Describe a change and the backend recalculates the same monthly figures
 * against it.
 *
 * Nothing here is written down. The baseline is the user's real history over the
 * selected period; everything labelled "scenario" is a projection the API returned and
 * then forgot, so running one leaves the transaction history untouched.
 */
export function SimulatorPanel({
  userId,
  range,
  accountId,
}: {
  userId: string
  range: DateRange
  accountId: string | null
}) {
  const goals = useAsync(() => api.goals(userId), [userId])
  const [form, setForm] = useState({
    horizonMonths: '12',
    oneOffPurchase: '',
    monthlyExpenditureChange: '',
    monthlyIncomeChange: '',
    goalId: '',
  })
  const [result, setResult] = useState<SimulationResult | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  /** Blank means "no change", which the API expects as an omitted field, not a zero. */
  const optional = (value: string) => (value.trim() === '' ? undefined : Number(value))

  async function run(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      setResult(
        await api.simulate(userId, {
          from: range.from,
          to: range.to,
          accountId: accountId ?? undefined,
          horizonMonths: Number(form.horizonMonths),
          oneOffPurchase: optional(form.oneOffPurchase),
          monthlyExpenditureChange: optional(form.monthlyExpenditureChange),
          monthlyIncomeChange: optional(form.monthlyIncomeChange),
          goalId: form.goalId === '' ? undefined : form.goalId,
        }),
      )
    } catch (caught) {
      setResult(null)
      setError(caught as ApiError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="grid grid-2">
      <section className="panel">
        <div className="panel-head">
          <h2>Financial simulator</h2>
        </div>
        <form onSubmit={run} className="form-grid">
          <label>
            Project forward (months)
            <input
              type="number"
              min="1"
              max="120"
              required
              value={form.horizonMonths}
              onChange={(e) => setForm({ ...form, horizonMonths: e.target.value })}
            />
          </label>
          <label>
            One-off purchase
            <input
              type="number"
              step="0.01"
              min="0"
              placeholder="e.g. 1200"
              value={form.oneOffPurchase}
              onChange={(e) => setForm({ ...form, oneOffPurchase: e.target.value })}
            />
          </label>
          <label>
            Monthly spending change
            <input
              type="number"
              step="0.01"
              placeholder="e.g. -150"
              value={form.monthlyExpenditureChange}
              onChange={(e) =>
                setForm({ ...form, monthlyExpenditureChange: e.target.value })
              }
            />
          </label>
          <label>
            Monthly income change
            <input
              type="number"
              step="0.01"
              placeholder="e.g. 200"
              value={form.monthlyIncomeChange}
              onChange={(e) => setForm({ ...form, monthlyIncomeChange: e.target.value })}
            />
          </label>
          <label>
            Against a goal
            <select
              value={form.goalId}
              onChange={(e) => setForm({ ...form, goalId: e.target.value })}
            >
              <option value="">No goal</option>
              {(goals.data ?? []).map((goal) => (
                <option key={goal.id} value={goal.id}>
                  {goal.name}
                </option>
              ))}
            </select>
          </label>
          <button type="submit" className="btn-wide" disabled={busy}>
            {busy ? 'Calculating…' : 'Run simulation'}
          </button>
        </form>
        <p className="muted">
          The baseline is your real history from {day(range.from)} to {day(range.to)}.
          Leave a field blank to leave it unchanged. Nothing is saved: a simulation
          never touches your transactions.
        </p>
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>Result</h2>
        </div>
        {error && <ErrorNotice error={error} />}
        {!error && !result && (
          <p className="empty">Describe a change and run it to see the projection.</p>
        )}
        {result && <Outcome result={result} />}
      </section>
    </div>
  )
}

function Outcome({ result }: { result: SimulationResult }) {
  const better = result.monthlyNetChange >= 0
  return (
    <div className="stack">
      <div className="compare">
        <div className="compare-col">
          <h3>Baseline, per month</h3>
          <div className="kv">
            <span className="muted">In</span>
            <span>{money(result.baseline.income)}</span>
          </div>
          <div className="kv">
            <span className="muted">Out</span>
            <span>{money(result.baseline.expenditure)}</span>
          </div>
          <div className="kv">
            <span className="muted">Net</span>
            <span className={result.baseline.net < 0 ? 'tone-down' : 'tone-up'}>
              {money(result.baseline.net)}
            </span>
          </div>
        </div>
        <div className="compare-col">
          <h3>Scenario, per month</h3>
          <div className="kv">
            <span className="muted">In</span>
            <span>{money(result.scenario.income)}</span>
          </div>
          <div className="kv">
            <span className="muted">Out</span>
            <span>{money(result.scenario.expenditure)}</span>
          </div>
          <div className="kv">
            <span className="muted">Net</span>
            <span className={result.scenario.net < 0 ? 'tone-down' : 'tone-up'}>
              {money(result.scenario.net)}
            </span>
          </div>
        </div>
      </div>

      <div className={better ? 'notice notice-ok' : 'notice notice-error'}>
        <strong>Monthly difference</strong>
        <span>
          {money(result.monthlyNetChange)} a month, and {money(result.horizonNetChange)}{' '}
          across {result.horizonMonths} months.
        </span>
      </div>

      <div className="kv">
        <span className="muted">Net over {result.horizonMonths} months, unchanged</span>
        <span>{money(result.baselineHorizonNet)}</span>
      </div>
      <div className="kv">
        <span className="muted">Net over {result.horizonMonths} months, scenario</span>
        <span>{money(result.scenarioHorizonNet)}</span>
      </div>

      {result.goalOutlook && <GoalEffect outlook={result.goalOutlook} />}

      <p className="muted">
        Averaged over {result.monthsOfHistory}{' '}
        {result.monthsOfHistory === 1 ? 'month' : 'months'} of history from{' '}
        {day(result.baselinePeriod.from)} to {day(result.baselinePeriod.to)}.
      </p>
    </div>
  )
}

function GoalEffect({ outlook }: { outlook: NonNullable<SimulationResult['goalOutlook']> }) {
  const months = (value?: number) =>
    value === undefined ? 'not at this rate' : `${value} ${value === 1 ? 'month' : 'months'}`
  return (
    <div className="compare-col">
      <h3>Effect on {outlook.goalName}</h3>
      <div className="kv">
        <span className="muted">Still to save</span>
        <span>{money(outlook.remaining)}</span>
      </div>
      <div className="kv">
        <span className="muted">Reached, unchanged</span>
        <span>
          {months(outlook.baselineMonthsToTarget)}
          {outlook.baselineProjectedDate && ` · ${day(outlook.baselineProjectedDate)}`}
        </span>
      </div>
      <div className="kv">
        <span className="muted">Reached, scenario</span>
        <span>
          {months(outlook.scenarioMonthsToTarget)}
          {outlook.scenarioProjectedDate && ` · ${day(outlook.scenarioProjectedDate)}`}
        </span>
      </div>
      <div className="kv">
        <span className="muted">Meets its own target date</span>
        <span>
          {outlook.onTrackBefore ? 'yes' : 'no'} → {outlook.onTrackAfter ? 'yes' : 'no'}
        </span>
      </div>
    </div>
  )
}
