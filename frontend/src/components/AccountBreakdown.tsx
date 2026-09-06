import type { AccountSummary } from '../api/types'
import { Async } from './Async'
import type { AsyncState } from '../hooks/useAsync'
import { label, money } from '../format'

/** Per account and per bank, which is the point of aggregating two banks in one place. */
export function AccountBreakdown({ state }: { state: AsyncState<AccountSummary[]> }) {
  return (
    <section className="panel">
      <h2>By account</h2>
      <Async state={state} empty="No accounts yet.">
        {(accounts) => (
          <div className="scroll">
            <table>
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
                    <td>{account.accountName}</td>
                    <td>{label(account.provider)}</td>
                    <td className="right">{money(account.income)}</td>
                    <td className="right">{money(account.expenditure)}</td>
                    <td className={account.netSavings < 0 ? 'right tone-down' : 'right tone-up'}>
                      {money(account.netSavings)}
                    </td>
                    <td className="right">{account.transactionCount}</td>
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
