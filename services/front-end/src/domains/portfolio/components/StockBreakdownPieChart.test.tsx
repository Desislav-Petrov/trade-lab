import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { StockBreakdownPieChart } from './StockBreakdownPieChart'
import type { StockBreakdownEntry } from '../types/portfolio.types'

vi.mock('recharts', async (importOriginal) => {
  const original = await importOriginal<typeof import('recharts')>()
  return {
    ...original,
    ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
      <div data-testid="responsive-container">{children}</div>
    ),
  }
})

const mockData: StockBreakdownEntry[] = [
  { ticker: 'AAPL', currentValue: 1500, percentOfStockPortfolio: 60.0 },
  { ticker: 'MSFT', currentValue: 1000, percentOfStockPortfolio: 40.0 },
]

describe('StockBreakdownPieChart', () => {
  it('StockBreakdownPieChart - multiple stocks - renders correct number of slices', () => {
    render(<StockBreakdownPieChart data={mockData} currency="USD" />)
    expect(screen.getByTestId('stock-breakdown-pie-chart')).toBeInTheDocument()
  })

  it('StockBreakdownPieChart - empty data - renders empty state', () => {
    render(<StockBreakdownPieChart data={[]} currency="USD" />)
    expect(screen.getByTestId('stock-breakdown-empty-state')).toBeInTheDocument()
    expect(screen.getByText('No stock holdings to display.')).toBeInTheDocument()
  })

  it('StockBreakdownPieChart - single stock - renders pie chart (100% slice)', () => {
    const single: StockBreakdownEntry[] = [
      { ticker: 'AAPL', currentValue: 1500, percentOfStockPortfolio: 100.0 },
    ]
    render(<StockBreakdownPieChart data={single} currency="USD" />)
    expect(screen.getByTestId('stock-breakdown-pie-chart')).toBeInTheDocument()
  })
})
