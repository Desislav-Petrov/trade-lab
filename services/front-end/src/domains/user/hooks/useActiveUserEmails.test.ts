import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useActiveUserEmails } from './useActiveUserEmails'

vi.mock('../api/userApi', () => ({
  getActiveUserEmails: vi.fn(),
  ACTIVE_USER_EMAILS_KEY: ['users', 'emails'],
}))

import { getActiveUserEmails } from '../api/userApi'
const mockGetActiveUserEmails = vi.mocked(getActiveUserEmails)

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

describe('useActiveUserEmails', () => {
  beforeEach(() => vi.clearAllMocks())

  it('useActiveUserEmails - success - returns emails', async () => {
    const emails = ['a@example.com', 'b@example.com']
    mockGetActiveUserEmails.mockResolvedValueOnce({ emails })

    const { result } = renderHook(() => useActiveUserEmails(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.emails).toEqual(emails)
  })

  it('useActiveUserEmails - empty list - returns empty array', async () => {
    mockGetActiveUserEmails.mockResolvedValueOnce({ emails: [] })

    const { result } = renderHook(() => useActiveUserEmails(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.emails).toEqual([])
  })

  it('useActiveUserEmails - network error - isError is true', async () => {
    mockGetActiveUserEmails.mockRejectedValueOnce(new Error('Network error'))

    const { result } = renderHook(() => useActiveUserEmails(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
