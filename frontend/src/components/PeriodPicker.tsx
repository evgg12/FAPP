import type { DateRange } from '../api/types'
import { MONTH_NAMES, PERIOD_SCALES, day } from '../format'
import type { PeriodScale } from '../format'

/**
 * The window analytics are calculated over: one calendar month, or the year so far.
 *
 * The month is chosen from two dropdowns rather than typed, and turns into the half-open
 * range the API expects — `2026-08` is 2026-08-01 up to but not including 2026-09-01 —
 * so the boundary cannot be got wrong by hand. The years offered are the ones that
 * actually hold transactions.
 */
export function PeriodPicker({
  scale,
  month,
  years,
  range,
  onChange,
}: {
  scale: PeriodScale
  month: string
  years: number[]
  range: DateRange
  onChange: (scale: PeriodScale, month: string) => void
}) {
  const [year, monthNumber] = month.split('-')

  return (
    <div className="row row-tight">
      <ul className="choices">
        {PERIOD_SCALES.map((option) => (
          <li key={option.scale}>
            <button
              type="button"
              className={scale === option.scale ? 'chip chip-on' : 'chip'}
              aria-pressed={scale === option.scale}
              onClick={() => onChange(option.scale, month)}
            >
              {option.label}
            </button>
          </li>
        ))}
      </ul>
      {scale === 'month' ? (
        <div className="period-select-group">
          <select
            className="period-select"
            value={monthNumber}
            aria-label="Month"
            onChange={(e) => onChange('month', `${year}-${e.target.value}`)}
          >
            {MONTH_NAMES.map((name, index) => (
              <option key={name} value={String(index + 1).padStart(2, '0')}>
                {name}
              </option>
            ))}
          </select>
          <select
            className="period-select"
            value={year}
            aria-label="Year"
            onChange={(e) => onChange('month', `${e.target.value}-${monthNumber}`)}
          >
            {years.map((option) => (
              <option key={option} value={String(option)}>
                {option}
              </option>
            ))}
          </select>
        </div>
      ) : (
        <p className="muted">
          {day(range.from)} to {day(range.to)} (exclusive)
        </p>
      )}
    </div>
  )
}
