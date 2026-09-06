import { describe, expect, it } from 'vitest'
import { day, defaultRange, label, money, monthName, share } from './format'

describe('display formatting', () => {
  it('formats money in the account currency', () => {
    expect(money(-24.15)).toBe('-£24.15')
    expect(money(1919.86, 'GBP')).toBe('£1,919.86')
    expect(money(-21.9, 'EUR')).toBe('-€21.90')
  })

  it('turns enum names into readable labels', () => {
    expect(label('GROCERIES')).toBe('Groceries')
    expect(label('CARD_PAYMENT')).toBe('Card payment')
    expect(label('bank_of_scotland')).toBe('Bank of scotland')
  })

  it('formats dates and months without shifting them across a timezone', () => {
    expect(day('2026-08-03')).toBe('03 Aug 2026')
    expect(monthName('2026-08')).toBe('August 2026')
  })

  it('sizes a bar against the largest value in its group', () => {
    expect(share(50, 100)).toBe('50%')
    expect(share(0, 100)).toBe('0%')
    // Nothing to compare against means no bar rather than a division by zero.
    expect(share(10, 0)).toBe('0%')
  })

  it('defaults to a window wide enough to contain a year of statements', () => {
    const range = defaultRange()

    expect(range.from).toMatch(/^\d{4}-\d{2}-01$/)
    expect(range.to).toMatch(/^\d{4}-\d{2}-01$/)
    expect(range.from < range.to).toBe(true)
  })
})
