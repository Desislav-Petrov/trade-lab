import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { createElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AccountsPage } from './AccountsPage'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import type { UserProfile } from '../../user/types/user'
import { act } from 'react'

vi.mock('../hooks/useLedger', () => ({
  useAccounts: vi.fn(),
  useOpenAccount: vi.fn(),
}))

vi.mock('../components/AccountList', () => ({
  AccountList: ({ accounts }: { accounts: { accountId: string; name: string }[] }) =>
    createElement('div', { 'data-testid': 'account-list' },
      accounts.length === 0
        ? 'No accounts yet. Open one to get started.'
        : accounts.map((a) => createElement('div', { key: a.accountId }, a.name)),
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
    createElement('div', { 'data-testid': 'open-account-form' },
      error ? createElement('p', { role: 'alert' }, error) : null,
      createElement('button', { onClick: () => onSubmit('USD', 'Test Account') }, 'Submit Form'),
      createElement('button', { onClick: onCancel }, 'Cancel Form'),
      isLoading ? createElement('span', null, 'loading') : null,
    ),
}))

import { useAccounts, useOpenAccount } from '../hooks/useLedger'
const mockUseAccounts = vi.mocked(useAccounts)
const mockUseOpenAccount = vi.mocked(useOpenAccount)

const mockProfile: UserProfile = {
  userId: 'u1',
  firstName: 'Jane',
  lastName: 'Doe',
  address: '123 Main St',
  email: 'jane@example.com',
  status: 'active',
  createdAt: '2026-01-01T00:00:00Z',
}

function renderPage(initialPath = '/accounts') {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return render(
    createElement(QueryClientProvider, { client: queryClient },
      createElement(MemoryRouter, { initialEntries: [initialPath] },
        createElement(Routes, null,
          createElement(Route, { path: '/accounts', element: createElement(AccountsPage) }),
          createElement(Route, { path: '/login', element: createElement('div', null, 'Login Page') }),
        ),
      ),
    ),
  )
}

function setupMocks(overrides: Partial<{ mutate: ReturnType<typeof vi.fn>; isPending: boolean }> = {}) {
  mockUseAccounts.mockReturnValue({
    data: { accounts: [] },
    isLoading: false,
  } as unknown as ReturnType<typeof useAccounts>)

  mockUseOpenAccount.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    ...overrides,
  } as unknown as ReturnType<typeof useOpenAccount>)
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
    setupMocks({ mutate })
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
    setupMocks({ mutate })
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
    setupMocks({ mutate })
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
    setupMocks({ mutate })
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
    setupMocks({ mutate })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /open new account/i }))
    fireEvent.click(screen.getByRole('button', { name: /submit form/i }))

    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText(/not authorised/i)).toBeInTheDocument()
  })
})
