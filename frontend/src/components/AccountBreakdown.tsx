import type { AccountSummary } from '../api/types'
import { Async } from './Async'
import type { AsyncState } from '../hooks/useAsync'
import { label, money } from '../format'

/** Per account and per bank, which is the point of aggregating two banks in one place. */
export function AccountBreakdown({ state }: { state: AsyncState<AccountSummary[]> }) {
  return (
    <section className="panel">
      <div className="panel-head">
        <h2>By account</h2>
      </div>
      <Async state={state} empty="No accounts yet. Add one to get started." lines={4}>
        {(accounts) => (
          <div className="scroll">
            <table className="cards">
              <thead>
                <tr>
                  <th>Account</th>
                  <th>Bank</th>
                  <th className="right">In</th>
                  <th className="right">Out</th>
                  <th className="right">Net</th>
                  <th className="right">Count</th>
                </tr>
              </thead>
              <tbody>
                {accounts.map((account) => (
                  <tr key={account.accountId}>
                    <td data-label="Account" className="card-title">{account.accountName}</td>
                    <td data-label="Bank">{label(account.provider)}</td>
                    <td data-label="In" className="right">{money(account.income)}</td>
                    <td data-label="Out" className="right">{money(account.expenditure)}</td>
                    <td
                      data-label="Net"
                      className={account.netSavings < 0 ? 'right tone-down' : 'right tone-up'}
                    >
                      {money(account.netSavings)}
                    </td>
                    <td data-label="Transactions" className="right">{account.transactionCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Async>
    </section>
  )
}
