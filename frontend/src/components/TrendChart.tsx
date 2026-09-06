import type { MonthlySummary } from '../api/types'
import { Async } from './Async'
import type { AsyncState } from '../hooks/useAsync'
import { money, monthName, shortMonth } from '../format'

/** Drawing constants, in viewBox units. The SVG scales; these do not change. */
const HEIGHT = 210
const PAD = { top: 12, right: 12, bottom: 26, left: 46 }
const GROUP = 46
const PLOT_HEIGHT = HEIGHT - PAD.top - PAD.bottom

/**
 * Money in, money out and what was left, month by month.
 *
 * Hand-drawn SVG rather than a charting library: a grouped bar chart with a line over it
 * is a few dozen lines of geometry, and a `viewBox` makes it resize to any screen
 * without a resize observer or a second dependency to justify.
 *
 * The chart is a reading aid. Every number in it came from the analytics API — nothing
 * here recalculates, and the axis is scaled to the largest figure returned rather than
 * to a rounded-up number that would misstate the bar heights.
 */
export function TrendChart({ state }: { state: AsyncState<MonthlySummary[]> }) {
  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Income and spending</h2>
        <ul className="legend">
          <li>
            <span className="swatch" style={{ background: 'var(--in)' }} /> In
          </li>
          <li>
            <span className="swatch" style={{ background: 'var(--out)' }} /> Out
          </li>
          <li>
            <span className="swatch" style={{ background: 'var(--accent)' }} /> Net
          </li>
        </ul>
      </div>
      <Async state={state} empty="No months in this period." lines={5}>
        {(months) => <Plot months={months} />}
      </Async>
      <p className="muted">
        Monthly totals for the selected period, as returned by the API. Bar heights are
        drawn to the largest figure shown.
      </p>
    </section>
  )
}

function Plot({ months }: { months: MonthlySummary[] }) {
  const width = PAD.left + PAD.right + months.length * GROUP
  const nets = months.map((month) => month.netSavings)
  const top = Math.max(...months.map((m) => Math.max(m.income, m.expenditure)), ...nets, 0)
  const bottom = Math.min(...nets, 0)
  // A period with nothing in it still needs a scale, or every bar divides by zero.
  const span = top - bottom || 1
  const y = (value: number) => PAD.top + ((top - value) / span) * PLOT_HEIGHT
  const zero = y(0)
  const labelEvery = Math.ceil(months.length / 6)

  const ticks = bottom < 0 ? [top, 0, bottom] : [top, top / 2, 0]
  const netPoints = months
    .map((month, index) => `${PAD.left + index * GROUP + GROUP / 2},${y(month.netSavings)}`)
    .join(' ')

  return (
    <svg
      className="chart"
      viewBox={`0 0 ${width} ${HEIGHT}`}
      role="img"
      aria-label={`Monthly income, spending and net for ${months.length} months`}
    >
      {ticks.map((tick) => (
        <g key={tick}>
          <line
            className="chart-grid"
            x1={PAD.left}
            x2={width - PAD.right}
            y1={y(tick)}
            y2={y(tick)}
          />
          <text className="chart-axis" x={PAD.left - 6} y={y(tick) + 4} textAnchor="end">
            {compact(tick)}
          </text>
        </g>
      ))}

      {months.map((month, index) => {
        const groupX = PAD.left + index * GROUP
        return (
          <g key={month.month}>
            <title>
              {`${monthName(month.month)}: ${money(month.income)} in, ${money(
                month.expenditure,
              )} out, net ${money(month.netSavings)}`}
            </title>
            <rect
              className="chart-in"
              x={groupX + GROUP * 0.18}
              width={GROUP * 0.28}
              y={y(month.income)}
              height={Math.max(zero - y(month.income), month.income > 0 ? 1 : 0)}
              rx="2"
            />
            <rect
              className="chart-out"
              x={groupX + GROUP * 0.54}
              width={GROUP * 0.28}
              y={y(month.expenditure)}
              height={Math.max(zero - y(month.expenditure), month.expenditure > 0 ? 1 : 0)}
              rx="2"
            />
            {index % labelEvery === 0 && (
              <text
                className="chart-axis"
                x={groupX + GROUP / 2}
                y={HEIGHT - 8}
                textAnchor="middle"
              >
                {shortMonth(month.month)}
              </text>
            )}
          </g>
        )
      })}

      <line className="chart-grid" x1={PAD.left} x2={width - PAD.right} y1={zero} y2={zero} />
      <polyline className="chart-net" points={netPoints} />
      {months.map((month, index) => (
        <circle
          key={month.month}
          className="chart-net-dot"
          cx={PAD.left + index * GROUP + GROUP / 2}
          cy={y(month.netSavings)}
          r="2.5"
        />
      ))}
    </svg>
  )
}

/** Axis labels only: `1450` as `£1.5k`, so they fit at any width. */
function compact(value: number): string {
  const sign = value < 0 ? '-' : ''
  const size = Math.abs(value)
  if (size >= 1000) {
    return `${sign}£${(size / 1000).toFixed(1)}k`
  }
  return `${sign}£${Math.round(size)}`
}
