import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { createElement } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { LoginForm } from './LoginForm'

vi.mock('../hooks/useActiveUserEmails', () => ({ useActiveUserEmails: vi.fn() }))
vi.mock('../hooks/useLoginUser', () => ({ useLoginUser: vi.fn() }))

import { useActiveUserEmails } from '../hooks/useActiveUserEmails'
import { useLoginUser } from '../hooks/useLoginUser'
const mockUseActiveUserEmails = vi.mocked(useActiveUserEmails)
const mockUseLoginUser = vi.mocked(useLoginUser)

function renderForm(onSuccess?: () => void) {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return render(
    createElement(
      QueryClientProvider,
      { client: queryClient },
      createElement(LoginForm, { onSuccess }),
    ),
  )
}

function setupMutationMock(overrides: Record<string, unknown> = {}) {
  mockUseLoginUser.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
    ...overrides,
  } as unknown as ReturnType<typeof useLoginUser>)
}

describe('LoginForm', () => {
  beforeEach(() => vi.clearAllMocks())

  it('LoginForm - loading emails - shows loading message', () => {
    mockUseActiveUserEmails.mockReturnValue({
      isLoading: true,
      isError: false,
      data: undefined,
    } as unknown as ReturnType<typeof useActiveUserEmails>)
    setupMutationMock()
    renderForm()

    expect(screen.getByText(/loading accounts/i)).toBeInTheDocument()
  })

  it('LoginForm - email fetch error - shows error message', () => {
    mockUseActiveUserEmails.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
    } as unknown as ReturnType<typeof useActiveUserEmails>)
    setupMutationMock()
    renderForm()

    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText(/failed to load accounts/i)).toBeInTheDocument()
  })

  it('LoginForm - no active users - shows empty state message', () => {
    mockUseActiveUserEmails.mockReturnValue({
      isLoading: false,
      isError: false,
      data: { emails: [] },
    } as unknown as ReturnType<typeof useActiveUserEmails>)
    setupMutationMock()
    renderForm()

    expect(screen.getByText(/no active accounts found/i)).toBeInTheDocument()
  })

  it('LoginForm - emails loaded - renders select with options', () => {
    mockUseActiveUserEmails.mockReturnValue({
      isLoading: false,
      isError: false,
      data: { emails: ['a@example.com', 'b@example.com'] },
    } as unknown as ReturnType<typeof useActiveUserEmails>)
    setupMutationMock()
    renderForm()

    expect(screen.getByRole('combobox')).toBeInTheDocument()
    expect(screen.getByText('a@example.com')).toBeInTheDocument()
    expect(screen.getByText('b@example.com')).toBeInTheDocument()
  })

  it('LoginForm - submit with email selected - calls mutate', async () => {
    const mutate = vi.fn()
    mockUseActiveUserEmails.mockReturnValue({
      isLoading: false,
      isError: false,
      data: { emails: ['a@example.com'] },
    } as unknown as ReturnType<typeof useActiveUserEmails>)
    setupMutationMock({ mutate })
    renderForm()

    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'a@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: /log in/i }))

    await waitFor(() => expect(mutate).toHaveBeenCalledWith({ email: 'a@example.com' }))
  })

  it('LoginForm - pending state - disables button', () => {
    mockUseActiveUserEmails.mockReturnValue({
      isLoading: false,
      isError: false,
      data: { emails: ['a@example.com'] },
    } as unknown as ReturnType<typeof useActiveUserEmails>)
    setupMutationMock({ isPending: true })
    renderForm()

    expect(screen.getByRole('button', { name: /logging in/i })).toBeDisabled()
  })

  it('LoginForm - 404 server error - shows not found message', () => {
    const error = Object.assign(new Error('Not Found'), {
      isAxiosError: true,
      response: { status: 404 },
    })
    mockUseActiveUserEmails.mockReturnValue({
      isLoading: false,
      isError: false,
      data: { emails: ['a@example.com'] },
    } as unknown as ReturnType<typeof useActiveUserEmails>)
    setupMutationMock({ isError: true, error })
    renderForm()

    expect(screen.getByText(/no account found for this email/i)).toBeInTheDocument()
  })

  it('LoginForm - 403 server error - shows account unavailable message', () => {
    const error = Object.assign(new Error('Forbidden'), {
      isAxiosError: true,
      response: { status: 403 },
    })
    mockUseActiveUserEmails.mockReturnValue({
      isLoading: false,
      isError: false,
      data: { emails: ['a@example.com'] },
    } as unknown as ReturnType<typeof useActiveUserEmails>)
    setupMutationMock({ isError: true, error })
    renderForm()

    expect(screen.getByText(/suspended or closed/i)).toBeInTheDocument()
  })
})
