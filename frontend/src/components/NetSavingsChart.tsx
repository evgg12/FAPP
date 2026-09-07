import type { MonthlySummary } from '../api/types'
import { Async } from './Async'
import type { AsyncState } from '../hooks/useAsync'
import { money, monthInitial, monthName } from '../format'

const WIDTH = 360
const HEIGHT = 90
const PAD = { top: 8, right: 6, bottom: 14, left: 6 }

/**
 * Net savings for the last twelve months as one small line, so a good month and a bad
 * month are obvious at a glance without giving up a screen to a chart.
 *
 * Deliberately unlabelled on the vertical axis: the point is the shape, and the figures
 * themselves are on the summary cards and in each point's tooltip. The zero line is
 * drawn because whether a month saved or overspent is the one thing the shape alone
 * cannot show.
 */
export function NetSavingsChart({ state }: { state: AsyncState<MonthlySummary[]> }) {
  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Net savings, last 12 months</h2>
      </div>
      <Async state={state} empty="No months to show yet." lines={2}>
        {(months) => <Line months={months.slice(-12)} />}
      </Async>
    </section>
  )
}

function Line({ months }: { months: MonthlySummary[] }) {
  const nets = months.map((month) => month.netSavings)
  const top = Math.max(...nets, 0)
  const bottom = Math.min(...nets, 0)
  const span = top - bottom || 1
  const plotHeight = HEIGHT - PAD.top - PAD.bottom
  const step = months.length > 1 ? (WIDTH - PAD.left - PAD.right) / (months.length - 1) : 0

  const x = (index: number) => PAD.left + index * step
  const y = (value: number) => PAD.top + ((top - value) / span) * plotHeight
  const points = months.map((month, index) => `${x(index)},${y(month.netSavings)}`).join(' ')

  return (
    <svg
      className="chart chart-compact"
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      role="img"
      aria-label={`Net savings for the last ${months.length} months`}
    >
      <line className="chart-grid" x1={PAD.left} x2={WIDTH - PAD.right} y1={y(0)} y2={y(0)} />
      <polyline className="chart-net" points={points} />
      {months.map((month, index) => (
        <g key={month.month}>
          <title>{`${monthName(month.month)}: ${money(month.netSavings)}`}</title>
          <circle
            className={month.netSavings < 0 ? 'chart-out' : 'chart-in'}
            cx={x(index)}
            cy={y(month.netSavings)}
            r="2.5"
          />
          <text className="chart-axis" x={x(index)} y={HEIGHT - 3} textAnchor="middle">
            {monthInitial(month.month)}
          </text>
        </g>
      ))}
    </svg>
  )
}
