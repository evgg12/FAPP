/** Display formatting only. No figure shown here is calculated in the browser. */

export function money(amount: number, currency = 'GBP'): string {
  return new Intl.NumberFormat('en-GB', { style: 'currency', currency }).format(amount)
}

/** `GROCERIES` reads better as `Groceries`, and `CARD_PAYMENT` as `Card payment`. */
export function label(value: string): string {
  const words = value.replace(/_/g, ' ').toLowerCase()
  return words.charAt(0).toUpperCase() + words.slice(1)
}

export function day(isoDate: string): string {
  return new Date(`${isoDate}T00:00:00Z`).toLocaleDateString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  })
}

/** `2026-08` as `August 2026`. */
export function monthName(yearMonth: string): string {
  return new Date(`${yearMonth}-01T00:00:00Z`).toLocaleDateString('en-GB', {
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  })
}

/** How wide to draw a bar, as a percentage of the largest value in its group. */
export function share(value: number, largest: number): string {
  if (largest <= 0) {
    return '0%'
  }
  return `${Math.max((value / largest) * 100, value > 0 ? 1 : 0)}%`
}

/** First day of the month `monthsAgo` before this one, and the first of next month. */
export function defaultRange(monthsAgo = 11): { from: string; to: string } {
  const now = new Date()
  const from = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - monthsAgo, 1))
  const to = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 1))
  return { from: isoDay(from), to: isoDay(to) }
}

function isoDay(date: Date): string {
  return date.toISOString().slice(0, 10)
}

/** A named window of time the dashboard can be looked at through. */
export type PeriodScale = 'week' | 'month' | 'quarter' | 'year' | 'twelveMonths' | 'custom'

export const PERIOD_SCALES: { scale: PeriodScale; label: string }[] = [
  { scale: 'week', label: 'This week' },
  { scale: 'month', label: 'This month' },
  { scale: 'quarter', label: 'Last 3 months' },
  { scale: 'year', label: 'Year to date' },
  { scale: 'twelveMonths', label: 'Last 12 months' },
  { scale: 'custom', label: 'Custom' },
]

/**
 * The dates a named scale covers. `to` is exclusive, matching the API, so "this month"
 * ends on the first of next month and today's transactions are included.
 */
export function scaleRange(scale: PeriodScale): { from: string; to: string } {
  const now = new Date()
  const year = now.getUTCFullYear()
  const month = now.getUTCMonth()
  const tomorrow = new Date(Date.UTC(year, month, now.getUTCDate() + 1))
  switch (scale) {
    case 'week': {
      // Monday-first, which is how a UK bank week reads.
      const weekday = (now.getUTCDay() + 6) % 7
      return {
        from: isoDay(new Date(Date.UTC(year, month, now.getUTCDate() - weekday))),
        to: isoDay(tomorrow),
      }
    }
    case 'month':
      return { from: isoDay(new Date(Date.UTC(year, month, 1))), to: isoDay(tomorrow) }
    case 'quarter':
      return {
        from: isoDay(new Date(Date.UTC(year, month - 2, 1))),
        to: isoDay(new Date(Date.UTC(year, month + 1, 1))),
      }
    case 'year':
      return { from: isoDay(new Date(Date.UTC(year, 0, 1))), to: isoDay(tomorrow) }
    case 'twelveMonths':
    case 'custom':
    default:
      return defaultRange()
  }
}

/** `2026-08` as `Aug 26`, for axis labels where the full name will not fit. */
export function shortMonth(yearMonth: string): string {
  const date = new Date(`${yearMonth}-01T00:00:00Z`)
  return `${date.toLocaleDateString('en-GB', { month: 'short', timeZone: 'UTC' })} ${String(
    date.getUTCFullYear(),
  ).slice(2)}`
}

/** A percentage the backend calculated, rendered for reading. */
export function percent(value: number): string {
  return `${value.toFixed(value >= 10 ? 0 : 1)}%`
}

/** How far to fill a progress bar. Clamped for drawing only; the figure is not. */
export function progressWidth(percentage: number): string {
  return `${Math.min(Math.max(percentage, 0), 100)}%`
}
