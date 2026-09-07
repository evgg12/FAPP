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
export type PeriodScale = 'month' | 'year'

export const PERIOD_SCALES: { scale: PeriodScale; label: string }[] = [
  { scale: 'month', label: 'Month' },
  { scale: 'year', label: 'Annual' },
]

/** `2026-08`, the month a month picker starts on. */
export function currentYearMonth(): string {
  const now = new Date()
  return `${now.getUTCFullYear()}-${String(now.getUTCMonth() + 1).padStart(2, '0')}`
}

/**
 * The dates one calendar month covers. `to` is exclusive, matching the API, so
 * `2026-08` is 2026-08-01 up to but not including 2026-09-01.
 */
export function monthRange(yearMonth: string): { from: string; to: string } {
  const [year, month] = yearMonth.split('-').map(Number)
  return {
    from: isoDay(new Date(Date.UTC(year, month - 1, 1))),
    to: isoDay(new Date(Date.UTC(year, month, 1))),
  }
}

/** The dates a named scale covers: the current month, or this year so far. */
export function scaleRange(scale: PeriodScale): { from: string; to: string } {
  if (scale === 'month') {
    return monthRange(currentYearMonth())
  }
  const now = new Date()
  // Year to date, ending after today so today's transactions are included.
  return {
    from: isoDay(new Date(Date.UTC(now.getUTCFullYear(), 0, 1))),
    to: isoDay(new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1))),
  }
}

/** A percentage the backend calculated, rendered for reading. */
export function percent(value: number): string {
  return `${value.toFixed(value >= 10 ? 0 : 1)}%`
}

/** How far to fill a progress bar. Clamped for drawing only; the figure is not. */
export function progressWidth(percentage: number): string {
  return `${Math.min(Math.max(percentage, 0), 100)}%`
}

export const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

/** A wide window used only to find out which months actually hold transactions. */
export function historyRange(years = 6): { from: string; to: string } {
  const now = new Date()
  return {
    from: `${now.getUTCFullYear() - years}-01-01`,
    to: isoDay(new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 1))),
  }
}

/**
 * The most recent month that actually has transactions in it, which is a more useful
 * default than the real-world current month — a statement is usually imported after the
 * month it covers has ended.
 */
export function latestMonthWithData(
  months: { month: string; transactionCount: number }[],
): string | null {
  const withData = months.filter((month) => month.transactionCount > 0).map((month) => month.month)
  return withData.length === 0 ? null : withData.sort().at(-1)!
}

/** The years to offer in the year dropdown: those with data, newest first. */
export function yearsWithData(
  months: { month: string; transactionCount: number }[],
): number[] {
  const years = new Set(
    months.filter((month) => month.transactionCount > 0).map((month) => Number(month.month.slice(0, 4))),
  )
  years.add(new Date().getUTCFullYear())
  return [...years].sort((a, b) => b - a)
}

/** The twelve months ending with the current one. */
export function twelveMonthRange(): { from: string; to: string } {
  const now = new Date()
  return {
    from: isoDay(new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 11, 1))),
    to: isoDay(new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 1))),
  }
}

/** `2026-08` as `A`, for an axis that has room for one letter per month. */
export function monthInitial(yearMonth: string): string {
  return MONTH_NAMES[Number(yearMonth.slice(5, 7)) - 1].charAt(0)
}
