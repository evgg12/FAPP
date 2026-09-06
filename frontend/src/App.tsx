import { useEffect, useMemo, useRef, useState } from 'react'
import { api, clearCredentials, currentEmail } from './api/client'
import type { DateRange } from './api/types'
import { useAsync } from './hooks/useAsync'
import {
  currentYearMonth,
  historyRange,
  latestMonthWithData,
  monthRange,
  scaleRange,
  yearsWithData,
} from './format'
import type { PeriodScale } from './format'
import { AccountBreakdown } from './components/AccountBreakdown'
import { AccountChips } from './components/AccountChips'
import { AccountForm } from './components/AccountForm'
import { CategoryBreakdown } from './components/CategoryBreakdown'
import { GoalsPanel } from './components/GoalsPanel'
import { LargestExpenses } from './components/LargestExpenses'
import { ManageAccounts } from './components/ManageAccounts'
import { PeriodPicker } from './components/PeriodPicker'
import { RecategorisePanel } from './components/RecategorisePanel'
import { SignInScreen } from './components/SignInScreen'
import { StatementUpload } from './components/StatementUpload'
import { SummaryPanel } from './components/SummaryPanel'
import { TransactionList } from './components/TransactionList'

const ACCOUNT_KEY = 'fapp.accountId'

type View = 'dashboard' | 'transactions' | 'goals' | 'accounts'

const VIEWS: { view: View; label: string }[] = [
  { view: 'dashboard', label: 'Dashboard' },
  { view: 'transactions', label: 'Transactions' },
  { view: 'goals', label: 'Goals' },
  { view: 'accounts', label: 'Accounts' },
]

/** Which views the account and period controls actually change. */
const NEEDS_ACCOUNT: View[] = ['dashboard', 'transactions']
const NEEDS_PERIOD: View[] = ['dashboard']

/**
 * The application shell: sign in, choose what you are looking at, choose the account and
 * period it is calculated over.
 *
 * Analytics are always user-scoped and narrowed by the selected account, which is how the
 * API is shaped — one set of endpoints with an optional `accountId` rather than two
 * parallel trees. Selecting "all accounts" simply omits it.
 *
 * Every panel loads independently, so one failing request shows an error inside its own
 * panel instead of blanking the page.
 */
export default function App() {
  const [userId, setUserId] = useState<string | null>(null)
  const [email, setEmail] = useState<string | null>(() => currentEmail())
  const [checkingSession, setCheckingSession] = useState(() => currentEmail() !== null)
  const [view, setView] = useState<View>('dashboard')
  const [accountId, setAccountId] = useState<string | null>(() => localStorage.getItem(ACCOUNT_KEY))
  const [scale, setScale] = useState<PeriodScale>('month')
  const [month, setMonth] = useState(() => currentYearMonth())
  const [range, setRange] = useState<DateRange>(() => scaleRange('month'))
  // Bumped after an import or a recategorisation so every panel reloads.
  const [dataVersion, setDataVersion] = useState(0)

  // Credentials survive a reload within the tab, so the session is re-established
  // rather than making the user sign in again.
  useEffect(() => {
    if (!email || userId) {
      return
    }
    api
      .me()
      .then((user) => setUserId(user.id))
      .catch(() => {
        clearCredentials()
        setEmail(null)
        setUserId(null)
      })
      .finally(() => setCheckingSession(false))
  }, [email, userId])

  useEffect(() => {
    if (accountId) {
      localStorage.setItem(ACCOUNT_KEY, accountId)
    } else {
      localStorage.removeItem(ACCOUNT_KEY)
    }
  }, [accountId])

  const rangeIsUsable = range.from < range.to
  const ready = userId !== null && rangeIsUsable
  const analyticsKey = [userId, accountId, range.from, range.to, dataVersion, rangeIsUsable]

  const accounts = useAsync(
    userId && rangeIsUsable ? () => api.accounts(userId, range) : null,
    [userId, range.from, range.to, dataVersion, rangeIsUsable],
  )
  const summary = useAsync(
    ready ? () => api.summary(userId!, range, accountId ?? undefined) : null,
    analyticsKey,
  )
  const categories = useAsync(
    ready ? () => api.categories(userId!, range, accountId ?? undefined) : null,
    analyticsKey,
  )
  const largest = useAsync(
    ready ? () => api.largestExpenses(userId!, range, accountId ?? undefined, 10) : null,
    analyticsKey,
  )
  /*
   * One wide query, only to find out which months hold anything. It is what makes the
   * month dropdown default to the last month imported rather than to whatever month it
   * happens to be in the real world.
   */
  const history = useAsync(
    userId ? () => api.monthly(userId, historyRange()) : null,
    [userId, dataVersion],
  )
  const latestMonth = useMemo(
    () => (history.data ? latestMonthWithData(history.data) : null),
    [history.data],
  )
  const years = useMemo(() => yearsWithData(history.data ?? []), [history.data])

  // Only until the user picks a month themselves; after that their choice stands.
  const monthChosen = useRef(false)
  useEffect(() => {
    if (monthChosen.current || !latestMonth) {
      return
    }
    setMonth(latestMonth)
    setRange((current) => (scale === 'month' ? monthRange(latestMonth) : current))
  }, [latestMonth, scale])

  const transactions = useAsync(
    accountId ? () => api.transactions(accountId) : null,
    [accountId, dataVersion],
  )

  if (!userId) {
    if (checkingSession) {
      return (
        <main className="signin">
          <p className="muted" role="status">
            Loading…
          </p>
        </main>
      )
    }
    return (
      <SignInScreen
        onSignedIn={(signedInUserId) => {
          setEmail(currentEmail())
          setUserId(signedInUserId)
        }}
      />
    )
  }

  const noAccountsYet = accounts.data !== undefined && accounts.data.length === 0
  const showAccountControl = NEEDS_ACCOUNT.includes(view) && !noAccountsYet
  const showPeriodControl = NEEDS_PERIOD.includes(view)

  return (
    <div className="shell">
      <header className="topbar">
        <div className="topbar-inner">
          <div className="brandbar">
            <div className="brand">
              <h1>FAPP</h1>
              <span className="brand-sub">Financial Aggregation &amp; Planning</span>
            </div>
            <div className="whoami">
              <span className="whoami-email">{email}</span>
              <button
                type="button"
                className="btn-quiet"
                onClick={() => {
                  clearCredentials()
                  setEmail(null)
                  setUserId(null)
                  setAccountId(null)
                  setView('dashboard')
                }}
              >
                Sign out
              </button>
            </div>
          </div>
          <nav aria-label="Sections">
            <ul className="tabs">
              {VIEWS.map((item) => (
                <li key={item.view}>
                  <button
                    type="button"
                    className="tab"
                    aria-current={view === item.view ? 'page' : undefined}
                    onClick={() => setView(item.view)}
                  >
                    {item.label}
                  </button>
                </li>
              ))}
            </ul>
          </nav>
        </div>
      </header>

      {(showAccountControl || showPeriodControl) && (
        <div className="toolbar">
          <div className="toolbar-inner stack">
            {showAccountControl && (
              <AccountChips
                accounts={accounts}
                selectedId={accountId}
                onSelect={setAccountId}
              />
            )}
            {showPeriodControl && (
              <PeriodPicker
                scale={scale}
                month={month}
                years={years}
                range={range}
                onChange={(nextScale, nextMonth) => {
                  monthChosen.current = true
                  setScale(nextScale)
                  setMonth(nextMonth)
                  setRange(nextScale === 'month' ? monthRange(nextMonth) : scaleRange('year'))
                }}
              />
            )}
          </div>
        </div>
      )}

      <main className="main">
        {!rangeIsUsable && (
          <div className="notice notice-error" role="alert">
            <strong>INVALID_DATE_RANGE</strong>
            <span>The start date must be before the end date.</span>
          </div>
        )}

        {noAccountsYet && view !== 'accounts' && (
          <div className="notice notice-info">
            <strong>Get started</strong>
            <span>
              Add an account under Accounts, then import a CSV statement to see your
              figures here.
            </span>
          </div>
        )}

        {view === 'dashboard' && (
          <>
            <SummaryPanel state={summary} />
            <div className="grid grid-2">
              <CategoryBreakdown state={categories} />
              <AccountBreakdown state={accounts} />
            </div>
            <LargestExpenses state={largest} />
            <TransactionList
              state={transactions}
              accountSelected={accountId !== null}
              limit={8}
            />
          </>
        )}

        {view === 'transactions' && (
          <TransactionList state={transactions} accountSelected={accountId !== null} />
        )}

        {view === 'goals' && <GoalsPanel userId={userId} />}

        {view === 'accounts' && (
          <>
            <AccountForm
              userId={userId}
              onCreated={(createdId) => {
                setAccountId(createdId)
                setDataVersion((version) => version + 1)
              }}
            />
            <ManageAccounts
              accounts={accounts}
              onRemoved={(removedId) => {
                if (removedId === accountId) {
                  setAccountId(null)
                }
                setDataVersion((version) => version + 1)
              }}
            />
            {accountId ? (
              <StatementUpload
                accountId={accountId}
                onImported={() => setDataVersion((version) => version + 1)}
              />
            ) : (
              <section className="panel">
                <div className="panel-head">
                  <h2>Import a statement</h2>
                </div>
                <p className="empty">
                  Select a single account below to import a statement into it.
                </p>
                <AccountChips
                  accounts={accounts}
                  selectedId={accountId}
                  onSelect={setAccountId}
                />
              </section>
            )}
            <RecategorisePanel
              userId={userId}
              onDone={() => setDataVersion((version) => version + 1)}
            />
          </>
        )}
      </main>

      <footer className="foot">
        <div className="foot-inner">
          <p className="muted">
            Every figure shown is calculated by the backend. FAPP informs; it does not
            advise.
          </p>
        </div>
      </footer>
    </div>
  )
}
