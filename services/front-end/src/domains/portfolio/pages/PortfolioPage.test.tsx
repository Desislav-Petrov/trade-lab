import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useNavigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { PortfolioPage } from './PortfolioPage'
import { useActiveAccounts } from '../../ledger/hooks/useLedger'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import { usePortfolioStore } from '../hooks/usePortfolioStore'
import { usePortfolioHoldings } from '../hooks/usePortfolioHoldings'

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: vi.fn(),
  }
})

vi.mock('../../ledger/hooks/useLedger')
vi.mock('../../user/hooks/useSessionStore')
vi.mock('../hooks/usePortfolioStore')
vi.mock('../hooks/usePortfolioHoldings')

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    )
  }
}

describe('PortfolioPage', () => {
  const mockNavigate = vi.fn()
  const mockSetSelectedAccountId = vi.fn()

  const mockAccount1 = {
    id: 'acc-1',
    name: 'Trading Account',
    currency: 'USD' as const,
    status: 'ACTIVE' as const,
    balance: 10000,
    userId: 'user-1',
    createdAt: '2026-01-01T00:00:00Z',
  }

  const mockAccount2 = {
    id: 'acc-2',
    name: 'Investment Account',
    currency: 'EUR' as const,
    status: 'ACTIVE' as const,
    balance: 5000,
    userId: 'user-1',
    createdAt: '2026-01-02T00:00:00Z',
  }

  const mockHoldingsData = {
    holdings: [
      {
        ticker: 'AAPL',
        quantity: 10,
        currentPrice: 150,
        currentValue: 1500,
        minPrice: 140,
        maxPrice: 160,
        avgPrice: 145,
        portfolioPercent: 50,
        unrealisedPnL: 50,
      },
    ],
    cash: {
      balance: 1500,
      currency: 'USD' as const,
      portfolioPercent: 50,
    },
  }

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useNavigate).mockReturnValue(mockNavigate)
    vi.mocked(useSessionStore).mockReturnValue({ userId: 'user-1', username: 'testuser' })
    vi.mocked(usePortfolioStore).mockImplementation((selector: any) => {
      const state = {
        selectedAccountId: null,
        setSelectedAccountId: mockSetSelectedAccountId,
      }
      return selector(state)
    })
  })

  it('PortfolioPage - loading accounts - renders loading state', () => {
    vi.mocked(useActiveAccounts).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
    } as any)

    vi.mocked(usePortfolioHoldings).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
      error: null,
    } as any)

    render(<PortfolioPage />, { wrapper: createWrapper() })

    expect(screen.getByText('Portfolio')).toBeInTheDocument()
    expect(screen.getByText('Loading accounts...')).toBeInTheDocument()
  })

  it('PortfolioPage - account fetch error - renders error message', () => {
    vi.mocked(useActiveAccounts).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
    } as any)

    vi.mocked(usePortfolioHoldings).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
      error: null,
    } as any)

    render(<PortfolioPage />, { wrapper: createWrapper() })

    expect(screen.getByText('Portfolio')).toBeInTheDocument()
    expect(screen.getByText('Could not load accounts.')).toBeInTheDocument()
  })

  it('PortfolioPage - no accounts - renders empty state', () => {
    vi.mocked(useActiveAccounts).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as any)

    vi.mocked(usePortfolioHoldings).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
      error: null,
    } as any)

    render(<PortfolioPage />, { wrapper: createWrapper() })

    expect(screen.getByText('Portfolio')).toBeInTheDocument()
    expect(screen.getByText('No accounts available. Open an account first.')).toBeInTheDocument()
  })

  it('PortfolioPage - loading holdings - renders loading state', () => {
    vi.mocked(useActiveAccounts).mockReturnValue({
      data: [mockAccount1],
      isLoading: false,
      isError: false,
    } as any)

    vi.mocked(usePortfolioHoldings).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      error: null,
    } as any)

    render(<PortfolioPage />, { wrapper: createWrapper() })

    expect(screen.getByText('Portfolio')).toBeInTheDocument()
    expect(screen.getByText('Loading portfolio...')).toBeInTheDocument()
  })

  it('PortfolioPage - successful holdings fetch - renders PortfolioHoldingsTable', () => {
    vi.mocked(useActiveAccounts).mockReturnValue({
      data: [mockAccount1],
      isLoading: false,
      isError: false,
    } as any)

    vi.mocked(usePortfolioHoldings).mockReturnValue({
      data: mockHoldingsData,
      isLoading: false,
      isError: false,
      error: null,
    } as any)

    render(<PortfolioPage />, { wrapper: createWrapper() })

    expect(screen.getByText('Portfolio')).toBeInTheDocument()
    expect(screen.getByText('AAPL')).toBeInTheDocument()
  })

  it('PortfolioPage - price unavailable error - renders price unavailable message', () => {
    vi.mocked(useActiveAccounts).mockReturnValue({
      data: [mockAccount1],
      isLoading: false,
      isError: false,
    } as any)

    vi.mocked(usePortfolioHoldings).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: {
        response: {
          status: 502,
          data: { error: 'Price data unavailable' },
        },
      },
    } as any)

    render(<PortfolioPage />, { wrapper: createWrapper() })

    expect(screen.getByText('Portfolio')).toBeInTheDocument()
    expect(screen.getByText('Could not load portfolio. Price data unavailable.')).toBeInTheDocument()
  })

  it('PortfolioPage - balance unavailable error - renders balance unavailable message', () => {
    vi.mocked(useActiveAccounts).mockReturnValue({
      data: [mockAccount1],
      isLoading: false,
      isError: false,
    } as any)

    vi.mocked(usePortfolioHoldings).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: {
        response: {
          status: 502,
          data: { error: 'Balance data unavailable' },
        },
      },
    } as any)

    render(<PortfolioPage />, { wrapper: createWrapper() })

    expect(screen.getByText('Portfolio')).toBeInTheDocument()
    expect(screen.getByText('Could not load portfolio. Balance data unavailable.')).toBeInTheDocument()
  })

  it('PortfolioPage - account not found error - renders account not found message', () => {
    vi.mocked(useActiveAccounts).mockReturnValue({
      data: [mockAccount1],
      isLoading: false,
      isError: false,
    } as any)

    vi.mocked(usePortfolioHoldings).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: {
        response: {
          status: 404,
          data: { error: 'Account not found' },
        },
      },
    } as any)

    render(<PortfolioPage />, { wrapper: createWrapper() })

    expect(screen.getByText('Portfolio')).toBeInTheDocument()
    expect(screen.getByText('Account not found.')).toBeInTheDocument()
  })

  it('PortfolioPage - unauthenticated error - redirects to login', () => {
    vi.mocked(useActiveAccounts).mockReturnValue({
      data: [mockAccount1],
      isLoading: false,
      isError: false,
    } as any)

    vi.mocked(usePortfolioHoldings).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: {
        response: {
          status: 401,
          data: { error: 'Unauthorized' },
        },
      },
    } as any)

    render(<PortfolioPage />, { wrapper: createWrapper() })

    waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/login')
    })
  })

  it('PortfolioPage - account selection change - calls setSelectedAccountId', async () => {
    const user = userEvent.setup()

    vi.mocked(useActiveAccounts).mockReturnValue({
      data: [mockAccount1, mockAccount2],
      isLoading: false,
      isError: false,
    } as any)

    vi.mocked(usePortfolioHoldings).mockReturnValue({
      data: mockHoldingsData,
      isLoading: false,
      isError: false,
      error: null,
    } as any)

    render(<PortfolioPage />, { wrapper: createWrapper() })

    const selector = screen.getByLabelText('Select Account')
    await user.selectOptions(selector, 'acc-2')

    expect(mockSetSelectedAccountId).toHaveBeenCalledWith('acc-2')
  })

  it('PortfolioPage - default account selection on mount - sets first account when no prior selection', () => {
    vi.mocked(useActiveAccounts).mockReturnValue({
      data: [mockAccount1, mockAccount2],
      isLoading: false,
      isError: false,
    } as any)

    vi.mocked(usePortfolioHoldings).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      error: null,
    } as any)

    render(<PortfolioPage />, { wrapper: createWrapper() })

    waitFor(() => {
      expect(mockSetSelectedAccountId).toHaveBeenCalledWith('acc-1')
    })
  })

  it('PortfolioPage - default account selection on mount - preserves existing selection', () => {
    vi.mocked(usePortfolioStore).mockImplementation((selector: any) => {
      const state = {
        selectedAccountId: 'acc-2',
        setSelectedAccountId: mockSetSelectedAccountId,
      }
      return selector(state)
    })

    vi.mocked(useActiveAccounts).mockReturnValue({
      data: [mockAccount1, mockAccount2],
      isLoading: false,
      isError: false,
    } as any)

    vi.mocked(usePortfolioHoldings).mockReturnValue({
      data: mockHoldingsData,
      isLoading: false,
      isError: false,
      error: null,
    } as any)

    render(<PortfolioPage />, { wrapper: createWrapper() })

    expect(mockSetSelectedAccountId).not.toHaveBeenCalled()
  })
})
