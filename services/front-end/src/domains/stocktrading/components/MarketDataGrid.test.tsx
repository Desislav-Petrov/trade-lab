import { describe, it, expect } from 'vitest'
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
    fiftyTwoWeekHigh: 200.001,
  },
  {
    ticker: 'MSFT',
    companyName: 'Microsoft Corporation',
    currentPrice: 300.5,
    open: 298.0,
    dayLow: 295.25,
    fiftyTwoWeekHigh: 350.999,
  },
  {
    ticker: 'GOOG',
    companyName: 'Alphabet Inc.',
    currentPrice: 150.0,
    open: 148.0,
    dayLow: 145.0,
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

  it('MarketDataGrid - feedStatus connected with rows - renders table with all six column headers', () => {
    render(<MarketDataGrid rows={sampleRows} feedStatus="connected" />)
    expect(screen.getByText(/^Ticker/)).toBeInTheDocument()
    expect(screen.getByText(/^Company Name/)).toBeInTheDocument()
    expect(screen.getByText(/^Current Price \(USD\)/)).toBeInTheDocument()
    expect(screen.getByText(/^Open \(USD\)/)).toBeInTheDocument()
    expect(screen.getByText(/^Day Low \(USD\)/)).toBeInTheDocument()
    expect(screen.getByText(/^52W High \(USD\)/)).toBeInTheDocument()
  })

  it('MarketDataGrid - feedStatus connected with rows - renders row data', () => {
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
    expect(div.style.overflowX).toBe('auto')
    expect(div.style.overflowY).toBe('auto')
  })
})
