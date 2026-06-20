import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import type { AxiosError } from 'axios'
import { useRegisterUser } from './useRegisterUser'

vi.mock('../api/userApi', () => ({
  createUser: vi.fn(),
}))

import { createUser } from '../api/userApi'
const mockCreateUser = vi.mocked(createUser)

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

describe('useRegisterUser', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('useRegisterUser - success - callsOnSuccessCallback', async () => {
    mockCreateUser.mockResolvedValueOnce({ userId: '550e8400-e29b-41d4-a716-446655440000' })
    const onSuccess = vi.fn()

    const { result } = renderHook(() => useRegisterUser({ onSuccess }), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate({ firstName: 'Jane', lastName: 'Doe', address: '123 Main St', email: 'jane@example.com' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(onSuccess).toHaveBeenCalledOnce()
  })

  it('useRegisterUser - 409 response - exposesConflictError', async () => {
    const error = Object.assign(new Error('Conflict'), {
      isAxiosError: true,
      response: { status: 409 },
    })
    mockCreateUser.mockRejectedValueOnce(error)

    const { result } = renderHook(() => useRegisterUser(), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate({ firstName: 'Jane', lastName: 'Doe', address: '123 Main St', email: 'dupe@example.com' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as AxiosError)?.response?.status).toBe(409)
  })

  it('useRegisterUser - 400 response - exposesValidationError', async () => {
    const error = Object.assign(new Error('Bad Request'), {
      isAxiosError: true,
      response: { status: 400 },
    })
    mockCreateUser.mockRejectedValueOnce(error)

    const { result } = renderHook(() => useRegisterUser(), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate({ firstName: '', lastName: 'Doe', address: '123 Main St', email: 'jane@example.com' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as AxiosError)?.response?.status).toBe(400)
  })
})
