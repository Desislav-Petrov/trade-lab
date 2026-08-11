import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { InsightsTab } from './InsightsTab'
import type { PortfolioInsights } from '../types/portfolio.types'

vi.mock('./AssetClassPieChart', () => ({
  AssetClassPieChart: ({ data }: { data: unknown }) =>
    data ? <div data-testid="asset-class-pie-chart">AssetClassPieChart</div> : <p>No portfolio data to display.</p>,
}))

vi.mock('./StockBreakdownPieChart', () => ({
  StockBreakdownPieChart: ({ data }: { data: unknown[] }) =>
    data.length > 0 ? <div data-testid="stock-breakdown-pie-chart">StockBreakdownPieChart</div> : <p>No stock holdings to display.</p>,
}))

vi.mock('./UnrealisedPnLDivergingBarChart', () => ({
  UnrealisedPnLDivergingBarChart: ({ data }: { data: unknown[] }) =>
    data.length > 0 ? <div data-testid="unrealised-pnl-bar-chart">UnrealisedPnLBarChart</div> : <p>No stock holdings to display.</p>,
}))

const mockInsights: PortfolioInsights = {
  assetClassBreakdown: {
    stockPercent: 75.0,
    cashPercent: 25.0,
    totalPortfolioValue: 2000.0,
  },
  stockBreakdown: [
    { ticker: 'AAPL', currentValue: 1500, percentOfStockPortfolio: 100.0 },
  ],
  unrealisedPnLContribution: [
    { ticker: 'AAPL', unrealisedPnL: 50 },
  ],
}

const cashOnlyInsights: PortfolioInsights = {
  assetClassBreakdown: {
    stockPercent: 0,
    cashPercent: 100,
    totalPortfolioValue: 500,
  },
  stockBreakdown: [],
  unrealisedPnLContribution: [],
}

describe('InsightsTab', () => {
  it('InsightsTab - isLoading true - shows skeleton, no charts', () => {
    render(<InsightsTab insights={undefined} isLoading={true} isError={false} currency="USD" />)
    expect(screen.getByTestId('insights-loading')).toBeInTheDocument()
    expect(screen.getAllByTestId('chart-skeleton')).toHaveLength(3)
    expect(screen.queryByTestId('insights-content')).not.toBeInTheDocument()
  })

  it('InsightsTab - isError true - shows error message, no charts', () => {
    render(<InsightsTab insights={undefined} isLoading={false} isError={true} currency="USD" />)
    expect(screen.getByTestId('insights-error')).toBeInTheDocument()
    expect(screen.getByText('Could not load insights. Please try again.')).toBeInTheDocument()
    expect(screen.queryByTestId('insights-content')).not.toBeInTheDocument()
  })

  it('InsightsTab - normal render with insights - all three charts mounted', () => {
    render(<InsightsTab insights={mockInsights} isLoading={false} isError={false} currency="USD" />)
    expect(screen.getByTestId('insights-content')).toBeInTheDocument()
    expect(screen.getByTestId('asset-class-pie-chart')).toBeInTheDocument()
    expect(screen.getByTestId('stock-breakdown-pie-chart')).toBeInTheDocument()
    expect(screen.getByTestId('unrealised-pnl-bar-chart')).toBeInTheDocument()
  })

  it('InsightsTab - no stock positions - Chart 1 renders, Charts 2 and 3 show empty state', () => {
    render(<InsightsTab insights={cashOnlyInsights} isLoading={false} isError={false} currency="USD" />)
    expect(screen.getByTestId('insights-content')).toBeInTheDocument()
    expect(screen.getByTestId('asset-class-pie-chart')).toBeInTheDocument()
    expect(screen.getAllByText('No stock holdings to display.')).toHaveLength(2)
    expect(screen.queryByTestId('stock-breakdown-pie-chart')).not.toBeInTheDocument()
    expect(screen.queryByTestId('unrealised-pnl-bar-chart')).not.toBeInTheDocument()
  })
})
