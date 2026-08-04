import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
} from '@tanstack/react-router'
import { createElement } from 'react'
import { AuthCallbackHandler } from './AuthCallbackHandler'
import * as userApi from '../api/userApi'
import type { UserResponse } from '../types/user'
import { useSessionStore } from '../hooks/useSessionStore'

// Build a minimal real-looking JWT that decodes to userId 'user-123'
const SUB = 'abc-user-id-123'
// exp in far future: 9999999999
const JWT_PAYLOAD = btoa(JSON.stringify({ sub: SUB, exp: 9999999999, iss: 'trade-platform' }))
const VALID_TOKEN = `header.${JWT_PAYLOAD}.signature`

const mockUserResponse: UserResponse = {
  userId: SUB,
  firstName: 'Jane',
  lastName: 'Doe',
  address: null,
  email: 'jane@example.com',
  status: 'active',
  createdAt: '2026-01-01T00:00:00Z',
  settings: { feedType: 'SYNTHETIC', updatedAt: '2026-01-01T00:00:00Z' },
}

async function renderWithToken(token: string | null) {
  const search = token ? `?token=${token}` : ''
  const onSuccess = vi.fn()
  const rootRoute = createRootRoute()
  const callbackRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/auth/callback',
    component: () => createElement(AuthCallbackHandler, { onSuccess }),
  })
  const routeTree = rootRoute.addChildren([callbackRoute])
  const router = createRouter({
    routeTree,
    history: createMemoryHistory({ initialEntries: [`/auth/callback${search}`] }),
  })
  await router.load()
  render(createElement(RouterProvider, { router }))
  return { onSuccess }
}

describe('AuthCallbackHandler', () => {
  beforeEach(() => {
    useSessionStore.getState().clearSession()
    vi.restoreAllMocks()
  })

  it('AuthCallbackHandler - valid token - shows loading indicator initially', async () => {
    vi.spyOn(userApi, 'fetchUserById').mockReturnValue(new Promise(() => {}))
    await renderWithToken(VALID_TOKEN)
    expect(screen.getByRole('status')).toBeInTheDocument()
  })

  it('AuthCallbackHandler - valid token and successful fetch - calls onSuccess', async () => {
    vi.spyOn(userApi, 'fetchUserById').mockResolvedValue(mockUserResponse)
    const { onSuccess } = await renderWithToken(VALID_TOKEN)
    await waitFor(() => expect(onSuccess).toHaveBeenCalledTimes(1))
  })

  it('AuthCallbackHandler - valid token and successful fetch - establishes session', async () => {
    vi.spyOn(userApi, 'fetchUserById').mockResolvedValue(mockUserResponse)
    await renderWithToken(VALID_TOKEN)
    await waitFor(() =>
      expect(useSessionStore.getState().session?.accessToken).toBe(VALID_TOKEN),
    )
  })

  it('AuthCallbackHandler - valid token - passes token to fetchUserById for bootstrap auth', async () => {
    const spy = vi.spyOn(userApi, 'fetchUserById').mockResolvedValue(mockUserResponse)
    renderWithToken(VALID_TOKEN)
    await waitFor(() => expect(spy).toHaveBeenCalledTimes(1))
    expect(spy).toHaveBeenCalledWith(SUB, VALID_TOKEN)
  })

  it('AuthCallbackHandler - missing token - shows error message', async () => {
    await renderWithToken(null)
    await waitFor(() =>
      expect(screen.getByRole('alert')).toBeInTheDocument(),
    )
    expect(screen.getByText(/authentication failed/i)).toBeInTheDocument()
  })

  it('AuthCallbackHandler - fetch fails - shows error message', async () => {
    vi.spyOn(userApi, 'fetchUserById').mockRejectedValue(new Error('Network error'))
    await renderWithToken(VALID_TOKEN)
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument())
    expect(screen.getByText(/authentication failed/i)).toBeInTheDocument()
  })

  it('AuthCallbackHandler - fetch fails - does not call onSuccess', async () => {
    vi.spyOn(userApi, 'fetchUserById').mockRejectedValue(new Error('Network error'))
    const { onSuccess } = await renderWithToken(VALID_TOKEN)
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument())
    expect(onSuccess).not.toHaveBeenCalled()
  })
})
