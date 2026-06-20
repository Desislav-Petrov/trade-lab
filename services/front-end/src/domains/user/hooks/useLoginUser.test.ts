import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import type { AxiosError } from 'axios'
import { useLoginUser } from './useLoginUser'

vi.mock('../api/userApi', () => ({
  loginUser: vi.fn(),
}))

import { loginUser } from '../api/userApi'
const mockLoginUser = vi.mocked(loginUser)

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

describe('useLoginUser', () => {
  beforeEach(() => vi.clearAllMocks())

  it('useLoginUser - success - calls onSuccess with response data', async () => {
    const payload = { userId: '550e8400-e29b-41d4-a716-446655440000', email: 'a@example.com' }
    mockLoginUser.mockResolvedValueOnce(payload)
    const onSuccess = vi.fn()

    const { result } = renderHook(() => useLoginUser({ onSuccess }), { wrapper: createWrapper() })

    act(() => {
      result.current.mutate({ email: 'a@example.com' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(onSuccess).toHaveBeenCalledOnce()
    expect(onSuccess).toHaveBeenCalledWith(payload)
  })

  it('useLoginUser - 404 response - exposes error', async () => {
    const error = Object.assign(new Error('Not Found'), {
      isAxiosError: true,
      response: { status: 404 },
    })
    mockLoginUser.mockRejectedValueOnce(error)

    const { result } = renderHook(() => useLoginUser(), { wrapper: createWrapper() })

    act(() => {
      result.current.mutate({ email: 'ghost@example.com' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as AxiosError)?.response?.status).toBe(404)
  })

  it('useLoginUser - 403 response - exposes error', async () => {
    const error = Object.assign(new Error('Forbidden'), {
      isAxiosError: true,
      response: { status: 403 },
    })
    mockLoginUser.mockRejectedValueOnce(error)

    const { result } = renderHook(() => useLoginUser(), { wrapper: createWrapper() })

    act(() => {
      result.current.mutate({ email: 'closed@example.com' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as AxiosError)?.response?.status).toBe(403)
  })
})
