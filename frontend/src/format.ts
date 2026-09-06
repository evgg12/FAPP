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
