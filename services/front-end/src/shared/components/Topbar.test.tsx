import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
} from '@tanstack/react-router'
import { act } from 'react'
import { Topbar } from './Topbar'
import { useSessionStore } from '../../domains/user/hooks/useSessionStore'
import type { UserResponse } from '../../domains/user/types/user'

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

async function renderTopbar(initialPath = '/') {
  const rootRoute = createRootRoute()
  const catchAllRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '$',
    component: () => (
      <>
        <Topbar />
        <div data-testid="outlet" />
      </>
    ),
  })
  const loginRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/login',
    component: () => <div>Login Page</div>,
  })
  const routeTree = rootRoute.addChildren([catchAllRoute, loginRoute])
  const router = createRouter({
    routeTree,
    history: createMemoryHistory({ initialEntries: [initialPath] }),
  })
  await router.load()
  let result!: ReturnType<typeof render>
  await act(async () => {
    result = render(<RouterProvider router={router} />)
  })
  return result
}

describe('Topbar', () => {
  beforeEach(() => {
    act(() => useSessionStore.getState().clearSession())
  })

  it('Topbar - renders - displays platform name', async () => {
    await renderTopbar()
    expect(screen.getByText('TRADE-LAB')).toBeInTheDocument()
  })

  it('Topbar - renders - has top bar landmark', async () => {
    await renderTopbar()
    expect(screen.getByRole('banner')).toBeInTheDocument()
  })

  it('Topbar - renders - has user area', async () => {
    await renderTopbar()
    expect(screen.getByLabelText(/user area/i)).toBeInTheDocument()
  })

  it('Topbar - no session - shows Login or Register', async () => {
    await renderTopbar()
    expect(screen.getByText('Login or Register')).toBeInTheDocument()
    expect(screen.queryByText(/logged in as/i)).not.toBeInTheDocument()
  })

  it('Topbar - session exists - shows logged in as name', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    await renderTopbar()
    expect(screen.getByText(/logged in as jane doe/i)).toBeInTheDocument()
    expect(screen.queryByText('Login or Register')).not.toBeInTheDocument()
  })

  it('Topbar - session exists - shows today date', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    await renderTopbar()
    const today = new Date().toLocaleDateString(undefined, {
      weekday: 'short',
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    })
    expect(screen.getByText(today)).toBeInTheDocument()
  })

  it('Topbar - logout button clicked - clears session', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    await renderTopbar()
    fireEvent.click(screen.getByRole('button', { name: /logout/i }))
    expect(useSessionStore.getState().user).toBeNull()
  })
})
