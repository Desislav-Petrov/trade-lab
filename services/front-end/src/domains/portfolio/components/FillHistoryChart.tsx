import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  XAxis,
  YAxis,
} from 'recharts'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/shared/components/ui/tooltip'
import { cn } from '@/shared/lib/utils'
import type { FillDataPoint, FillHistoryEntry } from '../types/portfolio.types'
import { CHART_COLOURS, LOSS_COLOUR, PROFIT_COLOUR } from './chartColours'

export interface FillHistoryChartProps {
  fills: FillHistoryEntry[]
  hiddenSymbols: Set<string>
  onToggleSymbol: (ticker: string) => void
}

interface ChartPoint {
  filledAt: string
  [key: string]: FillDataPoint | number | string | undefined
}

interface FillDotProps {
  cx?: number
  cy?: number
  payload?: unknown
}

function formatCurrency(value: number): string {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value))
}

function pointKey(ticker: string): string {
  return `${ticker}Fill`
}

function buildChartData(fills: FillHistoryEntry[]): ChartPoint[] {
  return fills
    .flatMap((fill) =>
      fill.dataPoints.map((point) => ({
        filledAt: point.filledAt,
        [fill.ticker]: point.executionPrice,
        [pointKey(fill.ticker)]: point,
      })),
    )
    .sort((a, b) => a.filledAt.localeCompare(b.filledAt))
}

function makeDot(ticker: string) {
  return function FillDot({ cx, cy, payload }: FillDotProps) {
    if (typeof cx !== 'number' || typeof cy !== 'number') return <g />

    const chartPoint = payload as ChartPoint | undefined
    const fillPoint = chartPoint?.[pointKey(ticker)] as FillDataPoint | undefined
    if (!fillPoint) return <g />

    const colour = fillPoint.side === 'BUY' ? PROFIT_COLOUR : LOSS_COLOUR

    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <circle
            cx={cx}
            cy={cy}
            r={4}
            fill={colour}
            stroke={colour}
            data-testid={`fill-dot-${ticker}-${fillPoint.side}`}
          />
        </TooltipTrigger>
        <TooltipContent>
          <div className="space-y-1">
            <p className="font-semibold">{ticker}</p>
            <p>{fillPoint.side}</p>
            <p>{formatCurrency(fillPoint.executionPrice)}</p>
            <p>Quantity: {fillPoint.quantity}</p>
            <p>{formatDateTime(fillPoint.filledAt)}</p>
          </div>
        </TooltipContent>
      </Tooltip>
    )
  }
}

export function FillHistoryChart({
  fills,
  hiddenSymbols,
  onToggleSymbol,
}: FillHistoryChartProps) {
  if (fills.length === 0) {
    return (
      <p data-testid="fill-history-empty-state" className="text-center text-sm text-[var(--color-text-secondary)]">
        No trade history to display.
      </p>
    )
  }

  const chartData = buildChartData(fills)
  const visibleFills = fills.filter((fill) => !hiddenSymbols.has(fill.ticker))

  return (
    <TooltipProvider>
      <div data-testid="fill-history-chart" className="flex flex-col gap-4">
        <div className="h-80 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData} margin={{ top: 16, right: 24, bottom: 16, left: 24 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis
                dataKey="filledAt"
                tick={{ fill: '#94a3b8', fontSize: 11 }}
                tickFormatter={(value: string) => formatDateTime(value)}
              />
              <YAxis
                tick={{ fill: '#94a3b8', fontSize: 11 }}
                tickFormatter={(value: number) => formatCurrency(value)}
              />
              {visibleFills.map((fill, index) => (
                <Line
                  key={fill.ticker}
                  type="stepAfter"
                  data={fill.dataPoints.map((point) => ({
                    filledAt: point.filledAt,
                    [fill.ticker]: point.executionPrice,
                    [pointKey(fill.ticker)]: point,
                  }))}
                  dataKey={fill.ticker}
                  stroke={CHART_COLOURS[index % CHART_COLOURS.length]}
                  strokeWidth={2}
                  dot={makeDot(fill.ticker)}
                  connectNulls
                  isAnimationActive={false}
                  data-testid={`fill-history-line-${fill.ticker}`}
                />
              ))}
            </LineChart>
          </ResponsiveContainer>
        </div>

        <div className="flex flex-wrap gap-2" aria-label="Fill history legend">
          {fills.map((fill, index) => {
            const hidden = hiddenSymbols.has(fill.ticker)
            return (
              <button
                key={fill.ticker}
                type="button"
                onClick={() => onToggleSymbol(fill.ticker)}
                className={cn(
                  'flex items-center gap-2 rounded border border-[var(--color-border)] px-2 py-1 text-xs text-[var(--color-text-primary)]',
                  hidden && 'opacity-40',
                )}
                data-testid={`fill-history-legend-${fill.ticker}`}
              >
                <span
                  className="h-2 w-2 rounded-full"
                  style={{ backgroundColor: CHART_COLOURS[index % CHART_COLOURS.length] }}
                />
                {fill.ticker}
              </button>
            )
          })}
        </div>
      </div>
    </TooltipProvider>
  )
}
