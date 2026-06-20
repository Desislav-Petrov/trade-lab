import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import type { UseMutationResult } from '@tanstack/react-query'
import type { RegisterUserRequest, RegisterUserResponse } from '../types/user'
import { RegistrationForm } from './RegistrationForm'

vi.mock('../hooks/useRegisterUser', () => ({
  useRegisterUser: vi.fn(),
}))

import { useRegisterUser } from '../hooks/useRegisterUser'
const mockUseRegisterUser = vi.mocked(useRegisterUser)

type MockHookResult = Pick<
  UseMutationResult<RegisterUserResponse, Error, RegisterUserRequest>,
  'mutate' | 'isPending' | 'isSuccess' | 'isError' | 'error'
>

function renderForm(onSuccess?: () => void) {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return render(
    createElement(QueryClientProvider, { client: queryClient },
      createElement(RegistrationForm, { onSuccess })
    )
  )
}

function setupDefaultMock(overrides: Partial<MockHookResult> = {}) {
  mockUseRegisterUser.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isSuccess: false,
    isError: false,
    error: null,
    ...overrides,
  } as ReturnType<typeof useRegisterUser>)
}

describe('RegistrationForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('RegistrationForm - renders all fields and submit button', () => {
    setupDefaultMock()
    renderForm()

    expect(screen.getByLabelText(/first name/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/last name/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/address/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /submit/i })).toBeInTheDocument()
  })

  it('RegistrationForm - submit with empty fields - shows inline errors', async () => {
    setupDefaultMock()
    renderForm()

    fireEvent.click(screen.getByRole('button', { name: /submit/i }))

    await waitFor(() => {
      expect(screen.getAllByRole('alert').length).toBeGreaterThan(0)
    })
    expect(screen.getByText(/first name is required/i)).toBeInTheDocument()
    expect(screen.getByText(/last name is required/i)).toBeInTheDocument()
    expect(screen.getByText(/address is required/i)).toBeInTheDocument()
    expect(screen.getByText(/email is required/i)).toBeInTheDocument()
  })

  it('RegistrationForm - submit with invalid email - shows email error', async () => {
    setupDefaultMock()
    renderForm()

    await userEvent.type(screen.getByLabelText(/first name/i), 'Jane')
    await userEvent.type(screen.getByLabelText(/last name/i), 'Doe')
    await userEvent.type(screen.getByLabelText(/address/i), '123 Main St')
    await userEvent.type(screen.getByLabelText(/email/i), 'not-an-email')

    fireEvent.click(screen.getByRole('button', { name: /submit/i }))

    await waitFor(() => {
      expect(screen.getByText(/must be a valid email address/i)).toBeInTheDocument()
    })
  })

  it('RegistrationForm - loading state - disables submit button', () => {
    setupDefaultMock({ isPending: true })
    renderForm()

    expect(screen.getByRole('button', { name: /submitting/i })).toBeDisabled()
  })

  it('RegistrationForm - 409 error - shows form-level duplicate email error', () => {
    const conflictError = Object.assign(new Error('Conflict'), {
      isAxiosError: true,
      response: { status: 409 },
    })
    setupDefaultMock({ isError: true, error: conflictError })
    renderForm()

    expect(screen.getByText(/an account with this email already exists/i)).toBeInTheDocument()
  })
})
