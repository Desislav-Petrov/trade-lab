import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AddTickerPanel } from './AddTickerPanel'
import type { SubscriptionResponse } from '../../marketdata/types/subscription'

const mockTickers: SubscriptionResponse[] = [
  { ticker: 'AAPL', companyName: 'Apple Inc.' },
  { ticker: 'AMZN', companyName: 'Amazon.com Inc.' },
  { ticker: 'MSFT', companyName: 'Microsoft Corporation' },
]

function renderPanel(props: Partial<React.ComponentProps<typeof AddTickerPanel>> = {}) {
  const defaults = {
    availableTickers: mockTickers,
    onAdd: vi.fn(),
    onClose: vi.fn(),
    isLoading: false,
    errorMessage: null,
  }
  return render(<AddTickerPanel {...defaults} {...props} />)
}

describe('AddTickerPanel', () => {
  beforeEach(() => vi.clearAllMocks())

  it('AddTickerPanel - renders - shows full available list initially', () => {
    renderPanel()
    expect(screen.getByText('AAPL')).toBeInTheDocument()
    expect(screen.getByText('AMZN')).toBeInTheDocument()
    expect(screen.getByText('MSFT')).toBeInTheDocument()
  })

  it('AddTickerPanel - typing in filter - reduces visible items case-insensitively', async () => {
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByRole('textbox', { name: /filter tickers/i }), 'aa')

    expect(screen.getByText('AAPL')).toBeInTheDocument()
    expect(screen.queryByText('AMZN')).not.toBeInTheDocument()
    expect(screen.queryByText('MSFT')).not.toBeInTheDocument()
  })

  it('AddTickerPanel - no selection - Add button is disabled', () => {
    renderPanel()
    expect(screen.getByRole('button', { name: /^add$/i })).toBeDisabled()
  })

  it('AddTickerPanel - after checking a ticker - Add button is enabled', async () => {
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByRole('checkbox', { name: /AAPL/i }))

    expect(screen.getByRole('button', { name: /^add$/i })).toBeEnabled()
  })

  it('AddTickerPanel - clicking Add - calls onAdd with selected tickers', async () => {
    const user = userEvent.setup()
    const onAdd = vi.fn()
    renderPanel({ onAdd })

    await user.click(screen.getByRole('checkbox', { name: /AAPL/i }))
    await user.click(screen.getByRole('checkbox', { name: /MSFT/i }))
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    expect(onAdd).toHaveBeenCalledOnce()
    expect(onAdd).toHaveBeenCalledWith(['AAPL', 'MSFT'])
  })

  it('AddTickerPanel - errorMessage non-null - renders error message', () => {
    renderPanel({ errorMessage: 'Something went wrong.' })
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText('Something went wrong.')).toBeInTheDocument()
  })

  it('AddTickerPanel - clicking Cancel - calls onClose', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    renderPanel({ onClose })

    await user.click(screen.getByRole('button', { name: /cancel/i }))

    expect(onClose).toHaveBeenCalledOnce()
  })

  it('AddTickerPanel - isLoading true - Add button shows loading state and is disabled', async () => {
    const user = userEvent.setup()
    renderPanel({ isLoading: true })

    // Even with a checked ticker the Add button must be disabled during loading
    // We check disabled state here without checking a ticker first (selectedCount=0 + isLoading)
    expect(screen.getByRole('button', { name: /adding/i })).toBeDisabled()
  })

  it('AddTickerPanel - isLoading true with selection - Add button is still disabled', async () => {
    const user = userEvent.setup()
    // Start not loading so we can check a box, then re-render with isLoading
    const { rerender } = renderPanel({ isLoading: false })

    await user.click(screen.getByRole('checkbox', { name: /AAPL/i }))
    expect(screen.getByRole('button', { name: /^add$/i })).toBeEnabled()

    rerender(
      <AddTickerPanel
        availableTickers={mockTickers}
        onAdd={vi.fn()}
        onClose={vi.fn()}
        isLoading={true}
        errorMessage={null}
      />,
    )

    expect(screen.getByRole('button', { name: /adding/i })).toBeDisabled()
  })
})
