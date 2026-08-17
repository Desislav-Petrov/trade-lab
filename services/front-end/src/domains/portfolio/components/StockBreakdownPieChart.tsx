import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import type { PieLabelRenderProps, TooltipContentProps } from 'recharts'
import type { StockBreakdownEntry } from '../types/portfolio.types'
import { CHART_COLOURS } from './chartColours'

export interface StockBreakdownPieChartProps {
  data: StockBreakdownEntry[]
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

interface SliceItem {
  name: string
  ticker: string
  value: number
  pct: number | null
  original: StockBreakdownEntry
}

function makeCustomTooltip(currency: string) {
  return function CustomTooltipContent({ active, payload }: TooltipContentProps) {
    if (!active || !payload || payload.length === 0) return null
    const item = payload[0]
    const slice = item.payload as SliceItem
    const entry = slice.original
    const pct =
      entry.percentOfStockPortfolio !== null
        ? `${entry.percentOfStockPortfolio.toFixed(2)}%`
        : '—'
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
        <p>{formatCurrency(entry.currentValue, currency)}</p>
        <p>{pct}</p>
      </div>
    )
  }
}

export function StockBreakdownPieChart({ data, currency }: StockBreakdownPieChartProps) {
  if (data.length === 0) {
    return (
      <p
        data-testid="stock-breakdown-empty-state"
        style={{ color: '#94a3b8', textAlign: 'center' }}
      >
        No stock holdings to display.
      </p>
    )
  }

  const slices: SliceItem[] = data.map((entry) => ({
    name: entry.ticker,
    ticker: entry.ticker,
    value: entry.currentValue,
    pct: entry.percentOfStockPortfolio,
    original: entry,
  }))

  const CustomTooltip = makeCustomTooltip(currency)

  return (
    <div data-testid="stock-breakdown-pie-chart" style={{ width: '100%', height: 280 }}>
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie
            data={slices}
            dataKey="value"
            nameKey="name"
            cx="50%"
            cy="50%"
            outerRadius={90}
            label={(props: PieLabelRenderProps) => {
              const idx = props.index ?? 0
              const entry = slices[idx]
              if (!entry) return ''
              return `${entry.ticker}: ${entry.pct !== null ? `${entry.pct.toFixed(1)}%` : '—'}`
            }}
          >
            {slices.map((entry, index) => (
              <Cell
                key={entry.ticker}
                fill={CHART_COLOURS[index % CHART_COLOURS.length]}
                data-testid={`slice-${entry.ticker}`}
              />
            ))}
          </Pie>
          <Tooltip content={CustomTooltip} />
          <Legend />
        </PieChart>
      </ResponsiveContainer>
    </div>
  )
}
