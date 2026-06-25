import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SubscriptionList } from './SubscriptionList'
import type { SubscriptionResponse } from '../../marketdata/types/subscription'

const mockSubscriptions: SubscriptionResponse[] = [
  { ticker: 'AAPL', companyName: 'Apple Inc.' },
  { ticker: 'MSFT', companyName: 'Microsoft Corporation' },
]

describe('SubscriptionList', () => {
  beforeEach(() => vi.clearAllMocks())

  it('SubscriptionList - isLoading true - renders loading indicator', () => {
    render(
      <SubscriptionList
        subscriptions={[]}
        selectedTickers={[]}
        onSelectionChange={vi.fn()}
        isLoading={true}
      />,
    )
    expect(screen.getByText(/loading/i)).toBeInTheDocument()
  })

  it('SubscriptionList - empty subscriptions - renders empty state message', () => {
    render(
      <SubscriptionList
        subscriptions={[]}
        selectedTickers={[]}
        onSelectionChange={vi.fn()}
        isLoading={false}
      />,
    )
    expect(screen.getByText(/you have no subscriptions yet/i)).toBeInTheDocument()
  })

  it('SubscriptionList - with subscriptions - renders ticker and companyName for each row', () => {
    render(
      <SubscriptionList
        subscriptions={mockSubscriptions}
        selectedTickers={[]}
        onSelectionChange={vi.fn()}
        isLoading={false}
      />,
    )
    expect(screen.getByText('AAPL')).toBeInTheDocument()
    expect(screen.getByText('Apple Inc.')).toBeInTheDocument()
    expect(screen.getByText('MSFT')).toBeInTheDocument()
    expect(screen.getByText('Microsoft Corporation')).toBeInTheDocument()
  })

  it('SubscriptionList - checking unchecked checkbox - calls onSelectionChange with ticker added', async () => {
    const user = userEvent.setup()
    const handleSelectionChange = vi.fn()

    render(
      <SubscriptionList
        subscriptions={mockSubscriptions}
        selectedTickers={[]}
        onSelectionChange={handleSelectionChange}
        isLoading={false}
      />,
    )

    await user.click(screen.getByRole('checkbox', { name: /AAPL/i }))

    expect(handleSelectionChange).toHaveBeenCalledOnce()
    expect(handleSelectionChange).toHaveBeenCalledWith(['AAPL'])
  })

  it('SubscriptionList - unchecking checked checkbox - calls onSelectionChange with ticker removed', async () => {
    const user = userEvent.setup()
    const handleSelectionChange = vi.fn()

    render(
      <SubscriptionList
        subscriptions={mockSubscriptions}
        selectedTickers={['AAPL', 'MSFT']}
        onSelectionChange={handleSelectionChange}
        isLoading={false}
      />,
    )

    await user.click(screen.getByRole('checkbox', { name: /AAPL/i }))

    expect(handleSelectionChange).toHaveBeenCalledOnce()
    expect(handleSelectionChange).toHaveBeenCalledWith(['MSFT'])
  })
})
