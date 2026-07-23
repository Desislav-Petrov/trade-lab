import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, within } from '@testing-library/react'
import { PortfolioHoldingsTable } from './PortfolioHoldingsTable'
import type { StockHolding, CashHolding } from '../types/portfolio.types'

const mockHoldings: StockHolding[] = [
  {
    ticker: 'MSFT',
    quantity: 5,
    currentPrice: 300.0,
    currentValue: 1500.0,
    minPrice: 280.0,
    maxPrice: 320.0,
    avgPrice: 290.0,
    portfolioPercent: 60.0,
    unrealisedPnL: 50.0,
  },
  {
    ticker: 'AAPL',
    quantity: 10,
    currentPrice: 150.0,
    currentValue: 1000.0,
    minPrice: 140.0,
    maxPrice: 160.0,
    avgPrice: 145.0,
    portfolioPercent: 40.0,
    unrealisedPnL: -50.0,
  },
]

const mockCash: CashHolding = {
  balance: 500.0,
  currency: 'USD',
  portfolioPercent: 20.0,
}

describe('PortfolioHoldingsTable', () => {
  it('PortfolioHoldingsTable - renders - all column headers are present', () => {
    render(
      <PortfolioHoldingsTable holdings={mockHoldings} cash={mockCash} currency="USD" />
    )

    expect(screen.getByRole('columnheader', { name: /ticker/i })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: /shares/i })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: /current value/i })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: /min share price/i })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: /max share price/i })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: /avg bought price/i })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: /% of portfolio/i })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: /unrealised p&l/i })).toBeInTheDocument()
  })

  it('PortfolioHoldingsTable - renders - all columns show correct values for stock rows', () => {
    render(
      <PortfolioHoldingsTable holdings={[mockHoldings[0]]} cash={mockCash} currency="USD" />
    )

    const rows = screen.getAllByRole('row')
    // rows[0] = header, rows[1] = MSFT, rows[2] = cash
    const msftRow = rows[1]
    const cells = within(msftRow).getAllByRole('cell')

    expect(cells[0]).toHaveTextContent('MSFT')
    expect(cells[1]).toHaveTextContent('5')
    expect(cells[2]).toHaveTextContent('1500.00')
    expect(cells[3]).toHaveTextContent('280.00')
    expect(cells[4]).toHaveTextContent('320.00')
    expect(cells[5]).toHaveTextContent('290.00')
    expect(cells[6]).toHaveTextContent('60.00')
    expect(cells[7]).toHaveTextContent('50.00')
  })

  it('PortfolioHoldingsTable - cash row - always renders last with correct values', () => {
    render(
      <PortfolioHoldingsTable holdings={mockHoldings} cash={mockCash} currency="USD" />
    )

    const cashRow = screen.getByTestId('cash-row')
    const cells = within(cashRow).getAllByRole('cell')

    expect(cells[0]).toHaveTextContent('USD')
    expect(cells[1]).toHaveTextContent('—')
    expect(cells[2]).toHaveTextContent('500.00')
    expect(cells[3]).toHaveTextContent('—')
    expect(cells[4]).toHaveTextContent('—')
    expect(cells[5]).toHaveTextContent('—')
    expect(cells[6]).toHaveTextContent('20.00')
    expect(cells[7]).toHaveTextContent('—')
  })

  it('PortfolioHoldingsTable - default sort - renders tickers in ascending order', () => {
    render(
      <PortfolioHoldingsTable holdings={mockHoldings} cash={mockCash} currency="USD" />
    )

    const rows = screen.getAllByRole('row')
    // rows[0] = header, rows[1] = AAPL (asc), rows[2] = MSFT, rows[3] = cash
    expect(within(rows[1]).getAllByRole('cell')[0]).toHaveTextContent('AAPL')
    expect(within(rows[2]).getAllByRole('cell')[0]).toHaveTextContent('MSFT')
    // cash row is last
    expect(screen.getByTestId('cash-row')).toBe(rows[3])
  })

  it('PortfolioHoldingsTable - click Ticker header - descending then default', () => {
    render(
      <PortfolioHoldingsTable holdings={mockHoldings} cash={mockCash} currency="USD" />
    )

    const tickerHeader = screen.getByRole('columnheader', { name: /ticker/i })

    // Click once → desc
    fireEvent.click(tickerHeader)
    let rows = screen.getAllByRole('row')
    expect(within(rows[1]).getAllByRole('cell')[0]).toHaveTextContent('MSFT')
    expect(within(rows[2]).getAllByRole('cell')[0]).toHaveTextContent('AAPL')
    // cash still last
    expect(screen.getByTestId('cash-row')).toBe(rows[3])

    // Click twice → default (asc by ticker)
    fireEvent.click(tickerHeader)
    rows = screen.getAllByRole('row')
    expect(within(rows[1]).getAllByRole('cell')[0]).toHaveTextContent('AAPL')
    expect(within(rows[2]).getAllByRole('cell')[0]).toHaveTextContent('MSFT')
    expect(screen.getByTestId('cash-row')).toBe(rows[3])
  })

  it('PortfolioHoldingsTable - click Current Value header - sorts stock rows ascending; cash stays last', () => {
    render(
      <PortfolioHoldingsTable holdings={mockHoldings} cash={mockCash} currency="USD" />
    )

    fireEvent.click(screen.getByRole('columnheader', { name: /current value/i }))

    const rows = screen.getAllByRole('row')
    // AAPL currentValue=1000 < MSFT currentValue=1500
    expect(within(rows[1]).getAllByRole('cell')[0]).toHaveTextContent('AAPL')
    expect(within(rows[2]).getAllByRole('cell')[0]).toHaveTextContent('MSFT')
    expect(screen.getByTestId('cash-row')).toBe(rows[3])
  })

  it('PortfolioHoldingsTable - portfolioPercent null - renders as dash', () => {
    const holdingWithNull: StockHolding = { ...mockHoldings[0], portfolioPercent: null }
    const cashWithNull: CashHolding = { ...mockCash, portfolioPercent: null }

    render(
      <PortfolioHoldingsTable holdings={[holdingWithNull]} cash={cashWithNull} currency="USD" />
    )

    const rows = screen.getAllByRole('row')
    const stockCells = within(rows[1]).getAllByRole('cell')
    expect(stockCells[6]).toHaveTextContent('—')

    const cashCells = within(screen.getByTestId('cash-row')).getAllByRole('cell')
    expect(cashCells[6]).toHaveTextContent('—')
  })

  it('PortfolioHoldingsTable - positive unrealisedPnL - has pnl-positive class', () => {
    render(
      <PortfolioHoldingsTable holdings={[mockHoldings[0]]} cash={mockCash} currency="USD" />
    )

    const rows = screen.getAllByRole('row')
    const pnlCell = within(rows[1]).getAllByRole('cell')[7]
    expect(pnlCell).toHaveClass('pnl-positive')
  })

  it('PortfolioHoldingsTable - negative unrealisedPnL - has pnl-negative class', () => {
    render(
      <PortfolioHoldingsTable holdings={[mockHoldings[1]]} cash={mockCash} currency="USD" />
    )

    const rows = screen.getAllByRole('row')
    const pnlCell = within(rows[1]).getAllByRole('cell')[7]
    expect(pnlCell).toHaveClass('pnl-negative')
  })

  it('PortfolioHoldingsTable - empty holdings - renders cash row only with no stock rows', () => {
    render(
      <PortfolioHoldingsTable holdings={[]} cash={mockCash} currency="USD" />
    )

    const rows = screen.getAllByRole('row')
    // rows[0] = header, rows[1] = cash
    expect(rows).toHaveLength(2)
    expect(screen.getByTestId('cash-row')).toBeInTheDocument()
  })

  it('PortfolioHoldingsTable - empty holdings - no error rendered', () => {
    const { container } = render(
      <PortfolioHoldingsTable holdings={[]} cash={mockCash} currency="USD" />
    )

    expect(container.querySelector('[role="alert"]')).toBeNull()
  })

  it('PortfolioHoldingsTable - sorting other column - cash row stays last', () => {
    render(
      <PortfolioHoldingsTable holdings={mockHoldings} cash={mockCash} currency="USD" />
    )

    fireEvent.click(screen.getByRole('columnheader', { name: /unrealised p&l/i }))

    const rows = screen.getAllByRole('row')
    const lastDataRow = rows[rows.length - 1]
    expect(lastDataRow).toBe(screen.getByTestId('cash-row'))
  })

  it('PortfolioHoldingsTable - monetary values - displayed to 2 decimal places', () => {
    const holding: StockHolding = {
      ticker: 'TEST',
      quantity: 1,
      currentPrice: 10.1,
      currentValue: 10.1,
      minPrice: 9.999,
      maxPrice: 10.555,
      avgPrice: 10.0,
      portfolioPercent: 100.0,
      unrealisedPnL: 0.1,
    }

    render(
      <PortfolioHoldingsTable holdings={[holding]} cash={mockCash} currency="USD" />
    )

    const rows = screen.getAllByRole('row')
    const cells = within(rows[1]).getAllByRole('cell')
    expect(cells[2]).toHaveTextContent('10.10')
    expect(cells[3]).toHaveTextContent('10.00') // 9.999 toFixed(2) = 10.00
    // 10.555 toFixed(2) in V8 rounds to 10.55 (half-even / half-down)
    expect(cells[4]).toHaveTextContent((10.555).toFixed(2))
  })

  it('PortfolioHoldingsTable - right-click stock row - shows context menu with Sell option', () => {
    const onSell = vi.fn()
    render(
      <PortfolioHoldingsTable holdings={mockHoldings} cash={mockCash} currency="USD" onSell={onSell} />
    )

    const rows = screen.getAllByRole('row')
    // rows[0] = header, rows[1] = AAPL (sorted asc), rows[2] = MSFT, rows[3] = cash
    fireEvent.contextMenu(rows[1])

    expect(screen.getByRole('menu')).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'Sell' })).toBeInTheDocument()
  })

  it('PortfolioHoldingsTable - right-click Sell menuitem - calls onSell with correct ticker and quantity', () => {
    const onSell = vi.fn()
    render(
      <PortfolioHoldingsTable holdings={[mockHoldings[0]]} cash={mockCash} currency="USD" onSell={onSell} />
    )

    const rows = screen.getAllByRole('row')
    // rows[0] = header, rows[1] = MSFT (quantity: 5)
    fireEvent.contextMenu(rows[1])
    fireEvent.click(screen.getByRole('menuitem', { name: 'Sell' }))

    expect(onSell).toHaveBeenCalledWith('MSFT', 5)
  })

  it('PortfolioHoldingsTable - right-click cash row - does not show context menu', () => {
    const onSell = vi.fn()
    render(
      <PortfolioHoldingsTable holdings={mockHoldings} cash={mockCash} currency="USD" onSell={onSell} />
    )

    fireEvent.contextMenu(screen.getByTestId('cash-row'))

    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('PortfolioHoldingsTable - Escape key - dismisses context menu', () => {
    const onSell = vi.fn()
    render(
      <PortfolioHoldingsTable holdings={mockHoldings} cash={mockCash} currency="USD" onSell={onSell} />
    )

    const rows = screen.getAllByRole('row')
    fireEvent.contextMenu(rows[1])
    expect(screen.getByRole('menu')).toBeInTheDocument()

    fireEvent.keyDown(document, { key: 'Escape' })

    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('PortfolioHoldingsTable - click outside - dismisses context menu', async () => {
    const onSell = vi.fn()
    render(
      <div>
        <PortfolioHoldingsTable holdings={mockHoldings} cash={mockCash} currency="USD" onSell={onSell} />
        <div data-testid="outside">Outside</div>
      </div>
    )

    const rows = screen.getAllByRole('row')
    fireEvent.contextMenu(rows[1])
    expect(screen.getByRole('menu')).toBeInTheDocument()

    fireEvent.mouseDown(screen.getByTestId('outside'))

    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })
})
