import { AssetClassPieChart } from './AssetClassPieChart'
import { StockBreakdownPieChart } from './StockBreakdownPieChart'
import { UnrealisedPnLDivergingBarChart } from './UnrealisedPnLDivergingBarChart'
import type { PortfolioInsights } from '../types/portfolio.types'

export interface InsightsTabProps {
  insights: PortfolioInsights | undefined
  isLoading: boolean
  isError: boolean
  currency: string
}

function ChartSkeleton() {
  return (
    <div
      data-testid="chart-skeleton"
      style={{
        width: '100%',
        height: 280,
        background: '#1e1e2e',
        borderRadius: 8,
        animation: 'pulse 1.5s ease-in-out infinite',
      }}
    />
  )
}

export function InsightsTab({ insights, isLoading, isError, currency }: InsightsTabProps) {
  if (isLoading) {
    return (
      <div data-testid="insights-loading" style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
        <ChartSkeleton />
        <ChartSkeleton />
        <ChartSkeleton />
      </div>
    )
  }

  if (isError) {
    return (
      <p data-testid="insights-error" role="alert" style={{ color: '#ef4444' }}>
        Could not load insights. Please try again.
      </p>
    )
  }

  return (
    <div data-testid="insights-content" style={{ display: 'flex', flexDirection: 'column', gap: 32 }}>
      <section>
        <h3
          style={{
            marginBottom: 12,
            fontSize: 13,
            fontWeight: 600,
            color: '#94a3b8',
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
          }}
        >
          Asset Class Breakdown
        </h3>
        <AssetClassPieChart data={insights?.assetClassBreakdown} currency={currency} />
      </section>

      <section>
        <h3
          style={{
            marginBottom: 12,
            fontSize: 13,
            fontWeight: 600,
            color: '#94a3b8',
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
          }}
        >
          Stock Holdings Breakdown
        </h3>
        <StockBreakdownPieChart data={insights?.stockBreakdown ?? []} currency={currency} />
      </section>

      <section>
        <h3
          style={{
            marginBottom: 12,
            fontSize: 13,
            fontWeight: 600,
            color: '#94a3b8',
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
          }}
        >
          Unrealised P&amp;L Contribution
        </h3>
        <UnrealisedPnLDivergingBarChart
          data={insights?.unrealisedPnLContribution ?? []}
          currency={currency}
        />
      </section>
    </div>
  )
}
