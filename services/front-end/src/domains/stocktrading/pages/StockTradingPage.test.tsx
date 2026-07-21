import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { createElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act } from 'react'
import { StockTradingPage } from './StockTradingPage'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import { useStockTradingStore } from '../hooks/useStockTradingStore'
import type { UserProfile } from '../../user/types/user'
import type { SubscriptionResponse } from '../../marketdata/types/subscription'
import type { AccountListResponse } from '../../ledger/types/account'

vi.mock('../../marketdata/hooks/useSubscriptions', () => ({
  useSubscriptions: vi.fn(),
  useBulkAddSubscriptions: vi.fn(),
  useBulkRemoveSubscriptions: vi.fn(),
  useSupportedTickers: vi.fn(),
}))

vi.mock('../../marketdata/hooks/useMarketDataFeed', () => ({
  useMarketDataFeed: vi.fn(() => ({ rows: [], feedStatus: 'connecting' })),
}))

vi.mock('../../ledger/hooks/useLedger', () => ({
  useActiveAccounts: vi.fn(),
}))

vi.mock('../components/AccountSelector', () => ({
  AccountSelector: ({
    accounts,
    selectedAccountId,
    onSelect,
    isLoading,
    isError,
  }: {
    accounts: { id: string; name: string; currency: string }[]
    selectedAccountId: string | null
    onSelect: (id: string) => void
    isLoading: boolean
    isError: boolean
  }) => {
    if (isLoading)
      return createElement('p', { 'data-testid': 'account-selector-loading' }, 'Loading accounts…')
    if (isError)
      return createElement(
        'p',
        { role: 'alert', 'data-testid': 'account-selector-error' },
        'Could not load accounts.',
      )
    if (accounts.length === 0)
      return createElement(
        'p',
        { 'data-testid': 'account-selector-empty' },
        'No accounts available. Open an account first.',
      )
    return createElement(
      'select',
      {
        'data-testid': 'account-selector',
        value: selectedAccountId ?? '',
        onChange: (e: React.ChangeEvent<HTMLSelectElement>) => onSelect(e.target.value),
      },
      accounts.map((a) =>
        createElement('option', { key: a.id, value: a.id }, `${a.name} (${a.currency})`),
      ),
    )
  },
}))

vi.mock('../components/MarketDataGrid', () => ({
  MarketDataGrid: ({
    onBuy,
  }: {
    rows: unknown[]
    feedStatus: string
    onBuy?: (ticker: string, companyName: string, priceSnapshot: string) => void
  }) =>
    createElement(
      'div',
      { 'data-testid': 'market-data-grid' },
      onBuy
        ? createElement(
            'button',
            {
              onClick: () => onBuy('AAPL', 'Apple Inc.', '180.000'),
              'data-testid': 'trigger-buy',
            },
            'Trigger Buy',
          )
        : null,
    ),
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
      createElement(
        'button',
        { onClick: onRemove, disabled: selectedCount === 0 },
        `Remove selected (${selectedCount})`,
      ),
    ),
}))

vi.mock('../components/BuyPanel', () => ({
  BuyPanel: ({
    ticker,
    companyName,
    priceSnapshot,
    accountId,
    userId,
    onClose,
  }: {
    ticker: string
    companyName: string
    priceSnapshot: string
    accountId: string
    userId: string
    onClose: () => void
  }) =>
    createElement(
      'div',
      { 'data-testid': 'buy-panel' },
      createElement('span', { 'data-testid': 'buy-panel-ticker' }, ticker),
      createElement('span', { 'data-testid': 'buy-panel-company' }, companyName),
      createElement('span', { 'data-testid': 'buy-panel-price' }, priceSnapshot),
      createElement('span', { 'data-testid': 'buy-panel-account' }, accountId),
      createElement('span', { 'data-testid': 'buy-panel-userid' }, userId),
      createElement('button', { onClick: onClose, 'data-testid': 'buy-panel-close' }, 'Close'),
    ),
}))

import {
  useSubscriptions,
  useBulkAddSubscriptions,
  useBulkRemoveSubscriptions,
  useSupportedTickers,
} from '../../marketdata/hooks/useSubscriptions'
import { useActiveAccounts } from '../../ledger/hooks/useLedger'
import { useMarketDataFeed } from '../../marketdata/hooks/useMarketDataFeed'

const mockUseSubscriptions = vi.mocked(useSubscriptions)
const mockUseBulkAddSubscriptions = vi.mocked(useBulkAddSubscriptions)
const mockUseBulkRemoveSubscriptions = vi.mocked(useBulkRemoveSubscriptions)
const mockUseSupportedTickers = vi.mocked(useSupportedTickers)
const mockUseActiveAccounts = vi.mocked(useActiveAccounts)
const mockUseMarketDataFeed = vi.mocked(useMarketDataFeed)

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

const mockActiveAccounts = [
  {
    id: 'acc-1',
    name: 'My USD Account',
    currency: 'USD',
    balance: 100,
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 'acc-2',
    name: 'My GBP Account',
    currency: 'GBP',
    balance: 200,
    status: 'ACTIVE',
    createdAt: '2026-01-02T00:00:00Z',
  },
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
          createElement(Route, {
            path: '/login',
            element: createElement('div', null, 'Login Page'),
          }),
        ),
      ),
    ),
  )
}

function setupMocks(
  overrides: {
    subscriptions?: SubscriptionResponse[]
    isLoading?: boolean
    loadError?: Error | null
    addMutate?: ReturnType<typeof vi.fn>
    removeMutate?: ReturnType<typeof vi.fn>
    addIsPending?: boolean
    removeIsPending?: boolean
    removeError?: Error | null
    activeAccounts?: typeof mockActiveAccounts
    isAccountsLoading?: boolean
    isAccountsError?: boolean
  } = {},
) {
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

  const accounts =
    overrides.activeAccounts !== undefined ? overrides.activeAccounts : mockActiveAccounts
  mockUseActiveAccounts.mockReturnValue({
    data: { accounts } as AccountListResponse,
    isLoading: overrides.isAccountsLoading ?? false,
    isError: overrides.isAccountsError ?? false,
  } as unknown as ReturnType<typeof useActiveAccounts>)
}

describe('StockTradingPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    act(() => useSessionStore.getState().clearSession())
    act(() => useStockTradingStore.getState().clearSelectedAccountId())
    mockUseMarketDataFeed.mockReturnValue({ rows: [], feedStatus: 'connecting' })
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

    expect(addMutate).toHaveBeenCalledWith({ userId: 'u1', tickers: ['AAPL'] }, expect.any(Object))
  })

  it('StockTradingPage - onRemove fires - calls bulkRemoveSubscriptions with selected tickers', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const removeMutate = vi.fn()
    setupMocks({ removeMutate })
    renderPage()

    fireEvent.click(screen.getAllByRole('button', { name: /toggle/i })[0])
    fireEvent.click(screen.getByRole('button', { name: /remove selected/i }))

    expect(removeMutate).toHaveBeenCalledWith(
      { userId: 'u1', tickers: ['AAPL'] },
      expect.any(Object),
    )
  })

  it('StockTradingPage - remove mutation fails - displays error message', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const error = Object.assign(new Error('Server Error'), {
      isAxiosError: true,
      response: { data: { error: 'Ticker not found in subscriptions.' }, status: 404 },
    })
    const removeMutate = vi.fn((_, { onError }: { onError: (e: unknown) => void }) =>
      onError(error),
    )
    setupMocks({ removeMutate })
    renderPage()

    fireEvent.click(screen.getAllByRole('button', { name: /toggle/i })[0])
    fireEvent.click(screen.getByRole('button', { name: /remove selected/i }))

    expect(screen.getAllByRole('alert').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('Ticker not found in subscriptions.')).toBeInTheDocument()
  })

  it('StockTradingPage - AccountSelector rendered - account selector is present on the page', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    setupMocks()
    renderPage()
    expect(screen.getByTestId('account-selector')).toBeInTheDocument()
  })

  it('StockTradingPage - accounts returned with no prior selection - sets first account as default', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    setupMocks({ activeAccounts: mockActiveAccounts })
    renderPage()

    await waitFor(() => {
      expect(useStockTradingStore.getState().selectedAccountId).toBe('acc-1')
    })
  })

  it('StockTradingPage - accounts returned with prior selection - does not change existing selection', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => useStockTradingStore.getState().setSelectedAccountId('acc-2'))
    setupMocks({ activeAccounts: mockActiveAccounts })
    renderPage()

    await waitFor(() => {
      expect(useStockTradingStore.getState().selectedAccountId).toBe('acc-2')
    })
  })

  it('StockTradingPage - no accounts returned - selectedAccountId remains null', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    setupMocks({ activeAccounts: [] })
    renderPage()

    await waitFor(() => {
      expect(useStockTradingStore.getState().selectedAccountId).toBeNull()
    })
  })

  it('StockTradingPage - user selects different account - selectedAccountId updates in store', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    setupMocks({ activeAccounts: mockActiveAccounts })
    renderPage()

    const select = screen.getByTestId('account-selector')
    fireEvent.change(select, { target: { value: 'acc-2' } })

    expect(useStockTradingStore.getState().selectedAccountId).toBe('acc-2')
  })

  it('StockTradingPage - accounts loading - forwards isLoading to AccountSelector', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    setupMocks({ activeAccounts: [], isAccountsLoading: true })
    renderPage()
    expect(screen.getByTestId('account-selector-loading')).toBeInTheDocument()
  })

  it('StockTradingPage - accounts error - forwards isError to AccountSelector', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    setupMocks({ activeAccounts: [], isAccountsError: true })
    renderPage()
    expect(screen.getByTestId('account-selector-error')).toBeInTheDocument()
  })

  it('StockTradingPage - account selected and onBuy triggered - BuyPanel renders with correct props including userId', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => useStockTradingStore.getState().setSelectedAccountId('acc-1'))
    setupMocks({ activeAccounts: mockActiveAccounts })
    renderPage()

    const triggerBtn = screen.getByTestId('trigger-buy')
    fireEvent.click(triggerBtn)

    await waitFor(() => {
      expect(screen.getByTestId('buy-panel')).toBeInTheDocument()
    })
    expect(screen.getByTestId('buy-panel-ticker')).toHaveTextContent('AAPL')
    expect(screen.getByTestId('buy-panel-company')).toHaveTextContent('Apple Inc.')
    expect(screen.getByTestId('buy-panel-price')).toHaveTextContent('180.000')
    expect(screen.getByTestId('buy-panel-account')).toHaveTextContent('acc-1')
    expect(screen.getByTestId('buy-panel-userid')).toHaveTextContent('u1')
  })

  it('StockTradingPage - BuyPanel onClose - removes BuyPanel from DOM', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => useStockTradingStore.getState().setSelectedAccountId('acc-1'))
    setupMocks({ activeAccounts: mockActiveAccounts })
    renderPage()

    fireEvent.click(screen.getByTestId('trigger-buy'))

    await waitFor(() => {
      expect(screen.getByTestId('buy-panel')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByTestId('buy-panel-close'))

    await waitFor(() => {
      expect(screen.queryByTestId('buy-panel')).not.toBeInTheDocument()
    })
  })

  it('StockTradingPage - no account selected - MarketDataGrid does not receive onBuy', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    setupMocks({ activeAccounts: [] })
    renderPage()

    expect(screen.queryByTestId('trigger-buy')).not.toBeInTheDocument()
  })

  it('StockTradingPage - live feed connected with rows - MarketDataGrid remains mounted and receives deferred rows', () => {
    mockUseMarketDataFeed.mockReturnValue({
      rows: [
        {
          ticker: 'AAPL',
          companyName: 'Apple Inc.',
          currentPrice: 180.5,
          open: 179.0,
          dayLow: 178.0,
          dayHigh: 182.0,
          fiftyTwoWeekHigh: 200.0,
        },
      ],
      feedStatus: 'connected',
    })
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => useStockTradingStore.getState().setSelectedAccountId('acc-1'))
    setupMocks({ activeAccounts: mockActiveAccounts })
    renderPage()

    expect(screen.getByTestId('market-data-grid')).toBeInTheDocument()
    expect(screen.getByTestId('trigger-buy')).toBeInTheDocument()
  })
})
