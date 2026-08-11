import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AssetClassPieChart } from './AssetClassPieChart'
import type { AssetClassBreakdown } from '../types/portfolio.types'

vi.mock('recharts', async (importOriginal) => {
  const original = await importOriginal<typeof import('recharts')>()
  return {
    ...original,
    ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
      <div data-testid="responsive-container">{children}</div>
    ),
  }
})

const mockData: AssetClassBreakdown = {
  stockPercent: 65.5,
  cashPercent: 34.5,
  totalPortfolioValue: 6825.0,
}

describe('AssetClassPieChart', () => {
  it('AssetClassPieChart - data present - renders pie chart with two slices', () => {
    render(<AssetClassPieChart data={mockData} currency="USD" />)
    expect(screen.getByTestId('asset-class-pie-chart')).toBeInTheDocument()
  })

  it('AssetClassPieChart - data is null - renders empty state', () => {
    render(<AssetClassPieChart data={null} currency="USD" />)
    expect(screen.getByTestId('asset-class-empty-state')).toBeInTheDocument()
    expect(screen.getByText('No portfolio data to display.')).toBeInTheDocument()
  })

  it('AssetClassPieChart - data is undefined - renders empty state', () => {
    render(<AssetClassPieChart data={undefined} currency="USD" />)
    expect(screen.getByTestId('asset-class-empty-state')).toBeInTheDocument()
    expect(screen.getByText('No portfolio data to display.')).toBeInTheDocument()
  })

  it('AssetClassPieChart - totalPortfolioValue is 0 - renders empty state', () => {
    const zeroData: AssetClassBreakdown = {
      stockPercent: null,
      cashPercent: null,
      totalPortfolioValue: 0,
    }
    render(<AssetClassPieChart data={zeroData} currency="USD" />)
    expect(screen.getByTestId('asset-class-empty-state')).toBeInTheDocument()
    expect(screen.getByText('No portfolio data to display.')).toBeInTheDocument()
  })
})
