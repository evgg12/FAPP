/**
 * The shapes the FAPP REST API actually returns, transcribed from the response records
 * in `com.fapp.api` and `com.fapp.analytics`.
 *
 * Java `BigDecimal` arrives as a JSON number and `LocalDate` as `YYYY-MM-DD`, so money
 * is typed `number` and dates `string`. The frontend formats those for display and
 * never does financial arithmetic on them: every total shown comes from the backend,
 * which is the only place allowed to calculate one.
 */

export type AccountType = 'CURRENT' | 'SAVINGS' | 'CREDIT_CARD' | 'OTHER'

export type Category =
  | 'GROCERIES'
  | 'RESTAURANTS'
  | 'TRANSPORT'
  | 'SUBSCRIPTIONS'
  | 'BILLS'
  | 'SHOPPING'
  | 'ENTERTAINMENT'
  | 'INCOME'
  | 'TRANSFER'
  | 'SAVINGS'
  | 'UNCATEGORISED'

export type CategorySource = 'USER' | 'RULE' | 'ADAPTER' | 'DEFAULT'

export type TransactionType =
  | 'CARD_PAYMENT'
  | 'DIRECT_DEBIT'
  | 'STANDING_ORDER'
  | 'BANK_TRANSFER'
  | 'CASH_WITHDRAWAL'
  | 'FEE'
  | 'INTEREST'
  | 'REFUND'
  | 'OTHER'

export interface User {
  id: string
  email: string
  displayName: string
  createdAt: string
}

export interface Account {
  id: string
  userId: string
  provider: string
  displayName: string
  accountType: AccountType
  currency: string
}

export interface StatementImport {
  id: string
  accountId: string
  provider: string
  periodStart: string
  periodEnd: string
  rowCount: number
  importedCount: number
  duplicateCount: number
  importedAt: string
}

export interface Transaction {
  id: string
  bookingDate: string
  /** Absent unless the bank supplied a separate date for when it happened. */
  occurredOn?: string
  /** Signed: negative left the account, positive arrived in it. */
  amount: number
  currency: string
  originalAmount?: number
  originalCurrency?: string
  description: string
  merchant?: string
  category: Category
  categorySource: CategorySource
  transactionType: TransactionType
  externalId?: string
}

/** `from` is inclusive, `to` is exclusive. Both are required by every analytics call. */
export interface DateRange {
  from: string
  to: string
}

export interface FinancialSummary {
  period: DateRange
  income: number
  /** Reported positive: 250 means 250 spent. */
  expenditure: number
  netSavings: number
  transactionCount: number
}

export interface CategorySummary {
  category: Category
  income: number
  expenditure: number
  net: number
  transactionCount: number
}

export interface MonthlySummary {
  /** `YYYY-MM`. */
  month: string
  income: number
  expenditure: number
  netSavings: number
  transactionCount: number
}

export interface AccountSummary {
  accountId: string
  provider: string
  accountName: string
  income: number
  expenditure: number
  netSavings: number
  transactionCount: number
}

export interface LargestExpense {
  transactionId: string
  bookingDate: string
  description: string
  merchant?: string
  /** Positive: the size of the outgoing. */
  amount: number
  category: Category
  accountId: string
  accountName: string
}

/** The single error shape the API returns for every failed request. */
export interface ApiErrorBody {
  code: string
  message: string
  fields?: Record<string, string>
  timestamp: string
}

/** A savings goal with the progress the backend calculated. */
export interface SavingsGoal {
  id: string
  userId: string
  name: string
  currency: string
  targetAmount: number
  currentAmount: number
  remainingAmount: number
  percentageComplete: number
  achieved: boolean
  targetDate: string
  createdAt: string
  updatedAt: string
}

/** Monthly income, expenditure and what is left. */
export interface MonthlyFigures {
  income: number
  expenditure: number
  net: number
}

/** What a scenario would do to a savings goal. */
export interface GoalOutlook {
  goalId?: string
  goalName: string
  remaining: number
  baselineMonthsToTarget?: number
  scenarioMonthsToTarget?: number
  baselineProjectedDate?: string
  scenarioProjectedDate?: string
  onTrackBefore: boolean
  onTrackAfter: boolean
}

/**
 * A what-if answer. `baseline` is real history; everything named `scenario` is
 * hypothetical and stored nowhere.
 */
export interface SimulationResult {
  baselinePeriod: DateRange
  monthsOfHistory: number
  horizonMonths: number
  baseline: MonthlyFigures
  scenario: MonthlyFigures
  monthlyNetChange: number
  baselineHorizonNet: number
  scenarioHorizonNet: number
  horizonNetChange: number
  goalOutlook?: GoalOutlook
}

/** What a recategorisation pass considered and changed. */
export interface RecategorisationResult {
  examined: number
  recategorised: number
}

/** A hypothetical change to test. All figures optional. */
export interface Scenario {
  from: string
  to: string
  accountId?: string
  horizonMonths: number
  oneOffPurchase?: number
  monthlyExpenditureChange?: number
  monthlyIncomeChange?: number
  goalId?: string
}
