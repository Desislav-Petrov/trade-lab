import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { createElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act } from 'react'
import { PortfolioPage } from './PortfolioPage'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import { usePortfolioStore } from '../hooks/usePortfolioStore'
import type { UserProfile } from '../../user/types/user'
import type { AccountResponse } from '../../ledger/types/account'
import type { PortfolioHoldingsResponse } from '../types/portfolio.types'

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
        createElement('option', { key: a.id, value: a.id }, `${a.name} (${a.currency})`)
      )
    )
  },
}))

vi.mock('../components/PortfolioHoldingsTable', () => ({
  PortfolioHoldingsTable: ({
    holdings,
    cash,
  }: PortfolioHoldingsResponse) =>
    createElement(
      'div',
      { 'data-testid': 'holdings-table' },
      `Holdings: ${holdings.length}, Cash: ${cash.balance}`
    ),
}))

import { useActiveAccounts } from '../../ledger/hooks/useLedger'
import { usePortfolioHoldings } from '../hooks/usePortfolioHoldings'

const mockUseActiveAccounts = vi.mocked(useActiveAccounts)
const mockUsePortfolioHoldings = vi.mocked(usePortfolioHoldings)

const mockProfile: UserProfile = {
  userId: 'u1',
  firstName: 'Jane',
  lastName: 'Doe',
  address: '123 Main St',
  email: 'jane@example.com',
  status: 'active',
  createdAt: '2026-01-01T00:00:00Z',
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

function renderPage(initialPath = '/portfolio') {
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
          createElement(Route, { path: '/portfolio', element: createElement(PortfolioPage) }),
          createElement(Route, { path: '/login', element: createElement('div', null, 'Login Page') })
        )
      )
    )
  )
}

describe('PortfolioPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    act(() => {
      useSessionStore.getState().clearSession()
      usePortfolioStore.setState({ selectedAccountId: null })
    })
  })

  it('PortfolioPage - no session - redirects to /login', () => {
    mockUseActiveAccounts.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
      error: null,
    } as unknown as ReturnType<typeof usePortfolioHoldings>)

    renderPage()

    expect(screen.getByText('Login Page')).toBeInTheDocument()
  })

  it('PortfolioPage - session exists - renders heading', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
      error: null,
    } as unknown as ReturnType<typeof usePortfolioHoldings>)

    renderPage()

    expect(screen.getByRole('heading', { name: /portfolio/i })).toBeInTheDocument()
  })

  it('PortfolioPage - accounts loading - shows loading text', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockUseActiveAccounts.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
      error: null,
    } as unknown as ReturnType<typeof usePortfolioHoldings>)

    renderPage()

    expect(screen.getByText(/loading accounts/i)).toBeInTheDocument()
  })

  it('PortfolioPage - holdings loading - renders loading state', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => usePortfolioStore.setState({ selectedAccountId: 'acc-1' }))
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [mockAccount] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      error: null,
    } as unknown as ReturnType<typeof usePortfolioHoldings>)

    renderPage()

    expect(screen.getByText(/loading\.\.\./i)).toBeInTheDocument()
  })

  it('PortfolioPage - holdings loaded - renders PortfolioHoldingsTable', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => usePortfolioStore.setState({ selectedAccountId: 'acc-1' }))
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [mockAccount] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue({
      data: mockHoldingsResponse,
      isLoading: false,
      isError: false,
      error: null,
    } as unknown as ReturnType<typeof usePortfolioHoldings>)

    renderPage()

    expect(screen.getByTestId('holdings-table')).toBeInTheDocument()
  })

  it('PortfolioPage - 502 price error - renders price unavailable message', () => {
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
    mockUsePortfolioHoldings.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: priceError,
    } as unknown as ReturnType<typeof usePortfolioHoldings>)

    renderPage()

    expect(
      screen.getByText('Could not load portfolio. Price data unavailable.')
    ).toBeInTheDocument()
  })

  it('PortfolioPage - 502 balance error - renders balance unavailable message', () => {
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
    mockUsePortfolioHoldings.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: balanceError,
    } as unknown as ReturnType<typeof usePortfolioHoldings>)

    renderPage()

    expect(
      screen.getByText('Could not load portfolio. Balance data unavailable.')
    ).toBeInTheDocument()
  })

  it('PortfolioPage - no accounts - renders empty-state message, no table', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
      error: null,
    } as unknown as ReturnType<typeof usePortfolioHoldings>)

    renderPage()

    expect(
      screen.getByText('No accounts available. Open an account first.')
    ).toBeInTheDocument()
    expect(screen.queryByTestId('holdings-table')).not.toBeInTheDocument()
  })

  it('PortfolioPage - accounts fetch error - shows could not load accounts', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockUseActiveAccounts.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
      error: null,
    } as unknown as ReturnType<typeof usePortfolioHoldings>)

    renderPage()

    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText('Could not load accounts.')).toBeInTheDocument()
  })

  it('PortfolioPage - 401 holdings error - redirects to /login', () => {
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
    mockUsePortfolioHoldings.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: authError,
    } as unknown as ReturnType<typeof usePortfolioHoldings>)

    renderPage()

    expect(screen.getByText('Login Page')).toBeInTheDocument()
  })

  it('PortfolioPage - account selection change - calls setSelectedAccountId', () => {
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
    mockUsePortfolioHoldings.mockReturnValue({
      data: mockHoldingsResponse,
      isLoading: false,
      isError: false,
      error: null,
    } as unknown as ReturnType<typeof usePortfolioHoldings>)

    renderPage()

    const select = screen.getByRole('combobox', { name: /select account/i })
    fireEvent.change(select, { target: { value: 'acc-2' } })

    expect(usePortfolioStore.getState().selectedAccountId).toBe('acc-2')
  })

  it('PortfolioPage - default account selection - selects first account when none stored', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    // selectedAccountId is null, accounts loads → useEffect should select first
    mockUseActiveAccounts.mockReturnValue({
      data: { accounts: [mockAccount] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useActiveAccounts>)
    mockUsePortfolioHoldings.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
      error: null,
    } as unknown as ReturnType<typeof usePortfolioHoldings>)

    renderPage()

    expect(usePortfolioStore.getState().selectedAccountId).toBe('acc-1')
  })
})
