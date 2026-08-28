import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { createElement } from 'react'
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
} from '@tanstack/react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act } from 'react'
import { PortfolioPage } from './PortfolioPage'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import { usePortfolioStore } from '../hooks/usePortfolioStore'
import type { UserResponse } from '../../user/types/user'
import type { AccountResponse } from '../../ledger/types/account'
import type { PortfolioHoldingsResponse } from '../types/portfolio.types'
import type { SellPanelHook } from '../../stocktrading/hooks/useSellPanel'

vi.mock('../../ledger/hooks/useLedger', () => ({
  useActiveAccounts: vi.fn(),
}))

vi.mock('../hooks/usePortfolioHoldings', () => ({
  usePortfolioHoldings: vi.fn(),
}))

vi.mock('../components/PortfolioAccountSelector', () => ({
  PortfolioAccountSelector: ({
    accounts,
    selectedAccountId,
    onAccountChange,
  }: {
    accounts: AccountResponse[]
    selectedAccountId: string | null
    onAccountChange: (id: string) => void
  }) => {
    if (accounts.length === 0) {
      return createElement('p', null, 'No accounts available. Open an account first.')
    }
    return createElement(
      'select',
      {
        'aria-label': 'Select account',
        value: selectedAccountId ?? '',
        onChange: (e: React.ChangeEvent<HTMLSelectElement>) => onAccountChange(e.target.value),
      },
      accounts.map((a: AccountResponse) =>
        createElement('option', { key: a.id, value: a.id }, `${a.name} (${a.currency})`),
      ),
    )
  },
}))

let capturedOnSell: ((ticker: string, maxQuantity: number) => void) | undefined

vi.mock('../components/PortfolioHoldingsTable', () => ({
  PortfolioHoldingsTable: ({
    holdings,
    cash,
    onSell,
  }: PortfolioHoldingsResponse & { onSell?: (ticker: string, maxQuantity: number) => void }) => {
    capturedOnSell = onSell
    return createElement(
      'div',
      { 'data-testid': 'holdings-table' },
      `Holdings: ${holdings.length}, Cash: ${cash.balance}`,
    )
  },
}))

vi.mock('../components/InsightsTab', () => ({
  InsightsTab: ({
    isLoading,
    isError,
  }: {
    isLoading: boolean
    isError: boolean
    insights: unknown
    currency: string
  }) => {
    if (isLoading)
      return createElement('div', { 'data-testid': 'insights-loading' }, 'Loading insights...')
    if (isError)
      return createElement(
        'div',
        { 'data-testid': 'insights-error' },
        'Could not load insights. Please try again.',
      )
    return createElement('div', { 'data-testid': 'insights-tab' }, 'Insights')
  },
}))

vi.mock('../components/AdvancedInsightsTab', () => ({
  AdvancedInsightsTab: ({ accountId }: { accountId: string | null }) =>
    createElement(
      'div',
      { 'data-testid': 'advanced-insights-tab' },
      `accountId=${accountId ?? ''}`,
    ),
}))

vi.mock('../../stocktrading/hooks/useSellPanel', () => ({
  useSellPanel: vi.fn(),
}))

vi.mock('../../stocktrading/components/SellPanel', () => ({
  SellPanel: ({ ticker, maxQuantity }: { ticker: string; maxQuantity: number }) =>
    createElement(
      'div',
      { 'data-testid': 'sell-panel' },
      `SellPanel: ${ticker} qty=${maxQuantity}`,
    ),
}))

import { useActiveAccounts } from '../../ledger/hooks/useLedger'
import { usePortfolioHoldings } from '../hooks/usePortfolioHoldings'
import { useSellPanel } from '../../stocktrading/hooks/useSellPanel'

const mockUseActiveAccounts = vi.mocked(useActiveAccounts)
const mockUsePortfolioHoldings = vi.mocked(usePortfolioHoldings)
const mockUseSellPanel = vi.mocked(useSellPanel)

function buildSellPanelHook(overrides: Partial<SellPanelHook> = {}): SellPanelHook {
  return {
    isOpen: false,
    ticker: null,
    maxQuantity: null,
    priceSnapshot: null,
    idempotencyKey: null,
    quantity: '',
    validationError: null,
    isFetchingPrice: false,
    priceError: null,
    isSubmitting: false,
    submitError: null,
    result: null,
    openSellPanel: vi.fn(),
    closeSellPanel: vi.fn(),
    setQuantity: vi.fn(),
    confirmSell: vi.fn(),
    ...overrides,
  }
}

const mockProfile: UserResponse = {
  userId: 'u1',
  firstName: 'Jane',
  lastName: 'Doe',
  address: '123 Main St',
  email: 'jane@example.com',
  status: 'active',
  createdAt: '2026-01-01T00:00:00Z',
  settings: { feedType: 'SYNTHETIC', updatedAt: '2026-01-01T00:00:00Z' },
}

const mockAccount: AccountResponse = {
  id: 'acc-1',
  name: 'My USD Account',
  currency: 'USD',
  balance: 1000,
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
}

const mockHoldingsResponse: PortfolioHoldingsResponse = {
  holdings: [
    {
      ticker: 'AAPL',
      quantity: 10,
      currentPrice: 150.0,
      currentValue: 1500.0,
      minPrice: 140.0,
      maxPrice: 160.0,
      avgPrice: 145.0,
      portfolioPercent: 75.0,
      unrealisedPnL: 50.0,
    },
  ],
  cash: {
    balance: 500.0,
    currency: 'USD',
    portfolioPercent: 25.0,
  },
}

function buildHoldingsHookReturn(overrides: Record<string, unknown> = {}) {
  return {
    data: undefined,
    holdings: [],
    cash: undefined,
    insights: undefined,
    isLoading: false,
    isError: false,
    error: null,
    fetchStatus: 'idle',
    isSuccess: false,
    ...overrides,
  } as unknown as ReturnType<typeof usePortfolioHoldings>
}

async function renderPage(initialPath = '/portfolio') {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  const rootRoute = createRootRoute()
  const portfolioRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/portfolio',
    component: PortfolioPage,
  })
  const loginRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/login',
    component: () => createElement('div', null, 'Login Page'),
  })
  const routeTree = rootRoute.addChildren([portfolioRoute, loginRoute])
  const router = createRouter({
    routeTree,
    history: createMemoryHistory({ initialEntries: [initialPath] }),
  })
  await router.load()
  let result!: ReturnType<typeof render>
  await act(async () => {
    result = render(
      createElement(
        QueryClientProvider,
        { client: queryClient },
        createElement(RouterProvider, { router }),
      ),
    )
  })
  return result
}

describe('PortfolioPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    capturedOnSell = undefined
    act(() => {
      useSessionStore.getState().clearSession()
      usePortfolioStore.setState({ selectedAccountId: null, hiddenSymbols: new Set() })
    })
    mockUseSellPanel.mockReturnValue(buildSellPanelHook())
  })

  it('PortfolioPage - no session - redirects to /login', async () => {
    mockUseActiveAccounts.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue(buildHoldingsHookReturn())

    await renderPage()

    expect(await screen.findByText('Login Page')).toBeInTheDocument()
  })

  it('PortfolioPage - session exists - renders heading', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue(buildHoldingsHookReturn())

    await renderPage()

    expect(screen.getByRole('heading', { name: /portfolio/i })).toBeInTheDocument()
  })

  it('PortfolioPage - on mount - default active tab is Insights', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => usePortfolioStore.setState({ selectedAccountId: 'acc-1' }))
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [mockAccount] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue(
      buildHoldingsHookReturn({ data: mockHoldingsResponse }),
    )

    await renderPage()

    const insightsTab = screen.getByRole('tab', { name: 'Insights' })
    expect(insightsTab).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByTestId('insights-tab')).toBeInTheDocument()
  })

  it('PortfolioPage - clicking Holdings tab - renders PortfolioHoldingsTable', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => usePortfolioStore.setState({ selectedAccountId: 'acc-1' }))
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [mockAccount] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue(
      buildHoldingsHookReturn({ data: mockHoldingsResponse }),
    )

    await renderPage()

    const user = userEvent.setup()
    await user.click(screen.getByRole('tab', { name: /holdings/i }))

    expect(screen.getByTestId('holdings-table')).toBeInTheDocument()
    // Verify usePortfolioHoldings was not called extra times due to tab switch (it may render twice in React 19)
    expect(mockUsePortfolioHoldings.mock.calls.length).toBeLessThanOrEqual(2)
  })

  it('PortfolioPage - clicking Advanced Insights tab - renders AdvancedInsightsTab', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => usePortfolioStore.setState({ selectedAccountId: 'acc-1' }))
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [mockAccount] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue(
      buildHoldingsHookReturn({ data: mockHoldingsResponse }),
    )

    await renderPage()

    const user = userEvent.setup()
    await user.click(screen.getByRole('tab', { name: /advanced insights/i }))

    expect(screen.getByTestId('advanced-insights-tab')).toHaveTextContent('accountId=acc-1')
  })

  it('PortfolioPage - account selection change - passes new accountId to AdvancedInsightsTab', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => usePortfolioStore.setState({ selectedAccountId: 'acc-1' }))
    const secondAccount: AccountResponse = {
      ...mockAccount,
      id: 'acc-2',
      name: 'Second Account',
    }
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [mockAccount, secondAccount] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue(
      buildHoldingsHookReturn({ data: mockHoldingsResponse }),
    )

    await renderPage()

    const user = userEvent.setup()
    await user.click(screen.getByRole('tab', { name: /advanced insights/i }))
    fireEvent.change(screen.getByRole('combobox', { name: /select account/i }), {
      target: { value: 'acc-2' },
    })

    expect(screen.getByTestId('advanced-insights-tab')).toHaveTextContent('accountId=acc-2')
  })

  it('PortfolioPage - accounts loading - shows loading text', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockUseActiveAccounts.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue(buildHoldingsHookReturn())

    await renderPage()

    expect(screen.getByText(/loading accounts/i)).toBeInTheDocument()
  })

  it('PortfolioPage - holdings loading - InsightsTab receives isLoading true', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => usePortfolioStore.setState({ selectedAccountId: 'acc-1' }))
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [mockAccount] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue(buildHoldingsHookReturn({ isLoading: true }))

    await renderPage()

    expect(screen.getByTestId('insights-loading')).toBeInTheDocument()
  })

  it('PortfolioPage - holdings error - InsightsTab receives isError true', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => usePortfolioStore.setState({ selectedAccountId: 'acc-1' }))
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [mockAccount] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue(
      buildHoldingsHookReturn({
        isError: true,
        error: Object.assign(new Error('err'), { isAxiosError: true, response: { status: 502 } }),
      }),
    )

    await renderPage()

    expect(screen.getByTestId('insights-error')).toBeInTheDocument()
  })

  it('PortfolioPage - 502 price error on Holdings tab - renders price unavailable message', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => usePortfolioStore.setState({ selectedAccountId: 'acc-1' }))
    const priceError = Object.assign(new Error('Bad Gateway'), {
      isAxiosError: true,
      response: {
        status: 502,
        data: { error: 'Price data unavailable for tickers' },
      },
    })
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [mockAccount] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue(
      buildHoldingsHookReturn({ isError: true, error: priceError }),
    )

    await renderPage()

    const user1 = userEvent.setup()
    await user1.click(screen.getByRole('tab', { name: /holdings/i }))

    expect(
      screen.getByText('Could not load portfolio. Price data unavailable.'),
    ).toBeInTheDocument()
  })

  it('PortfolioPage - 502 balance error on Holdings tab - renders balance unavailable message', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => usePortfolioStore.setState({ selectedAccountId: 'acc-1' }))
    const balanceError = Object.assign(new Error('Bad Gateway'), {
      isAxiosError: true,
      response: {
        status: 502,
        data: { error: 'Balance data unavailable' },
      },
    })
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [mockAccount] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue(
      buildHoldingsHookReturn({ isError: true, error: balanceError }),
    )

    await renderPage()

    const user2 = userEvent.setup()
    await user2.click(screen.getByRole('tab', { name: /holdings/i }))

    expect(
      screen.getByText('Could not load portfolio. Balance data unavailable.'),
    ).toBeInTheDocument()
  })

  it('PortfolioPage - no accounts - renders empty-state message, no table', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue(buildHoldingsHookReturn())

    await renderPage()

    expect(screen.getByText('No accounts available. Open an account first.')).toBeInTheDocument()
    expect(screen.queryByTestId('holdings-table')).not.toBeInTheDocument()
  })

  it('PortfolioPage - accounts fetch error - shows could not load accounts', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockUseActiveAccounts.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue(buildHoldingsHookReturn())

    await renderPage()

    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText('Could not load accounts.')).toBeInTheDocument()
  })

  it('PortfolioPage - 401 holdings error - redirects to /login', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => usePortfolioStore.setState({ selectedAccountId: 'acc-1' }))
    const authError = Object.assign(new Error('Unauthorized'), {
      isAxiosError: true,
      response: { status: 401 },
    })
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [mockAccount] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue(
      buildHoldingsHookReturn({ isError: true, error: authError }),
    )

    await renderPage()

    expect(await screen.findByText('Login Page')).toBeInTheDocument()
  })

  it('PortfolioPage - account selection change - calls setSelectedAccountId', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => usePortfolioStore.setState({ selectedAccountId: 'acc-1' }))
    const secondAccount: AccountResponse = {
      ...mockAccount,
      id: 'acc-2',
      name: 'Second Account',
    }
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [mockAccount, secondAccount] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue(
      buildHoldingsHookReturn({ data: mockHoldingsResponse }),
    )

    await renderPage()

    const select = screen.getByRole('combobox', { name: /select account/i })
    fireEvent.change(select, { target: { value: 'acc-2' } })

    expect(usePortfolioStore.getState().selectedAccountId).toBe('acc-2')
  })

  it('PortfolioPage - default account selection - selects first account when none stored', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [mockAccount] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue(buildHoldingsHookReturn())

    await renderPage()

    expect(usePortfolioStore.getState().selectedAccountId).toBe('acc-1')
  })

  it('PortfolioPage - triggering onSell from table - calls openSellPanel with correct args', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => usePortfolioStore.setState({ selectedAccountId: 'acc-1' }))
    const openSellPanel = vi.fn()
    mockUseSellPanel.mockReturnValue(buildSellPanelHook({ openSellPanel }))
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [mockAccount] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue(
      buildHoldingsHookReturn({ data: mockHoldingsResponse }),
    )

    await renderPage()

    // Switch to Holdings tab first to see the table
    const user3 = userEvent.setup()
    await user3.click(screen.getByRole('tab', { name: /holdings/i }))

    capturedOnSell?.('AAPL', 10)

    expect(openSellPanel).toHaveBeenCalledWith('AAPL', 10)
  })

  it('PortfolioPage - isOpen true - SellPanel is rendered with correct props', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => usePortfolioStore.setState({ selectedAccountId: 'acc-1' }))
    mockUseSellPanel.mockReturnValue(
      buildSellPanelHook({ isOpen: true, ticker: 'AAPL', maxQuantity: 10 }),
    )
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [mockAccount] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue(
      buildHoldingsHookReturn({ data: mockHoldingsResponse }),
    )

    await renderPage()

    const sellPanel = screen.getByTestId('sell-panel')
    expect(sellPanel).toBeInTheDocument()
    expect(sellPanel).toHaveTextContent('SellPanel: AAPL qty=10')
  })

  it('PortfolioPage - isOpen false - SellPanel is not rendered', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => usePortfolioStore.setState({ selectedAccountId: 'acc-1' }))
    mockUseSellPanel.mockReturnValue(buildSellPanelHook({ isOpen: false }))
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [mockAccount] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue(
      buildHoldingsHookReturn({ data: mockHoldingsResponse }),
    )

    await renderPage()

    expect(screen.queryByTestId('sell-panel')).not.toBeInTheDocument()
  })
})
