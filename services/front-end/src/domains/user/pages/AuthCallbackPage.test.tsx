import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
} from '@tanstack/react-router'
import { createElement, act } from 'react'
import { AuthCallbackPage } from './AuthCallbackPage'
import * as userApi from '../api/userApi'
import type { UserResponse } from '../types/user'
import { useSessionStore } from '../hooks/useSessionStore'

const SUB = 'abc-user-id-123'
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

async function renderPage(token: string | null) {
  const search = token ? `?token=${token}` : ''
  const rootRoute = createRootRoute()
  const callbackRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/auth/callback',
    component: AuthCallbackPage,
  })
  const tradeRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/trade',
    component: () => createElement('div', null, 'Trade Page'),
  })
  const routeTree = rootRoute.addChildren([callbackRoute, tradeRoute])
  const router = createRouter({
    routeTree,
    history: createMemoryHistory({ initialEntries: [`/auth/callback${search}`] }),
  })
  await router.load()
  let result!: ReturnType<typeof render>
  await act(async () => {
    result = render(createElement(RouterProvider, { router }))
  })
  return result
}

describe('AuthCallbackPage', () => {
  beforeEach(() => {
    useSessionStore.getState().clearSession()
    vi.restoreAllMocks()
  })

  it('AuthCallbackPage - successful session - navigates to /trade', async () => {
    vi.spyOn(userApi, 'fetchUserById').mockResolvedValue(mockUserResponse)
    await renderPage(VALID_TOKEN)
    await waitFor(() => expect(screen.getByText('Trade Page')).toBeInTheDocument())
  })

  it('AuthCallbackPage - missing token - displays error message', async () => {
    await renderPage(null)
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument())
    expect(screen.getByText(/authentication failed/i)).toBeInTheDocument()
  })

  it('AuthCallbackPage - fetch fails - displays error without navigating', async () => {
    vi.spyOn(userApi, 'fetchUserById').mockRejectedValue(new Error('fetch error'))
    await renderPage(VALID_TOKEN)
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument())
    expect(screen.queryByText('Trade Page')).not.toBeInTheDocument()
  })
})
