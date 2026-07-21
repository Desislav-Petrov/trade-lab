import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MarketDataGrid } from './MarketDataGrid'
import type { MarketDataUpdate } from '../../marketdata/api/marketDataFeedApi'

const sampleRows: MarketDataUpdate[] = [
  {
    ticker: 'AAPL',
    companyName: 'Apple Inc.',
    currentPrice: 180.123,
    open: 179.456,
    dayLow: 178.789,
    dayHigh: 182.500,
    fiftyTwoWeekHigh: 200.001,
  },
  {
    ticker: 'MSFT',
    companyName: 'Microsoft Corporation',
    currentPrice: 300.5,
    open: 298.0,
    dayLow: 295.25,
    dayHigh: 305.0,
    fiftyTwoWeekHigh: 350.999,
  },
  {
    ticker: 'GOOG',
    companyName: 'Alphabet Inc.',
    currentPrice: 150.0,
    open: 148.0,
    dayLow: 145.0,
    dayHigh: 155.0,
    fiftyTwoWeekHigh: 180.0,
  },
]

const updatedRows: MarketDataUpdate[] = [
  {
    ticker: 'AAPL',
    companyName: 'Apple Inc.',
    currentPrice: 181.123,
    open: 179.456,
    dayLow: 178.789,
    dayHigh: 183.500,
    fiftyTwoWeekHigh: 200.001,
  },
  {
    ticker: 'MSFT',
    companyName: 'Microsoft Corporation',
    currentPrice: 299.5,
    open: 298.0,
    dayLow: 295.25,
    dayHigh: 305.0,
    fiftyTwoWeekHigh: 350.999,
  },
  {
    ticker: 'GOOG',
    companyName: 'Alphabet Inc.',
    currentPrice: 150.0,
    open: 148.0,
    dayLow: 145.0,
    dayHigh: 155.0,
    fiftyTwoWeekHigh: 180.0,
  },
]

describe('MarketDataGrid', () => {
  it('MarketDataGrid - feedStatus connecting - renders connecting paragraph only', () => {
    render(<MarketDataGrid rows={[]} feedStatus="connecting" />)
    expect(screen.getByText('Connecting…')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('MarketDataGrid - feedStatus error - renders error banner', () => {
    render(<MarketDataGrid rows={[]} feedStatus="error" />)
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText('Unable to connect to price feed.')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('MarketDataGrid - feedStatus lost - renders lost connection banner', () => {
    render(<MarketDataGrid rows={[]} feedStatus="lost" />)
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText('Connection lost. Please refresh the page.')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('MarketDataGrid - feedStatus connected with empty rows - renders no price data message', () => {
    render(<MarketDataGrid rows={[]} feedStatus="connected" />)
    expect(screen.getByText('No price data yet.')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('MarketDataGrid - feedStatus connected with rows - renders table with all seven column headers', () => {
    render(<MarketDataGrid rows={sampleRows} feedStatus="connected" />)
    expect(screen.getByText('Ticker ⇅')).toBeInTheDocument()
    expect(screen.getByText('Company Name ⇅')).toBeInTheDocument()
    expect(screen.getByText('Current Price (USD) ⇅')).toBeInTheDocument()
    expect(screen.getByText('Open (USD) ⇅')).toBeInTheDocument()
    expect(screen.getByText('Day Low (USD) ⇅')).toBeInTheDocument()
    expect(screen.getByText('Day High (USD) ⇅')).toBeInTheDocument()
    expect(screen.getByText('52W High (USD) ⇅')).toBeInTheDocument()
  })

  it('MarketDataGrid - connected rows - renders always visible sort indicators on sortable headers', () => {
    render(<MarketDataGrid rows={sampleRows} feedStatus="connected" />)
    expect(screen.getByText('Ticker ⇅')).toBeInTheDocument()
    expect(screen.getByText('Company Name ⇅')).toBeInTheDocument()
    expect(screen.getByText('Current Price (USD) ⇅')).toBeInTheDocument()
    expect(screen.getByText('Open (USD) ⇅')).toBeInTheDocument()
    expect(screen.getByText('Day Low (USD) ⇅')).toBeInTheDocument()
    expect(screen.getByText('Day High (USD) ⇅')).toBeInTheDocument()
    expect(screen.getByText('52W High (USD) ⇅')).toBeInTheDocument()
  })

  it('MarketDataGrid - connected rows - renders row data', () => {
    render(<MarketDataGrid rows={sampleRows} feedStatus="connected" />)
    expect(screen.getByText('AAPL')).toBeInTheDocument()
    expect(screen.getByText('Apple Inc.')).toBeInTheDocument()
    expect(screen.getByText('MSFT')).toBeInTheDocument()
    expect(screen.getByText('Microsoft Corporation')).toBeInTheDocument()
  })

  it('MarketDataGrid - price values - displays prices with exactly 3 decimal places', () => {
    render(<MarketDataGrid rows={sampleRows} feedStatus="connected" />)
    // AAPL currentPrice 180.123 → '180.123'
    expect(screen.getByText('180.123')).toBeInTheDocument()
    // AAPL open 179.456 → '179.456'
    expect(screen.getByText('179.456')).toBeInTheDocument()
    // AAPL dayLow 178.789 → '178.789'
    expect(screen.getByText('178.789')).toBeInTheDocument()
    // AAPL fiftyTwoWeekHigh 200.001 → '200.001'
    expect(screen.getByText('200.001')).toBeInTheDocument()
    // MSFT currentPrice 300.5 → '300.500'
    expect(screen.getByText('300.500')).toBeInTheDocument()
  })

  it('MarketDataGrid - live feed update - shows green up arrow for higher price and red down arrow for lower price', () => {
    const { rerender } = render(<MarketDataGrid rows={sampleRows} feedStatus="connected" />)

    expect(screen.queryByLabelText('AAPL price increased')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('MSFT price decreased')).not.toBeInTheDocument()

    rerender(<MarketDataGrid rows={updatedRows} feedStatus="connected" />)

    expect(screen.getByLabelText('AAPL price increased')).toHaveClass('text-[var(--color-success)]')
    expect(screen.getByLabelText('MSFT price decreased')).toHaveClass('text-[var(--color-danger)]')
  })

  it('MarketDataGrid - live feed initial snapshot - renders no direction arrows', () => {
    render(<MarketDataGrid rows={sampleRows} feedStatus="connected" />)

    expect(screen.queryByLabelText('AAPL price increased')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('AAPL price decreased')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('MSFT price increased')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('MSFT price decreased')).not.toBeInTheDocument()
  })

  it('MarketDataGrid - clicking Ticker header once - sorts ascending', () => {
    render(<MarketDataGrid rows={sampleRows} feedStatus="connected" />)

    fireEvent.click(screen.getByText(/^Ticker/))

    const rows = screen.getAllByRole('row')
    // Header is rows[0], data rows start at rows[1]
    expect(rows[1]).toHaveTextContent('AAPL')
    expect(rows[2]).toHaveTextContent('GOOG')
    expect(rows[3]).toHaveTextContent('MSFT')
  })

  it('MarketDataGrid - clicking Ticker header twice - sorts descending', () => {
    render(<MarketDataGrid rows={sampleRows} feedStatus="connected" />)

    fireEvent.click(screen.getByText(/^Ticker/))
    fireEvent.click(screen.getByText(/^Ticker ▲/))

    const rows = screen.getAllByRole('row')
    expect(rows[1]).toHaveTextContent('MSFT')
    expect(rows[2]).toHaveTextContent('GOOG')
    expect(rows[3]).toHaveTextContent('AAPL')
  })

  it('MarketDataGrid - clicking Ticker header three times - resets to unsorted', () => {
    render(<MarketDataGrid rows={sampleRows} feedStatus="connected" />)

    fireEvent.click(screen.getByText(/^Ticker/))
    fireEvent.click(screen.getByText(/^Ticker ▲/))
    fireEvent.click(screen.getByText(/^Ticker ▼/))

    // After three clicks, sort is 'none' — original order restored
    const rows = screen.getAllByRole('row')
    expect(rows[1]).toHaveTextContent('AAPL')
    expect(rows[2]).toHaveTextContent('MSFT')
    expect(rows[3]).toHaveTextContent('GOOG')
  })

  it('MarketDataGrid - clicking a different column - resets previous column sort', () => {
    render(<MarketDataGrid rows={sampleRows} feedStatus="connected" />)

    // Sort by Ticker ascending
    fireEvent.click(screen.getByText(/^Ticker/))
    expect(screen.getByText(/^Ticker ▲/)).toBeInTheDocument()

    // Now click Company Name — Ticker sort should reset
    fireEvent.click(screen.getByText(/^Company Name/))
    expect(screen.queryByText(/^Ticker ▲/)).not.toBeInTheDocument()
    expect(screen.getByText(/^Company Name ▲/)).toBeInTheDocument()
  })

  it('MarketDataGrid - table container - has overflowX and overflowY auto styles', () => {
    const { container } = render(<MarketDataGrid rows={sampleRows} feedStatus="connected" />)
    const div = container.firstChild as HTMLElement
    expect(div.className).toContain('overflow-x-auto')
    expect(div.className).toContain('rounded')
    expect(div.className).toContain('border')
  })

  it('MarketDataGrid - bordered cells - renders visible grid lines and header separation', () => {
    render(<MarketDataGrid rows={sampleRows} feedStatus="connected" />)

    expect(screen.getByRole('columnheader', { name: 'Ticker ⇅' })).toHaveClass('border')
    expect(screen.getByRole('columnheader', { name: 'Ticker ⇅' })).toHaveClass('border-b')
    expect(screen.getByRole('cell', { name: /AAPL/ })).toHaveClass('border')
  })

  // Context menu (COMP-1)
  it('MarketDataGrid - right-click on row with onBuy - shows context menu with Buy option', () => {
    const onBuy = vi.fn()
    render(<MarketDataGrid rows={sampleRows} feedStatus="connected" onBuy={onBuy} />)

    const rows = screen.getAllByRole('row')
    fireEvent.contextMenu(rows[1]) // AAPL row

    expect(screen.getByRole('menu')).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'Buy' })).toBeInTheDocument()
  })

  it('MarketDataGrid - clicking Buy in context menu - invokes onBuy with correct args', () => {
    const onBuy = vi.fn()
    render(<MarketDataGrid rows={sampleRows} feedStatus="connected" onBuy={onBuy} />)

    const rows = screen.getAllByRole('row')
    fireEvent.contextMenu(rows[1]) // AAPL row (currentPrice 180.123 → '180.123')

    fireEvent.click(screen.getByRole('menuitem', { name: 'Buy' }))

    expect(onBuy).toHaveBeenCalledWith('AAPL', 'Apple Inc.', '180.123')
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('MarketDataGrid - click outside context menu - dismisses menu without invoking onBuy', () => {
    const onBuy = vi.fn()
    render(<MarketDataGrid rows={sampleRows} feedStatus="connected" onBuy={onBuy} />)

    const rows = screen.getAllByRole('row')
    fireEvent.contextMenu(rows[1])
    expect(screen.getByRole('menu')).toBeInTheDocument()

    fireEvent.mouseDown(document.body)

    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    expect(onBuy).not.toHaveBeenCalled()
  })

  it('MarketDataGrid - press Escape - dismisses context menu without invoking onBuy', () => {
    const onBuy = vi.fn()
    render(<MarketDataGrid rows={sampleRows} feedStatus="connected" onBuy={onBuy} />)

    const rows = screen.getAllByRole('row')
    fireEvent.contextMenu(rows[1])
    expect(screen.getByRole('menu')).toBeInTheDocument()

    fireEvent.keyDown(document, { key: 'Escape' })

    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    expect(onBuy).not.toHaveBeenCalled()
  })

  it('MarketDataGrid - right-click without onBuy prop - does not show context menu', () => {
    render(<MarketDataGrid rows={sampleRows} feedStatus="connected" />)

    const rows = screen.getAllByRole('row')
    fireEvent.contextMenu(rows[1])

    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('MarketDataGrid - dayHigh column - renders Day High (USD) header', () => {
    render(<MarketDataGrid rows={sampleRows} feedStatus="connected" />)
    expect(screen.getByText('Day High (USD) ⇅')).toBeInTheDocument()
  })

  it('MarketDataGrid - dayHigh column - displays dayHigh value formatted to 3dp', () => {
    render(<MarketDataGrid rows={sampleRows} feedStatus="connected" />)
    expect(screen.getByText('182.500')).toBeInTheDocument()
  })

  it('MarketDataGrid - dayHigh column - clicking Day High header sorts ascending then descending then unsorted', () => {
    render(<MarketDataGrid rows={sampleRows} feedStatus="connected" />)

    fireEvent.click(screen.getByText(/^Day High/))
    // ascending: GOOG(155.0) < AAPL(182.5) < MSFT(305.0)
    let rows = screen.getAllByRole('row')
    expect(rows[1]).toHaveTextContent('GOOG')
    expect(rows[2]).toHaveTextContent('AAPL')
    expect(rows[3]).toHaveTextContent('MSFT')

    fireEvent.click(screen.getByText(/^Day High \(USD\) ▲/))
    // descending: MSFT, AAPL, GOOG
    rows = screen.getAllByRole('row')
    expect(rows[1]).toHaveTextContent('MSFT')
    expect(rows[2]).toHaveTextContent('AAPL')
    expect(rows[3]).toHaveTextContent('GOOG')

    fireEvent.click(screen.getByText(/^Day High \(USD\) ▼/))
    // unsorted — original order
    rows = screen.getAllByRole('row')
    expect(rows[1]).toHaveTextContent('AAPL')
    expect(rows[2]).toHaveTextContent('MSFT')
    expect(rows[3]).toHaveTextContent('GOOG')
  })
})
