import type { DateRange } from '../api/types'
import { PERIOD_SCALES, day, scaleRange } from '../format'
import type { PeriodScale } from '../format'

/**
 * The window analytics are calculated over, chosen by scale rather than by typing two
 * dates — a week, a month or a year is what someone actually wants to look at.
 *
 * Custom exposes the dates, and `to` is labelled exclusive because that is what the API
 * means by it and it changes what a person should enter.
 */
export function PeriodPicker({
  scale,
  range,
  onChange,
}: {
  scale: PeriodScale
  range: DateRange
  onChange: (scale: PeriodScale, range: DateRange) => void
}) {
  return (
    <div className="stack">
      <ul className="choices">
        {PERIOD_SCALES.map((option) => (
          <li key={option.scale}>
            <button
              type="button"
              className={scale === option.scale ? 'chip chip-on' : 'chip'}
              aria-pressed={scale === option.scale}
              onClick={() =>
                onChange(
                  option.scale,
                  option.scale === 'custom' ? range : scaleRange(option.scale),
                )
              }
            >
              {option.label}
            </button>
          </li>
        ))}
      </ul>
      {scale === 'custom' ? (
        <div className="row">
          <label>
            From (inclusive)
            <input
              type="date"
              value={range.from}
              max={range.to}
              onChange={(e) => onChange('custom', { ...range, from: e.target.value })}
            />
          </label>
          <label>
            To (exclusive)
            <input
              type="date"
              value={range.to}
              min={range.from}
              onChange={(e) => onChange('custom', { ...range, to: e.target.value })}
            />
          </label>
        </div>
      ) : (
        <p className="muted">
          {day(range.from)} to {day(range.to)} (exclusive)
        </p>
      )}
    </div>
  )
}
