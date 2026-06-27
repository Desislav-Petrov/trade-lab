import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { createElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act } from 'react'
import { StockTradingPage } from './StockTradingPage'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import type { UserProfile } from '../../user/types/user'
import type { SubscriptionResponse } from '../../marketdata/types/subscription'

vi.mock('../../marketdata/hooks/useSubscriptions', () => ({
  useSubscriptions: vi.fn(),
  useBulkAddSubscriptions: vi.fn(),
  useBulkRemoveSubscriptions: vi.fn(),
  useSupportedTickers: vi.fn(),
}))

vi.mock('../../marketdata/hooks/useMarketDataFeed', () => ({
  useMarketDataFeed: vi.fn(() => ({ rows: [], feedStatus: 'connecting' })),
}))

vi.mock('../components/MarketDataGrid', () => ({
  MarketDataGrid: () => createElement('div', { 'data-testid': 'market-data-grid' }),
}))

vi.mock('../components/SubscriptionList', () => ({
  SubscriptionList: ({
    subscriptions,
    isLoading,
    selectedTickers,
    onSelectionChange,
  }: {
    subscriptions: SubscriptionResponse[]
    isLoading: boolean
    selectedTickers: string[]
    onSelectionChange: (tickers: string[]) => void
  }) => {
    if (isLoading) return createElement('p', null, 'Loading...')
    if (subscriptions.length === 0)
      return createElement('p', { 'data-testid': 'empty-state' }, 'You have no subscriptions yet.')
    return createElement(
      'ul',
      { 'data-testid': 'subscription-list' },
      subscriptions.map((s) =>
        createElement(
          'li',
          { key: s.ticker },
          s.ticker,
          createElement(
            'button',
            {
              onClick: () =>
                onSelectionChange(
                  selectedTickers.includes(s.ticker)
                    ? selectedTickers.filter((t) => t !== s.ticker)
                    : [...selectedTickers, s.ticker],
                ),
            },
            'Toggle',
          ),
        ),
      ),
    )
  },
}))

vi.mock('../components/AddTickerPanel', () => ({
  AddTickerPanel: ({
    onAdd,
    onClose,
    errorMessage,
  }: {
    availableTickers: SubscriptionResponse[]
    onAdd: (tickers: string[]) => void
    onClose: () => void
    isLoading: boolean
    errorMessage: string | null
  }) =>
    createElement(
      'div',
      { 'data-testid': 'add-ticker-panel' },
      errorMessage ? createElement('p', { role: 'alert' }, errorMessage) : null,
      createElement('button', { onClick: () => onAdd(['AAPL']) }, 'Confirm Add'),
      createElement('button', { onClick: onClose }, 'Close Panel'),
    ),
}))

vi.mock('../components/RemoveTickerBar', () => ({
  RemoveTickerBar: ({
    selectedCount,
    onRemove,
  }: {
    selectedCount: number
    onRemove: () => void
    isLoading: boolean
  }) =>
    createElement(
      'div',
      { 'data-testid': 'remove-ticker-bar' },
      createElement('button', { onClick: onRemove, disabled: selectedCount === 0 }, `Remove selected (${selectedCount})`),
    ),
}))

import {
  useSubscriptions,
  useBulkAddSubscriptions,
  useBulkRemoveSubscriptions,
  useSupportedTickers,
} from '../../marketdata/hooks/useSubscriptions'

const mockUseSubscriptions = vi.mocked(useSubscriptions)
const mockUseBulkAddSubscriptions = vi.mocked(useBulkAddSubscriptions)
const mockUseBulkRemoveSubscriptions = vi.mocked(useBulkRemoveSubscriptions)
const mockUseSupportedTickers = vi.mocked(useSupportedTickers)

const mockProfile: UserProfile = {
  userId: 'u1',
  firstName: 'Jane',
  lastName: 'Doe',
  address: '123 Main St',
  email: 'jane@example.com',
  status: 'active',
  createdAt: '2026-01-01T00:00:00Z',
}

const mockSubscriptions: SubscriptionResponse[] = [
  { ticker: 'AAPL', companyName: 'Apple Inc.' },
  { ticker: 'MSFT', companyName: 'Microsoft Corporation' },
]

function renderPage(initialPath = '/trade') {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return render(
    createElement(
      QueryClientProvider,
      { client: queryClient },
      createElement(
        MemoryRouter,
        { initialEntries: [initialPath] },
        createElement(
          Routes,
          null,
          createElement(Route, { path: '/trade', element: createElement(StockTradingPage) }),
          createElement(Route, { path: '/login', element: createElement('div', null, 'Login Page') }),
        ),
      ),
    ),
  )
}

function setupMocks(overrides: {
  subscriptions?: SubscriptionResponse[]
  isLoading?: boolean
  loadError?: Error | null
  addMutate?: ReturnType<typeof vi.fn>
  removeMutate?: ReturnType<typeof vi.fn>
  addIsPending?: boolean
  removeIsPending?: boolean
  removeError?: Error | null
} = {}) {
  mockUseSubscriptions.mockReturnValue({
    data: overrides.subscriptions !== undefined ? overrides.subscriptions : mockSubscriptions,
    isLoading: overrides.isLoading ?? false,
    error: overrides.loadError ?? null,
  } as unknown as ReturnType<typeof useSubscriptions>)

  mockUseBulkAddSubscriptions.mockReturnValue({
    mutate: overrides.addMutate ?? vi.fn(),
    isPending: overrides.addIsPending ?? false,
    error: null,
  } as unknown as ReturnType<typeof useBulkAddSubscriptions>)

  mockUseBulkRemoveSubscriptions.mockReturnValue({
    mutate: overrides.removeMutate ?? vi.fn(),
    isPending: overrides.removeIsPending ?? false,
    error: overrides.removeError ?? null,
  } as unknown as ReturnType<typeof useBulkRemoveSubscriptions>)

  mockUseSupportedTickers.mockReturnValue({
    data: [
      { ticker: 'AAPL', companyName: 'Apple Inc.' },
      { ticker: 'MSFT', companyName: 'Microsoft Corporation' },
      { ticker: 'GOOG', companyName: 'Alphabet Inc.' },
    ],
    isLoading: false,
    error: null,
  } as unknown as ReturnType<typeof useSupportedTickers>)
}

describe('StockTradingPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    act(() => useSessionStore.getState().clearSession())
  })

  it('StockTradingPage - no session - redirects to /login', () => {
    setupMocks()
    renderPage()
    expect(screen.getByText('Login Page')).toBeInTheDocument()
  })

  it('StockTradingPage - session exists with subscriptions - renders subscription list', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    setupMocks()
    renderPage()
    expect(screen.getByTestId('subscription-list')).toBeInTheDocument()
    expect(screen.getByText('AAPL')).toBeInTheDocument()
    expect(screen.getByText('MSFT')).toBeInTheDocument()
  })

  it('StockTradingPage - session exists with empty subscriptions - renders empty state', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    setupMocks({ subscriptions: [] })
    renderPage()
    expect(screen.getByTestId('empty-state')).toBeInTheDocument()
    expect(screen.getByText(/you have no subscriptions yet/i)).toBeInTheDocument()
  })

  it('StockTradingPage - Add tickers button clicked - opens AddTickerPanel', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    setupMocks()
    renderPage()

    expect(screen.queryByTestId('add-ticker-panel')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /add tickers/i }))
    expect(screen.getByTestId('add-ticker-panel')).toBeInTheDocument()
  })

  it('StockTradingPage - onAdd fires - calls bulkAddSubscriptions with correct tickers', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const addMutate = vi.fn()
    setupMocks({ addMutate })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /add tickers/i }))
    fireEvent.click(screen.getByRole('button', { name: /confirm add/i }))

    expect(addMutate).toHaveBeenCalledWith(
      { userId: 'u1', tickers: ['AAPL'] },
      expect.any(Object),
    )
  })

  it('StockTradingPage - onRemove fires - calls bulkRemoveSubscriptions with selected tickers', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const removeMutate = vi.fn()
    setupMocks({ removeMutate })
    renderPage()

    // Select AAPL via SubscriptionList toggle
    fireEvent.click(screen.getAllByRole('button', { name: /toggle/i })[0])
    // Now click remove
    fireEvent.click(screen.getByRole('button', { name: /remove selected/i }))

    expect(removeMutate).toHaveBeenCalledWith(
      { userId: 'u1', tickers: ['AAPL'] },
      expect.any(Object),
    )
  })

  it('StockTradingPage - subscriptions and grid are visibly separated', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    setupMocks()
    renderPage()

    const divider = screen.getByTestId('subscriptions-grid-divider')
    expect(divider).toHaveClass('border-t')
    expect(divider).toHaveClass('pt-6')
    expect(screen.getByTestId('subscription-list')).toBeInTheDocument()
    expect(screen.getByTestId('market-data-grid')).toBeInTheDocument()
  })

  it('StockTradingPage - remove mutation fails - displays error message', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const error = Object.assign(new Error('Server Error'), {
      isAxiosError: true,
      response: { data: { error: 'Ticker not found in subscriptions.' }, status: 404 },
    })
    const removeMutate = vi.fn((_, { onError }: { onError: (e: unknown) => void }) => onError(error))
    setupMocks({ removeMutate })
    renderPage()

    // Select AAPL
    fireEvent.click(screen.getAllByRole('button', { name: /toggle/i })[0])
    fireEvent.click(screen.getByRole('button', { name: /remove selected/i }))

    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText('Ticker not found in subscriptions.')).toBeInTheDocument()
  })
})
