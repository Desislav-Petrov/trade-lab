import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { createElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act } from 'react'
import { TransactionListPage } from './TransactionListPage'

// jsdom does not implement scrollIntoView — stub it to prevent unhandled errors
window.HTMLElement.prototype.scrollIntoView = vi.fn()
import { useSessionStore } from '../../user/hooks/useSessionStore'
import type { UserResponse } from '../../user/types/user'
import type { TransactionResponse, TransactionListResponse } from '../types/transaction'
import type { TransactionTableProps } from '../components/TransactionTable'

vi.mock('../hooks/useTransactions', () => ({
  useTransactions: vi.fn(),
}))

vi.mock('../components/TransactionTable', () => ({
  TransactionTable: ({ transactions, isLoading, isError }: TransactionTableProps) => {
    if (isLoading) return createElement('div', { role: 'status' }, 'Loading…')
    if (isError) return createElement('p', { role: 'alert' }, 'Could not load transactions.')
    if (transactions.length === 0) return createElement('p', null, 'No transactions yet.')
    return createElement(
      'div',
      { 'data-testid': 'transaction-table' },
      `${transactions.length} transactions`,
    )
  },
}))

vi.mock('../components/PaginationControls', () => ({
  PaginationControls: ({
    currentPage,
    totalPages,
    onPageChange,
  }: {
    currentPage: number
    totalPages: number
    onPageChange: (page: number) => void
  }) => {
    if (totalPages === 0) return null
    return createElement(
      'div',
      { 'data-testid': 'pagination-controls' },
      createElement('span', null, `Page ${currentPage + 1} of ${totalPages}`),
      createElement('button', { onClick: () => onPageChange(currentPage + 1) }, 'Next'),
    )
  },
}))

import { useTransactions } from '../hooks/useTransactions'
const mockUseTransactions = vi.mocked(useTransactions)

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

const mockTransaction: TransactionResponse = {
  id: 'tx-1',
  type: 'CREDIT',
  assetType: 'CASH',
  amount: 1000,
  currency: 'USD',
  ticker: null,
  shares: null,
  description: 'Initial deposit',
  createdAt: '2026-07-01T10:00:00Z',
}

const mockTransactionListResponse: TransactionListResponse = {
  transactions: [mockTransaction],
  page: 0,
  totalPages: 3,
  totalCount: 60,
}

function renderPage(
  initialPath = '/accounts/acc-1/transactions',
  locationState?: Record<string, string>,
) {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return render(
    createElement(
      QueryClientProvider,
      { client: queryClient },
      createElement(
        MemoryRouter,
        {
          initialEntries: [
            locationState ? { pathname: initialPath, state: locationState } : initialPath,
          ],
        },
        createElement(
          Routes,
          null,
          createElement(Route, {
            path: '/accounts/:accountId/transactions',
            element: createElement(TransactionListPage),
          }),
          createElement(Route, {
            path: '/login',
            element: createElement('div', null, 'Login Page'),
          }),
        ),
      ),
    ),
  )
}

describe('TransactionListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    act(() => useSessionStore.getState().clearSession())
  })

  it('TransactionListPage - no session - redirects to /login', () => {
    mockUseTransactions.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
      error: null,
    })
    renderPage()
    expect(screen.getByText('Login Page')).toBeInTheDocument()
  })

  it('TransactionListPage - session exists with location state - renders heading with name and currency', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockUseTransactions.mockReturnValue({
      data: mockTransactionListResponse,
      isLoading: false,
      isError: false,
      error: null,
    })
    renderPage('/accounts/acc-1/transactions', {
      accountName: 'My USD Account',
      currency: 'USD',
    })
    expect(screen.getByRole('heading', { name: 'My USD Account — USD' })).toBeInTheDocument()
  })

  it('TransactionListPage - no location state - falls back to accountId in heading', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockUseTransactions.mockReturnValue({
      data: mockTransactionListResponse,
      isLoading: false,
      isError: false,
      error: null,
    })
    renderPage('/accounts/acc-1/transactions')
    expect(screen.getByRole('heading', { name: 'acc-1' })).toBeInTheDocument()
  })

  it('TransactionListPage - data loaded - renders TransactionTable with transactions', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockUseTransactions.mockReturnValue({
      data: mockTransactionListResponse,
      isLoading: false,
      isError: false,
      error: null,
    })
    renderPage('/accounts/acc-1/transactions', {
      accountName: 'My USD Account',
      currency: 'USD',
    })
    expect(screen.getByTestId('transaction-table')).toBeInTheDocument()
    expect(screen.getByText('1 transactions')).toBeInTheDocument()
  })

  it('TransactionListPage - data loaded - renders PaginationControls', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockUseTransactions.mockReturnValue({
      data: mockTransactionListResponse,
      isLoading: false,
      isError: false,
      error: null,
    })
    renderPage('/accounts/acc-1/transactions', {
      accountName: 'My USD Account',
      currency: 'USD',
    })
    expect(screen.getByTestId('pagination-controls')).toBeInTheDocument()
    expect(screen.getByText('Page 1 of 3')).toBeInTheDocument()
  })

  it('TransactionListPage - clicking Next - calls useTransactions with incremented page', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockUseTransactions.mockReturnValue({
      data: mockTransactionListResponse,
      isLoading: false,
      isError: false,
      error: null,
    })
    renderPage('/accounts/acc-1/transactions', {
      accountName: 'My USD Account',
      currency: 'USD',
    })

    expect(mockUseTransactions).toHaveBeenCalledWith('acc-1', 'u1', 0)

    fireEvent.click(screen.getByRole('button', { name: 'Next' }))

    expect(mockUseTransactions).toHaveBeenCalledWith('acc-1', 'u1', 1)
    expect(screen.getByText('Page 2 of 3')).toBeInTheDocument()
  })

  it('TransactionListPage - isLoading true - renders loading state via TransactionTable', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockUseTransactions.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      error: null,
    })
    renderPage('/accounts/acc-1/transactions', {
      accountName: 'My USD Account',
      currency: 'USD',
    })
    expect(screen.getByRole('status')).toBeInTheDocument()
  })

  it('TransactionListPage - isError true - renders error state via TransactionTable', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockUseTransactions.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: new Error('500'),
    })
    renderPage('/accounts/acc-1/transactions', {
      accountName: 'My USD Account',
      currency: 'USD',
    })
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText('Could not load transactions.')).toBeInTheDocument()
  })

  it('TransactionListPage - empty transactions - renders empty state and no pagination', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockUseTransactions.mockReturnValue({
      data: { transactions: [], page: 0, totalPages: 0, totalCount: 0 },
      isLoading: false,
      isError: false,
      error: null,
    })
    renderPage('/accounts/acc-1/transactions', {
      accountName: 'My USD Account',
      currency: 'USD',
    })
    expect(screen.getByText('No transactions yet.')).toBeInTheDocument()
    expect(screen.queryByTestId('pagination-controls')).not.toBeInTheDocument()
  })
})
