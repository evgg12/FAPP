import type { DateRange } from '../api/types'

/**
 * The window analytics are calculated over. The API requires both dates on every call
 * and never guesses, so they are always visible here rather than hidden in a default.
 * `to` is exclusive, which is stated because it changes what a caller should enter.
 */
export function DateRangePicker({
  range,
  onChange,
}: {
  range: DateRange
  onChange: (range: DateRange) => void
}) {
  return (
    <section className="panel">
      <h2>Period</h2>
      <div className="row wrap">
        <label>
          From (inclusive)
          <input
            type="date"
            value={range.from}
            max={range.to}
            onChange={(e) => onChange({ ...range, from: e.target.value })}
          />
        </label>
        <label>
          To (exclusive)
          <input
            type="date"
            value={range.to}
            min={range.from}
            onChange={(e) => onChange({ ...range, to: e.target.value })}
          />
        </label>
      </div>
    </section>
  )
}
