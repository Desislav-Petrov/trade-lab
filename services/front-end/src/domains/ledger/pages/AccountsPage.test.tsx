import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { createElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AccountsPage } from './AccountsPage'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import type { UserProfile } from '../../user/types/user'
import type { AccountResponse } from '../types/account'
import { act } from 'react'

vi.mock('../hooks/useLedger', () => ({
  useAccounts: vi.fn(),
  useOpenAccount: vi.fn(),
  useTopUpAccount: vi.fn(),
}))

vi.mock('../components/AccountList', () => ({
  AccountList: ({
    accounts,
    onTopUp,
    onTransactions,
  }: {
    accounts: AccountResponse[]
    onTopUp: (account: AccountResponse) => void
    onTransactions: (account: AccountResponse) => void
  }) =>
    createElement(
      'div',
      { 'data-testid': 'account-list' },
      accounts.length === 0
        ? 'No accounts yet. Open one to get started.'
        : accounts.map((a) =>
            createElement(
              'div',
              { key: a.id },
              a.name,
              createElement('button', { onClick: () => onTopUp(a) }, 'Top Up'),
              createElement('button', { onClick: () => onTransactions(a) }, 'Transactions'),
            ),
          ),
    ),
}))

vi.mock('../components/OpenAccountForm', () => ({
  OpenAccountForm: ({
    onSubmit,
    onCancel,
    isLoading,
    error,
  }: {
    onSubmit: (currency: 'USD', name?: string) => void
    onCancel: () => void
    isLoading: boolean
    error?: string
  }) =>
    createElement(
      'div',
      { 'data-testid': 'open-account-form' },
      error ? createElement('p', { role: 'alert' }, error) : null,
      createElement('button', { onClick: () => onSubmit('USD', 'Test Account') }, 'Submit Form'),
      createElement('button', { onClick: onCancel }, 'Cancel Form'),
      isLoading ? createElement('span', null, 'loading') : null,
    ),
}))

vi.mock('../components/TopUpModal', () => ({
  TopUpModal: ({
    account,
    onConfirm,
    onClose,
    isLoading,
    isSuccess,
    error,
  }: {
    account: AccountResponse
    onConfirm: (amount: number) => void
    onClose: () => void
    isLoading: boolean
    isSuccess: boolean
    error?: string
  }) => {
    if (isSuccess) {
      return createElement('div', { 'data-testid': 'top-up-modal' }, 'Top up successful')
    }
    return createElement(
      'div',
      { 'data-testid': 'top-up-modal' },
      createElement('p', null, `Top up: ${account.name}`),
      createElement('label', { htmlFor: 'amount-input' }, 'Amount'),
      createElement('input', { id: 'amount-input', 'data-testid': 'amount-input', type: 'number' }),
      error ? createElement('p', { role: 'alert' }, error) : null,
      createElement('button', { onClick: () => onConfirm(500) }, 'Confirm Top Up'),
      createElement('button', { onClick: onClose }, 'Cancel'),
      isLoading ? createElement('span', null, 'loading') : null,
    )
  },
}))

import { useAccounts, useOpenAccount, useTopUpAccount } from '../hooks/useLedger'
const mockUseAccounts = vi.mocked(useAccounts)
const mockUseOpenAccount = vi.mocked(useOpenAccount)
const mockUseTopUpAccount = vi.mocked(useTopUpAccount)

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

function renderPage(initialPath = '/accounts') {
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
          createElement(Route, { path: '/accounts', element: createElement(AccountsPage) }),
          createElement(Route, {
            path: '/login',
            element: createElement('div', null, 'Login Page'),
          }),
          createElement(Route, {
            path: '/accounts/:accountId/transactions',
            element: createElement('div', null, 'Transaction List Page'),
          }),
        ),
      ),
    ),
  )
}

function setupMocks(
  overrides: Partial<{
    openAccountMutate: ReturnType<typeof vi.fn>
    openAccountIsPending: boolean
    topUpMutate: ReturnType<typeof vi.fn>
    topUpIsPending: boolean
    topUpIsSuccess: boolean
    topUpReset: ReturnType<typeof vi.fn>
    accounts: AccountResponse[]
  }> = {},
) {
  mockUseAccounts.mockReturnValue({
    data: { accounts: overrides.accounts ?? [] },
    isLoading: false,
  } as unknown as ReturnType<typeof useAccounts>)

  mockUseOpenAccount.mockReturnValue({
    mutate: overrides.openAccountMutate ?? vi.fn(),
    isPending: overrides.openAccountIsPending ?? false,
  } as unknown as ReturnType<typeof useOpenAccount>)

  mockUseTopUpAccount.mockReturnValue({
    mutate: overrides.topUpMutate ?? vi.fn(),
    isPending: overrides.topUpIsPending ?? false,
    isSuccess: overrides.topUpIsSuccess ?? false,
    reset: overrides.topUpReset ?? vi.fn(),
  } as unknown as ReturnType<typeof useTopUpAccount>)
}

describe('AccountsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    act(() => useSessionStore.getState().clearSession())
  })

  it('AccountsPage - no session - redirects to /login', () => {
    setupMocks()
    renderPage()
    expect(screen.getByText('Login Page')).toBeInTheDocument()
  })

  it('AccountsPage - session exists - renders heading', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    setupMocks()
    renderPage()
    expect(screen.getByRole('heading', { name: /accounts/i })).toBeInTheDocument()
  })

  it('AccountsPage - loading accounts - shows loading text', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockUseAccounts.mockReturnValue({
      data: undefined,
      isLoading: true,
    } as unknown as ReturnType<typeof useAccounts>)
    mockUseOpenAccount.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useOpenAccount>)
    mockUseTopUpAccount.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isSuccess: false,
      reset: vi.fn(),
    } as unknown as ReturnType<typeof useTopUpAccount>)
    renderPage()
    expect(screen.getByText(/loading accounts/i)).toBeInTheDocument()
  })

  it('AccountsPage - accounts loaded - renders AccountList', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    setupMocks()
    renderPage()
    expect(screen.getByTestId('account-list')).toBeInTheDocument()
  })

  it('AccountsPage - open new account clicked - shows OpenAccountForm', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    setupMocks()
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /open new account/i }))

    expect(screen.getByTestId('open-account-form')).toBeInTheDocument()
  })

  it('AccountsPage - cancel form - hides OpenAccountForm', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    setupMocks()
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /open new account/i }))
    fireEvent.click(screen.getByRole('button', { name: /cancel form/i }))

    expect(screen.queryByTestId('open-account-form')).not.toBeInTheDocument()
  })

  it('AccountsPage - form submitted - calls mutate with userId and currency', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const mutate = vi.fn()
    setupMocks({ openAccountMutate: mutate })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /open new account/i }))
    fireEvent.click(screen.getByRole('button', { name: /submit form/i }))

    expect(mutate).toHaveBeenCalledWith(
      { userId: 'u1', currency: 'USD', name: 'Test Account' },
      expect.any(Object),
    )
  })

  it('AccountsPage - mutation success - hides form', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const mutate = vi.fn((_, { onSuccess }: { onSuccess: () => void }) => onSuccess())
    setupMocks({ openAccountMutate: mutate })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /open new account/i }))
    fireEvent.click(screen.getByRole('button', { name: /submit form/i }))

    expect(screen.queryByTestId('open-account-form')).not.toBeInTheDocument()
  })

  it('AccountsPage - mutation 401 error - redirects to /login', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const error = Object.assign(new Error('Unauthorized'), {
      isAxiosError: true,
      response: { status: 401 },
    })
    const mutate = vi.fn((_, { onError }: { onError: (e: unknown) => void }) => onError(error))
    setupMocks({ openAccountMutate: mutate })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /open new account/i }))
    fireEvent.click(screen.getByRole('button', { name: /submit form/i }))

    expect(screen.getByText('Login Page')).toBeInTheDocument()
  })

  it('AccountsPage - mutation 400 error - shows error in form', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const error = Object.assign(new Error('Bad Request'), {
      isAxiosError: true,
      response: { status: 400 },
    })
    const mutate = vi.fn((_, { onError }: { onError: (e: unknown) => void }) => onError(error))
    setupMocks({ openAccountMutate: mutate })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /open new account/i }))
    fireEvent.click(screen.getByRole('button', { name: /submit form/i }))

    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText(/invalid request/i)).toBeInTheDocument()
  })

  it('AccountsPage - mutation 403 error - shows authorisation error in form', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const error = Object.assign(new Error('Forbidden'), {
      isAxiosError: true,
      response: { status: 403 },
    })
    const mutate = vi.fn((_, { onError }: { onError: (e: unknown) => void }) => onError(error))
    setupMocks({ openAccountMutate: mutate })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /open new account/i }))
    fireEvent.click(screen.getByRole('button', { name: /submit form/i }))

    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText(/not authorised/i)).toBeInTheDocument()
  })

  // --- Top-up flow tests ---

  it('AccountsPage - clicking Top Up on an account - opens TopUpModal for that account', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    setupMocks({ accounts: [mockAccount] })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /top up/i }))

    expect(screen.getByTestId('top-up-modal')).toBeInTheDocument()
    expect(screen.getByText(/Amount/i)).toBeInTheDocument()
  })

  it('AccountsPage - successful top-up - isSuccess passed as true to modal', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    setupMocks({ accounts: [mockAccount], topUpIsSuccess: true })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /top up/i }))

    expect(screen.getByText('Top up successful')).toBeInTheDocument()
  })

  it('AccountsPage - closing modal - clears selectedAccount', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    setupMocks({ accounts: [mockAccount] })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /top up/i }))
    expect(screen.getByTestId('top-up-modal')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /cancel/i }))

    expect(screen.queryByTestId('top-up-modal')).not.toBeInTheDocument()
  })

  it('AccountsPage - top-up 401 error - navigates to /login', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const error = Object.assign(new Error('Unauthorized'), {
      isAxiosError: true,
      response: { status: 401 },
    })
    const topUpMutate = vi.fn((_, { onError }: { onError: (e: unknown) => void }) => onError(error))
    setupMocks({ accounts: [mockAccount], topUpMutate })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /top up/i }))
    fireEvent.click(screen.getByRole('button', { name: /confirm top up/i }))

    expect(screen.getByText('Login Page')).toBeInTheDocument()
  })

  it('AccountsPage - top-up 403 error - passes correct error string to modal', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const error = Object.assign(new Error('Forbidden'), {
      isAxiosError: true,
      response: { status: 403 },
    })
    const topUpMutate = vi.fn((_, { onError }: { onError: (e: unknown) => void }) => onError(error))
    setupMocks({ accounts: [mockAccount], topUpMutate })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /top up/i }))
    fireEvent.click(screen.getByRole('button', { name: /confirm top up/i }))

    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText('This account is not available for top-up.')).toBeInTheDocument()
  })

  it('AccountsPage - top-up 404 error - passes correct error string to modal', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const error = Object.assign(new Error('Not Found'), {
      isAxiosError: true,
      response: { status: 404 },
    })
    const topUpMutate = vi.fn((_, { onError }: { onError: (e: unknown) => void }) => onError(error))
    setupMocks({ accounts: [mockAccount], topUpMutate })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /top up/i }))
    fireEvent.click(screen.getByRole('button', { name: /confirm top up/i }))

    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText('Account not found.')).toBeInTheDocument()
  })

  // --- Transactions navigation tests ---

  it('AccountsPage - clicking Transactions on an account - navigates to transaction list page', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    setupMocks({ accounts: [mockAccount] })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /transactions/i }))

    expect(screen.getByText('Transaction List Page')).toBeInTheDocument()
  })
})
