import { describe, it, expect } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PortfolioHoldingsTable } from './PortfolioHoldingsTable'
import type { StockHolding, CashHolding } from '../types/portfolio.types'

const appleHolding: StockHolding = {
  ticker: 'AAPL',
  quantity: 10,
  currentPrice: 150.00,
  currentValue: 1500.00,
  minPrice: 140.00,
  maxPrice: 160.00,
  avgPrice: 145.00,
  portfolioPercent: 60.00,
  unrealisedPnL: 50.00,
}

const googleHolding: StockHolding = {
  ticker: 'GOOGL',
  quantity: 5,
  currentPrice: 100.00,
  currentValue: 500.00,
  minPrice: 90.00,
  maxPrice: 110.00,
  avgPrice: 105.00,
  portfolioPercent: 20.00,
  unrealisedPnL: -25.00,
}

const teslaHolding: StockHolding = {
  ticker: 'TSLA',
  quantity: 2.5,
  currentPrice: 200.00,
  currentValue: 500.00,
  minPrice: 180.00,
  maxPrice: 220.00,
  avgPrice: 200.00,
  portfolioPercent: 20.00,
  unrealisedPnL: 0,
}

const holdingWithNullPercent: StockHolding = {
  ticker: 'MSFT',
  quantity: 3,
  currentPrice: 0,
  currentValue: 0,
  minPrice: 250.00,
  maxPrice: 280.00,
  avgPrice: 270.00,
  portfolioPercent: null,
  unrealisedPnL: -810.00,
}

const cashHolding: CashHolding = {
  balance: 1000.00,
  currency: 'USD',
  portfolioPercent: 40.00,
}

const cashHoldingWithNullPercent: CashHolding = {
  balance: 0,
  currency: 'USD',
  portfolioPercent: null,
}

describe('PortfolioHoldingsTable', () => {
  it('PortfolioHoldingsTable - all columns - renders correct values', () => {
    render(
      <PortfolioHoldingsTable
        holdings={[appleHolding]}
        cash={cashHolding}
        currency="USD"
      />,
    )

    expect(screen.getByText('AAPL')).toBeInTheDocument()
    expect(screen.getByText('10')).toBeInTheDocument()
    expect(screen.getByText('1,500.00')).toBeInTheDocument()
    expect(screen.getByText('140.00')).toBeInTheDocument()
    expect(screen.getByText('160.00')).toBeInTheDocument()
    expect(screen.getByText('145.00')).toBeInTheDocument()
    expect(screen.getByText('60.00%')).toBeInTheDocument()
    expect(screen.getByText('50.00')).toBeInTheDocument()
  })

  it('PortfolioHoldingsTable - cash row - always last regardless of initial render', () => {
    render(
      <PortfolioHoldingsTable
        holdings={[googleHolding, appleHolding]}
        cash={cashHolding}
        currency="USD"
      />,
    )

    const rows = screen.getAllByRole('row')
    // rows[0] is header; rows[1..n-1] are stock rows; rows[n] is cash row
    const lastRow = rows[rows.length - 1]
    expect(within(lastRow).getByText('USD')).toBeInTheDocument()
    expect(within(lastRow).getByText('1,000.00')).toBeInTheDocument()
  })

  it('PortfolioHoldingsTable - cash row - displays currency and balance', () => {
    render(
      <PortfolioHoldingsTable
        holdings={[]}
        cash={cashHolding}
        currency="USD"
      />,
    )

    const rows = screen.getAllByRole('row')
    const cashRow = rows[1] // Header is row 0
    expect(within(cashRow).getByText('USD')).toBeInTheDocument()
    expect(within(cashRow).getByText('1,000.00')).toBeInTheDocument()
  })

  it('PortfolioHoldingsTable - cash row - renders em dash for non-applicable columns', () => {
    render(
      <PortfolioHoldingsTable
        holdings={[]}
        cash={cashHolding}
        currency="USD"
      />,
    )

    const rows = screen.getAllByRole('row')
    const cashRow = rows[1]
    const cells = within(cashRow).getAllByRole('cell')
    
    // Shares (index 1), Min (3), Max (4), Avg (5), Unrealised P&L (7) should be "—"
    expect(cells[1].textContent).toBe('—')
    expect(cells[3].textContent).toBe('—')
    expect(cells[4].textContent).toBe('—')
    expect(cells[5].textContent).toBe('—')
    expect(cells[7].textContent).toBe('—')
  })

  it('PortfolioHoldingsTable - cash row - displays portfolioPercent when present', () => {
    render(
      <PortfolioHoldingsTable
        holdings={[]}
        cash={cashHolding}
        currency="USD"
      />,
    )

    const rows = screen.getAllByRole('row')
    const cashRow = rows[1]
    expect(within(cashRow).getByText('40.00%')).toBeInTheDocument()
  })

  it('PortfolioHoldingsTable - portfolioPercent null - renders as em dash', () => {
    render(
      <PortfolioHoldingsTable
        holdings={[holdingWithNullPercent]}
        cash={cashHoldingWithNullPercent}
        currency="USD"
      />,
    )

    const rows = screen.getAllByRole('row')
    const stockRow = rows[1]
    const cashRow = rows[2]
    
    const stockCells = within(stockRow).getAllByRole('cell')
    const cashCells = within(cashRow).getAllByRole('cell')
    
    // Portfolio percent column is index 6
    expect(stockCells[6].textContent).toBe('—')
    expect(cashCells[6].textContent).toBe('—')
  })

  it('PortfolioHoldingsTable - positive unrealisedPnL - has green styling', () => {
    render(
      <PortfolioHoldingsTable
        holdings={[appleHolding]}
        cash={cashHolding}
        currency="USD"
      />,
    )

    const rows = screen.getAllByRole('row')
    const stockRow = rows[1]
    const cells = within(stockRow).getAllByRole('cell')
    
    // Unrealised P&L is column index 7
    const pnlCell = cells[7]
    expect(pnlCell).toHaveClass('text-[var(--color-success)]')
  })

  it('PortfolioHoldingsTable - negative unrealisedPnL - has red styling', () => {
    render(
      <PortfolioHoldingsTable
        holdings={[googleHolding]}
        cash={cashHolding}
        currency="USD"
      />,
    )

    const rows = screen.getAllByRole('row')
    const stockRow = rows[1]
    const cells = within(stockRow).getAllByRole('cell')
    
    // Unrealised P&L is column index 7
    const pnlCell = cells[7]
    expect(pnlCell).toHaveClass('text-[var(--color-danger)]')
  })

  it('PortfolioHoldingsTable - zero unrealisedPnL - has default styling', () => {
    render(
      <PortfolioHoldingsTable
        holdings={[teslaHolding]}
        cash={cashHolding}
        currency="USD"
      />,
    )

    const rows = screen.getAllByRole('row')
    const stockRow = rows[1]
    const cells = within(stockRow).getAllByRole('cell')
    
    // Unrealised P&L is column index 7
    const pnlCell = cells[7]
    expect(pnlCell).toHaveClass('text-[var(--color-text-primary)]')
    expect(pnlCell).not.toHaveClass('text-[var(--color-success)]')
    expect(pnlCell).not.toHaveClass('text-[var(--color-danger)]')
  })

  it('PortfolioHoldingsTable - default sort - ticker ascending', () => {
    render(
      <PortfolioHoldingsTable
        holdings={[teslaHolding, appleHolding, googleHolding]}
        cash={cashHolding}
        currency="USD"
      />,
    )

    const rows = screen.getAllByRole('row')
    // rows[0] is header; rows[1..3] are stock rows in sorted order
    expect(within(rows[1]).getByText('AAPL')).toBeInTheDocument()
    expect(within(rows[2]).getByText('GOOGL')).toBeInTheDocument()
    expect(within(rows[3]).getByText('TSLA')).toBeInTheDocument()
    // rows[4] is cash row
    expect(within(rows[4]).getByText('USD')).toBeInTheDocument()
  })

  it('PortfolioHoldingsTable - default sort - has ascending indicator on ticker', () => {
    render(
      <PortfolioHoldingsTable
        holdings={[appleHolding]}
        cash={cashHolding}
        currency="USD"
      />,
    )

    const tickerHeader = screen.getByRole('columnheader', { name: /^ticker/i })
    expect(tickerHeader.textContent).toContain('↑')
  })

  it('PortfolioHoldingsTable - click ticker header - cycles asc to desc to default', async () => {
    const user = userEvent.setup()
    render(
      <PortfolioHoldingsTable
        holdings={[appleHolding, googleHolding]}
        cash={cashHolding}
        currency="USD"
      />,
    )

    const tickerHeader = screen.getByRole('columnheader', { name: /^ticker/i })

    // Initial: ascending (default)
    expect(tickerHeader.textContent).toBe('Ticker ↑')

    // First click: descending
    await user.click(tickerHeader)
    expect(tickerHeader.textContent).toBe('Ticker ↓')

    // Check order: GOOGL then AAPL
    let rows = screen.getAllByRole('row')
    expect(within(rows[1]).getByText('GOOGL')).toBeInTheDocument()
    expect(within(rows[2]).getByText('AAPL')).toBeInTheDocument()

    // Second click: back to default (ascending)
    await user.click(tickerHeader)
    expect(tickerHeader.textContent).toBe('Ticker ↑')

    // Check order: AAPL then GOOGL
    rows = screen.getAllByRole('row')
    expect(within(rows[1]).getByText('AAPL')).toBeInTheDocument()
    expect(within(rows[2]).getByText('GOOGL')).toBeInTheDocument()
  })

  it('PortfolioHoldingsTable - click different column header - sorts ascending', async () => {
    const user = userEvent.setup()
    render(
      <PortfolioHoldingsTable
        holdings={[appleHolding, googleHolding]}
        cash={cashHolding}
        currency="USD"
      />,
    )

    const sharesHeader = screen.getByRole('columnheader', { name: /^shares$/i })
    await user.click(sharesHeader)

    expect(sharesHeader.textContent).toBe('Shares ↑')
    
    // GOOGL has 5 shares, AAPL has 10 shares
    const rows = screen.getAllByRole('row')
    expect(within(rows[1]).getByText('GOOGL')).toBeInTheDocument()
    expect(within(rows[2]).getByText('AAPL')).toBeInTheDocument()
  })

  it('PortfolioHoldingsTable - sort by Shares descending - orders correctly', async () => {
    const user = userEvent.setup()
    render(
      <PortfolioHoldingsTable
        holdings={[googleHolding, appleHolding, teslaHolding]}
        cash={cashHolding}
        currency="USD"
      />,
    )

    const sharesHeader = screen.getByRole('columnheader', { name: /^shares$/i })
    
    // First click: ascending
    await user.click(sharesHeader)
    
    // Second click: descending
    await user.click(sharesHeader)
    expect(sharesHeader.textContent).toBe('Shares ↓')
    
    // Order: AAPL (10), GOOGL (5), TSLA (2.5)
    const rows = screen.getAllByRole('row')
    expect(within(rows[1]).getByText('AAPL')).toBeInTheDocument()
    expect(within(rows[2]).getByText('GOOGL')).toBeInTheDocument()
    expect(within(rows[3]).getByText('TSLA')).toBeInTheDocument()
  })

  it('PortfolioHoldingsTable - cash row always last - after sorting by any column', async () => {
    const user = userEvent.setup()
    render(
      <PortfolioHoldingsTable
        holdings={[googleHolding, appleHolding]}
        cash={cashHolding}
        currency="USD"
      />,
    )

    // Sort by Shares
    const sharesHeader = screen.getByRole('columnheader', { name: /^shares$/i })
    await user.click(sharesHeader)

    let rows = screen.getAllByRole('row')
    let lastRow = rows[rows.length - 1]
    expect(within(lastRow).getByText('USD')).toBeInTheDocument()

    // Sort by Current Value
    const valueHeader = screen.getByRole('columnheader', { name: /^current value$/i })
    await user.click(valueHeader)

    rows = screen.getAllByRole('row')
    lastRow = rows[rows.length - 1]
    expect(within(lastRow).getByText('USD')).toBeInTheDocument()

    // Sort by Unrealised P&L
    const pnlHeader = screen.getByRole('columnheader', { name: /^unrealised p&l$/i })
    await user.click(pnlHeader)

    rows = screen.getAllByRole('row')
    lastRow = rows[rows.length - 1]
    expect(within(lastRow).getByText('USD')).toBeInTheDocument()
  })

  it('PortfolioHoldingsTable - empty holdings - renders cash row only', () => {
    render(
      <PortfolioHoldingsTable
        holdings={[]}
        cash={cashHolding}
        currency="GBP"
      />,
    )

    const rows = screen.getAllByRole('row')
    // Only header and cash row
    expect(rows).toHaveLength(2)
    expect(within(rows[1]).getByText('GBP')).toBeInTheDocument()
    expect(within(rows[1]).getByText('1,000.00')).toBeInTheDocument()
  })

  it('PortfolioHoldingsTable - fractional shares - formats correctly', () => {
    render(
      <PortfolioHoldingsTable
        holdings={[teslaHolding]}
        cash={cashHolding}
        currency="USD"
      />,
    )

    // TSLA has 2.5 shares
    expect(screen.getByText('2.5')).toBeInTheDocument()
  })

  it('PortfolioHoldingsTable - sort by portfolioPercent - handles null values correctly', async () => {
    const user = userEvent.setup()
    render(
      <PortfolioHoldingsTable
        holdings={[appleHolding, holdingWithNullPercent]}
        cash={cashHolding}
        currency="USD"
      />,
    )

    const percentHeader = screen.getByRole('columnheader', { name: /^% of portfolio$/i })
    await user.click(percentHeader)

    // Ascending: non-null first (60.00%), then null
    let rows = screen.getAllByRole('row')
    expect(within(rows[1]).getByText('AAPL')).toBeInTheDocument()
    expect(within(rows[2]).getByText('MSFT')).toBeInTheDocument()

    // Descending: non-null first (60.00%), then null
    await user.click(percentHeader)
    rows = screen.getAllByRole('row')
    expect(within(rows[1]).getByText('AAPL')).toBeInTheDocument()
    expect(within(rows[2]).getByText('MSFT')).toBeInTheDocument()
  })
})
