import { useEffect, useState } from 'react'
import { api } from './api/client'
import type { DateRange } from './api/types'
import { useAsync } from './hooks/useAsync'
import { defaultRange } from './format'
import { AccountBreakdown } from './components/AccountBreakdown'
import { AccountPicker } from './components/AccountPicker'
import { CategoryBreakdown } from './components/CategoryBreakdown'
import { DateRangePicker } from './components/DateRangePicker'
import { LargestExpenses } from './components/LargestExpenses'
import { MonthlyBreakdown } from './components/MonthlyBreakdown'
import { StatementUpload } from './components/StatementUpload'
import { SummaryPanel } from './components/SummaryPanel'
import { TransactionList } from './components/TransactionList'
import { UserPicker } from './components/UserPicker'

const USER_KEY = 'fapp.userId'
const ACCOUNT_KEY = 'fapp.accountId'

/**
 * The whole dashboard: pick a user, pick an account, import a statement, look at the
 * numbers.
 *
 * Analytics are always user-scoped and narrowed by the selected account, which is how
 * the API is shaped — one set of endpoints with an optional `accountId` rather than two
 * parallel trees. Selecting "all accounts" simply omits it.
 *
 * Every panel loads independently, so one failing request shows an error in its own
 * panel instead of blanking the page.
 */
export default function App() {
  const [userId, setUserId] = useState<string | null>(() => localStorage.getItem(USER_KEY))
  const [accountId, setAccountId] = useState<string | null>(() => localStorage.getItem(ACCOUNT_KEY))
  const [range, setRange] = useState<DateRange>(() => defaultRange())
  // Bumped after an import so every panel reloads against the new transactions.
  const [dataVersion, setDataVersion] = useState(0)

  useEffect(() => {
    if (userId) {
      localStorage.setItem(USER_KEY, userId)
    } else {
      localStorage.removeItem(USER_KEY)
      localStorage.removeItem(ACCOUNT_KEY)
      setAccountId(null)
    }
  }, [userId])

  useEffect(() => {
    if (accountId) {
      localStorage.setItem(ACCOUNT_KEY, accountId)
    } else {
      localStorage.removeItem(ACCOUNT_KEY)
    }
  }, [accountId])

  const rangeIsUsable = range.from < range.to
  const analyticsKey = [userId, accountId, range.from, range.to, dataVersion, rangeIsUsable]

  const accounts = useAsync(
    userId && rangeIsUsable ? () => api.accounts(userId, range) : null,
    [userId, range.from, range.to, dataVersion, rangeIsUsable],
  )
  const summary = useAsync(
    userId && rangeIsUsable ? () => api.summary(userId, range, accountId ?? undefined) : null,
    analyticsKey,
  )
  const categories = useAsync(
    userId && rangeIsUsable ? () => api.categories(userId, range, accountId ?? undefined) : null,
    analyticsKey,
  )
  const monthly = useAsync(
    userId && rangeIsUsable ? () => api.monthly(userId, range, accountId ?? undefined) : null,
    analyticsKey,
  )
  const largest = useAsync(
    userId && rangeIsUsable
      ? () => api.largestExpenses(userId, range, accountId ?? undefined, 10)
      : null,
    analyticsKey,
  )
  const transactions = useAsync(
    accountId ? () => api.transactions(accountId) : null,
    [accountId, dataVersion],
  )

  return (
    <div className="page">
      <header>
        <h1>FAPP</h1>
        <p className="muted">Financial Aggregation &amp; Planning Platform</p>
      </header>

      <UserPicker userId={userId} onChange={setUserId} />

      {!userId ? (
        <p className="muted">Choose a user to see a dashboard.</p>
      ) : (
        <>
          <AccountPicker
            userId={userId}
            range={range}
            accounts={accounts}
            selectedId={accountId}
            onSelect={setAccountId}
            onCreated={() => setDataVersion((version) => version + 1)}
          />

          {accountId ? (
            <StatementUpload
              accountId={accountId}
              onImported={() => setDataVersion((version) => version + 1)}
            />
          ) : (
            <section className="panel">
              <h2>Import a statement</h2>
              <p className="muted">Select a single account to import a statement into it.</p>
            </section>
          )}

          <DateRangePicker range={range} onChange={setRange} />
          {!rangeIsUsable && (
            <div className="notice notice-error" role="alert">
              <strong>INVALID_DATE_RANGE</strong>
              <span>The start date must be before the end date.</span>
            </div>
          )}

          <SummaryPanel state={summary} />
          <div className="grid">
            <CategoryBreakdown state={categories} />
            <MonthlyBreakdown state={monthly} />
          </div>
          <AccountBreakdown state={accounts} />
          <LargestExpenses state={largest} />
          <TransactionList state={transactions} accountSelected={accountId !== null} />
        </>
      )}

      <footer className="muted">
        Every figure shown is calculated by the backend. FAPP informs; it does not advise.
      </footer>
    </div>
  )
}
