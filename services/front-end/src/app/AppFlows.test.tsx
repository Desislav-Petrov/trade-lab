import { beforeEach, describe, expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act } from 'react'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { RootLayout } from './layouts/RootLayout'
import { LoginPage } from '../domains/user/pages/LoginPage'
import { RegistrationPage } from '../domains/user/pages/RegistrationPage'
import { ProfilePage } from '../domains/user/pages/ProfilePage'
import { AccountsPage } from '../domains/ledger/pages/AccountsPage'
import { StockTradingPage } from '../domains/stocktrading/pages/StockTradingPage'
import { useSessionStore } from '../domains/user/hooks/useSessionStore'
import type { UserProfile } from '../domains/user/types/user'
import type { AccountResponse } from '../domains/ledger/types/account'
import type { SubscriptionResponse } from '../domains/marketdata/types/subscription'
import type {
  FeedMessage,
  MarketDataUpdate,
} from '../domains/marketdata/api/marketDataFeedApi'

const mockCreateUser = vi.fn()
const mockGetActiveUserEmails = vi.fn()
const mockLoginUser = vi.fn()
const mockFetchUserById = vi.fn()
const mockFetchAccounts = vi.fn()
const mockCreateAccount = vi.fn()
const mockTopUpAccount = vi.fn()
const mockFetchSupportedTickers = vi.fn()
const mockFetchSubscriptions = vi.fn()
const mockBulkAddSubscriptions = vi.fn()
const mockBulkRemoveSubscriptions = vi.fn()
const mockConnectMarketDataFeed = vi.fn()

let emitFeedMessage: ((message: FeedMessage) => void) | null = null

vi.mock('../domains/user/api/userApi', () => ({
  createUser: mockCreateUser,
  getActiveUserEmails: mockGetActiveUserEmails,
  loginUser: mockLoginUser,
  fetchUserById: mockFetchUserById,
}))

vi.mock('../domains/ledger/api/accountApi', () => ({
  fetchAccounts: mockFetchAccounts,
  createAccount: mockCreateAccount,
  topUpAccount: mockTopUpAccount,
}))

vi.mock('../domains/marketdata/api/subscriptionApi', () => ({
  fetchSupportedTickers: mockFetchSupportedTickers,
  fetchSubscriptions: mockFetchSubscriptions,
  bulkAddSubscriptions: mockBulkAddSubscriptions,
  bulkRemoveSubscriptions: mockBulkRemoveSubscriptions,
}))

vi.mock('../domains/marketdata/api/marketDataFeedApi', () => ({
  connectMarketDataFeed: mockConnectMarketDataFeed,
}))

const profile: UserProfile = {
  userId: 'user-1',
  firstName: 'Jane',
  lastName: 'Doe',
  address: '123 Main St',
  email: 'jane.doe@example.com',
  status: 'active',
  createdAt: '2026-06-20T12:00:00Z',
}

const openedAccount: AccountResponse = {
  id: 'account-1',
  name: 'My Trading Account',
  currency: 'USD',
  balance: 0,
  status: 'ACTIVE',
  createdAt: '2026-06-24T12:00:00Z',
}

const toppedUpAccount: AccountResponse = {
  ...openedAccount,
  balance: 1500,
}

const aaplSubscription: SubscriptionResponse = {
  ticker: 'AAPL',
  companyName: 'Apple Inc.',
}

const msftSubscription: SubscriptionResponse = {
  ticker: 'MSFT',
  companyName: 'Microsoft Corporation',
}

const googSubscription: SubscriptionResponse = {
  ticker: 'GOOG',
  companyName: 'Alphabet Inc.',
}

const aaplFeedRow: MarketDataUpdate = {
  ticker: 'AAPL',
  companyName: 'Apple Inc.',
  currentPrice: 150,
  open: 148,
  dayLow: 147.5,
  fiftyTwoWeekHigh: 200,
}

const msftFeedRow: MarketDataUpdate = {
  ticker: 'MSFT',
  companyName: 'Microsoft Corporation',
  currentPrice: 320,
  open: 315,
  dayLow: 312,
  fiftyTwoWeekHigh: 360,
}

function renderApp(initialPath: string) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route element={<RootLayout />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegistrationPage />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/accounts" element={<AccountsPage />} />
            <Route path="/trade" element={<StockTradingPage />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function signInViaUi(user: ReturnType<typeof userEvent.setup>) {
  renderApp('/login')

  await user.selectOptions(await screen.findByLabelText(/select account/i), profile.email)
  await user.click(screen.getByRole('button', { name: /log in/i }))

  await screen.findByRole('heading', { name: /jane doe/i })
}

beforeEach(() => {
  vi.clearAllMocks()
  emitFeedMessage = null
  act(() => {
    useSessionStore.getState().clearSession()
  })

  mockCreateUser.mockResolvedValue({ userId: profile.userId })
  mockGetActiveUserEmails.mockResolvedValue({ emails: [profile.email] })
  mockLoginUser.mockResolvedValue({ userId: profile.userId, email: profile.email })
  mockFetchUserById.mockResolvedValue(profile)
  mockFetchSupportedTickers.mockResolvedValue([aaplSubscription, msftSubscription, googSubscription])
  mockConnectMarketDataFeed.mockImplementation((userId: string, onMessage: (message: FeedMessage) => void) => {
    emitFeedMessage = onMessage
    onMessage({ type: 'SNAPSHOT', data: [aaplFeedRow] })
    return () => {
      if (emitFeedMessage === onMessage) {
        emitFeedMessage = null
      }
    }
  })
})

describe('App flows', () => {
  it('App flows - registration to login - establishes a session and shows the shell', async () => {
    const user = userEvent.setup()
    renderApp('/register')

    await user.type(screen.getByLabelText(/first name/i), profile.firstName)
    await user.type(screen.getByLabelText(/last name/i), profile.lastName)
    await user.type(screen.getByLabelText(/address/i), profile.address)
    await user.type(screen.getByLabelText(/email/i), profile.email)
    await user.click(screen.getByRole('button', { name: /submit/i }))

    await screen.findByRole('heading', { name: /log in/i })
    expect(screen.getByRole('status')).toHaveTextContent(/account created\. please log in\./i)

    await user.selectOptions(await screen.findByLabelText(/select account/i), profile.email)
    await user.click(screen.getByRole('button', { name: /log in/i }))

    await screen.findByRole('heading', { name: /jane doe/i })
    expect(screen.getByRole('banner')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: /main navigation/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /logout/i })).toBeInTheDocument()
    expect(mockCreateUser).toHaveBeenCalledWith({
      firstName: profile.firstName,
      lastName: profile.lastName,
      address: profile.address,
      email: profile.email,
    })
    expect(mockLoginUser).toHaveBeenCalledWith({ email: profile.email })
    expect(mockFetchUserById).toHaveBeenCalledWith(profile.userId)

    await user.click(screen.getByRole('button', { name: /logout/i }))

    await screen.findByRole('heading', { name: /log in/i })
    expect(screen.queryByRole('navigation', { name: /main navigation/i })).not.toBeInTheDocument()
  })

  it('App flows - accounts journey - opens an account and tops it up', async () => {
    const user = userEvent.setup()
    mockFetchAccounts
      .mockResolvedValueOnce({ accounts: [] })
      .mockResolvedValueOnce({ accounts: [openedAccount] })
      .mockResolvedValueOnce({ accounts: [toppedUpAccount] })
    mockCreateAccount.mockResolvedValue(openedAccount)
    mockTopUpAccount.mockResolvedValue({
      accountId: openedAccount.id,
      newBalance: toppedUpAccount.balance,
      currency: openedAccount.currency,
      ledgerEntryId: 'ledger-1',
      timestamp: '2026-06-24T12:30:00Z',
    })

    await signInViaUi(user)
    await user.click(screen.getByRole('link', { name: /accounts/i }))

    await screen.findByRole('heading', { name: /accounts/i })
    expect(screen.getByText(/no accounts yet\. open one to get started\./i)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /open new account/i }))
    await user.selectOptions(screen.getByLabelText(/base currency/i), 'USD')
    await user.type(screen.getByLabelText(/account name/i), openedAccount.name)
    await user.click(screen.getByRole('button', { name: /open account/i }))

    await screen.findByText(openedAccount.name)
    expect(mockCreateAccount).toHaveBeenCalledWith({
      userId: profile.userId,
      currency: 'USD',
      name: openedAccount.name,
    })
    expect(screen.getByText('0.00')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /top up/i }))
    await user.type(screen.getByLabelText(/amount/i), '1500')
    await user.click(screen.getByRole('button', { name: /confirm/i }))

    await screen.findByText(/top up successful/i)
    expect(mockTopUpAccount).toHaveBeenCalledWith(openedAccount.id, {
      userId: profile.userId,
      amount: 1500,
    })
    await screen.findByText('1500.00')
  })

  it('App flows - stock trading journey - adds, renders, and removes subscriptions', async () => {
    const user = userEvent.setup()
    mockFetchSubscriptions
      .mockResolvedValueOnce([aaplSubscription])
      .mockResolvedValueOnce([aaplSubscription, msftSubscription])
      .mockResolvedValueOnce([msftSubscription])
    mockBulkAddSubscriptions.mockResolvedValue({ subscriptions: [aaplSubscription, msftSubscription] })
    mockBulkRemoveSubscriptions.mockResolvedValue(undefined)

    await signInViaUi(user)
    await user.click(screen.getByRole('link', { name: /stock trading/i }))

    await screen.findByRole('heading', { name: /stock trading/i })
    await screen.findByText('AAPL')
    expect(screen.getByRole('button', { name: /remove selected \(0\)/i })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: /add tickers/i }))
    await user.click(await screen.findByLabelText(/msft/i))
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    await waitFor(() => expect(mockBulkAddSubscriptions).toHaveBeenCalledWith(
      { userId: profile.userId, tickers: ['MSFT'] },
      expect.any(Object),
    ))

    await waitFor(() => expect(screen.queryByRole('button', { name: /adding/i })).not.toBeInTheDocument())
    emitFeedMessage?.({ type: 'TICK', data: msftFeedRow })
    await screen.findByText('MSFT')
    expect(screen.getByRole('button', { name: /remove selected \(0\)/i })).toBeDisabled()

    await user.click(screen.getByLabelText(/aapl/i))
    await user.click(screen.getByRole('button', { name: /remove selected \(1\)/i }))

    await waitFor(() => expect(mockBulkRemoveSubscriptions).toHaveBeenCalledWith(
      { userId: profile.userId, tickers: ['AAPL'] },
      expect.any(Object),
    ))

    await waitFor(() => expect(screen.queryByText('AAPL')).not.toBeInTheDocument())
    await screen.findByText('MSFT')
  })
})
