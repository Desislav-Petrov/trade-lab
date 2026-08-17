import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { UnrealisedPnLDivergingBarChart } from './UnrealisedPnLDivergingBarChart'
import type { UnrealisedPnLEntry } from '../types/portfolio.types'

vi.mock('recharts', async (importOriginal) => {
  const original = await importOriginal<typeof import('recharts')>()
  return {
    ...original,
    ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
      <div data-testid="responsive-container">{children}</div>
    ),
  }
})

describe('UnrealisedPnLDivergingBarChart', () => {
  it('UnrealisedPnLDivergingBarChart - empty data - renders empty state', () => {
    render(<UnrealisedPnLDivergingBarChart data={[]} currency="USD" />)
    expect(screen.getByTestId('unrealised-pnl-empty-state')).toBeInTheDocument()
    expect(screen.getByText('No stock holdings to display.')).toBeInTheDocument()
  })

  it('UnrealisedPnLDivergingBarChart - all positive P&L - renders bar chart', () => {
    const data: UnrealisedPnLEntry[] = [
      { ticker: 'AAPL', unrealisedPnL: 100 },
      { ticker: 'MSFT', unrealisedPnL: 50 },
    ]
    render(<UnrealisedPnLDivergingBarChart data={data} currency="USD" />)
    expect(screen.getByTestId('unrealised-pnl-bar-chart')).toBeInTheDocument()
  })

  it('UnrealisedPnLDivergingBarChart - all negative P&L - renders bar chart', () => {
    const data: UnrealisedPnLEntry[] = [
      { ticker: 'AAPL', unrealisedPnL: -80 },
      { ticker: 'MSFT', unrealisedPnL: -40 },
    ]
    render(<UnrealisedPnLDivergingBarChart data={data} currency="USD" />)
    expect(screen.getByTestId('unrealised-pnl-bar-chart')).toBeInTheDocument()
  })

  it('UnrealisedPnLDivergingBarChart - mixed P&L - renders bar chart with both', () => {
    const data: UnrealisedPnLEntry[] = [
      { ticker: 'AAPL', unrealisedPnL: 100 },
      { ticker: 'TSLA', unrealisedPnL: -60 },
    ]
    render(<UnrealisedPnLDivergingBarChart data={data} currency="USD" />)
    expect(screen.getByTestId('unrealised-pnl-bar-chart')).toBeInTheDocument()
  })
})
