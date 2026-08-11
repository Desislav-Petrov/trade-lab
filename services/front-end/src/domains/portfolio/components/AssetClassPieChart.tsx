import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import type { PieLabelRenderProps, TooltipContentProps } from 'recharts'
import type { AssetClassBreakdown } from '../types/portfolio.types'
import { CHART_COLOURS } from './chartColours'

export interface AssetClassPieChartProps {
  data: AssetClassBreakdown | null | undefined
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

interface SliceEntry {
  name: string
  value: number
  pct: number | null
  absValue: number
}

export function AssetClassPieChart({ data, currency }: AssetClassPieChartProps) {
  if (!data || data.totalPortfolioValue === 0) {
    return (
      <p data-testid="asset-class-empty-state" style={{ color: '#94a3b8', textAlign: 'center' }}>
        No portfolio data to display.
      </p>
    )
  }

  const stockPct = data.stockPercent
  const cashPct = data.cashPercent
  const total = data.totalPortfolioValue

  const stockAbsValue = stockPct !== null ? (stockPct / 100) * total : 0
  const cashAbsValue = cashPct !== null ? (cashPct / 100) * total : 0

  const slices: SliceEntry[] = [
    { name: 'Stock', value: stockAbsValue, pct: stockPct, absValue: stockAbsValue },
    { name: 'Cash', value: cashAbsValue, pct: cashPct, absValue: cashAbsValue },
  ]

  function renderTooltip(tooltipProps: TooltipContentProps) {
    if (!tooltipProps.active || !tooltipProps.payload || tooltipProps.payload.length === 0)
      return null
    const item = tooltipProps.payload[0]
    const entry = item.payload as SliceEntry
    const pctLabel = entry.pct !== null ? `${entry.pct.toFixed(2)}%` : '—'
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
        <p style={{ fontWeight: 600, marginBottom: 4 }}>{entry.name}</p>
        <p>{formatCurrency(entry.absValue, currency)}</p>
        <p>{pctLabel}</p>
      </div>
    )
  }

  return (
    <div data-testid="asset-class-pie-chart" style={{ width: '100%', height: 280 }}>
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
              const name = props.name as string | undefined
              if (name === 'Stock') {
                return stockPct !== null ? `Stock: ${stockPct.toFixed(1)}%` : 'Stock: —'
              }
              return cashPct !== null ? `Cash: ${cashPct.toFixed(1)}%` : 'Cash: —'
            }}
          >
            {slices.map((entry, index) => (
              <Cell key={entry.name} fill={CHART_COLOURS[index % CHART_COLOURS.length]} />
            ))}
          </Pie>
          <Tooltip content={renderTooltip} />
          <Legend />
        </PieChart>
      </ResponsiveContainer>
    </div>
  )
}
