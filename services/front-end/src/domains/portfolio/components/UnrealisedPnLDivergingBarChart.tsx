import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Cell,
  ResponsiveContainer,
  ReferenceLine,
  LabelList,
} from 'recharts'
import type { TooltipContentProps } from 'recharts'
import type { UnrealisedPnLEntry } from '../types/portfolio.types'
import { PROFIT_COLOUR, LOSS_COLOUR } from './chartColours'

export interface UnrealisedPnLDivergingBarChartProps {
  data: UnrealisedPnLEntry[]
  currency: string
}

function formatCurrency(value: number, currency: string): string {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

function makeCustomTooltip(currency: string) {
  return function CustomTooltipContent({ active, payload }: TooltipContentProps) {
    if (!active || !payload || payload.length === 0) return null
    const item = payload[0]
    const entry = item.payload as UnrealisedPnLEntry
    return (
      <div
        style={{
          background: '#1e1e2e',
          border: '1px solid #333',
          borderRadius: 6,
          padding: '8px 12px',
          color: '#e2e8f0',
          fontSize: 13,
        }}
      >
        <p style={{ fontWeight: 600, marginBottom: 4 }}>{entry.ticker}</p>
        <p
          style={{
            color: entry.unrealisedPnL >= 0 ? PROFIT_COLOUR : LOSS_COLOUR,
          }}
        >
          {formatCurrency(entry.unrealisedPnL, currency)}
        </p>
      </div>
    )
  }
}

export function UnrealisedPnLDivergingBarChart({
  data,
  currency,
}: UnrealisedPnLDivergingBarChartProps) {
  if (data.length === 0) {
    return (
      <p
        data-testid="unrealised-pnl-empty-state"
        style={{ color: '#94a3b8', textAlign: 'center' }}
      >
        No stock holdings to display.
      </p>
    )
  }

  const CustomTooltip = makeCustomTooltip(currency)

  return (
    <div data-testid="unrealised-pnl-bar-chart" style={{ width: '100%', height: 300 }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 24, right: 24, bottom: 16, left: 24 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="ticker" tick={{ fill: '#94a3b8', fontSize: 12 }} />
          <YAxis
            tick={{ fill: '#94a3b8', fontSize: 11 }}
            tickFormatter={(v: number) => formatCurrency(v, currency)}
          />
          <ReferenceLine y={0} stroke="#475569" strokeWidth={2} />
          <Tooltip content={CustomTooltip} />
          <Bar dataKey="unrealisedPnL">
            <LabelList
              dataKey="unrealisedPnL"
              formatter={(value: unknown) =>
                typeof value === 'number' ? formatCurrency(value, currency) : ''
              }
              style={{ fontSize: 11 }}
            />
            {data.map((entry) => (
              <Cell
                key={entry.ticker}
                fill={entry.unrealisedPnL >= 0 ? PROFIT_COLOUR : LOSS_COLOUR}
                data-testid={`bar-${entry.ticker}`}
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
