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
  | 'CUSTOM'
  | 'UNCATEGORISED'

/** Every category the API accepts, in the order the dropdown offers them. */
export const CATEGORIES: Category[] = [
  'GROCERIES',
  'RESTAURANTS',
  'TRANSPORT',
  'SUBSCRIPTIONS',
  'BILLS',
  'SHOPPING',
  'ENTERTAINMENT',
  'INCOME',
  'TRANSFER',
  'SAVINGS',
  'UNCATEGORISED',
]

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
  /** The user's own label, present only when `category` is `CUSTOM`. */
  customCategory?: string
  categorySource: CategorySource
  transactionType: TransactionType
  externalId?: string
  /** Attached client-side only, when transactions from several accounts are merged. */
  provider?: string
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
  customCategory?: string
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
  featured: boolean
  createdAt: string
  updatedAt: string
}




/** What a recategorisation pass considered and changed. */
export interface RecategorisationResult {
  examined: number
  recategorised: number
}


/**
 * What moved into and out of the savings pot over the period. A movement, not a bank
 * balance: FAPP holds statements, so it can only report what the statements show.
 */
export interface SavingsPot {
  period: DateRange
  paidIn: number
  withdrawn: number
  balance: number
  transactionCount: number
}

/**
 * A transaction as it appears pinned -- individually or inside a group: enough to
 * display, nothing more. `note` is set only on an individual pin.
 */
export interface PinnedTransaction {
  transactionId: string
  accountId: string
  bookingDate: string
  amount: number
  currency: string
  description: string
  merchant?: string
  category: Category
  customCategory?: string
  note?: string
  pinnedAt: string
}

/** A named group of pinned transactions, with optional notes. Organisational only. */
export interface PinnedGroup {
  id: string
  userId: string
  name: string
  notes?: string
  transactions: PinnedTransaction[]
  createdAt: string
  updatedAt: string
}
