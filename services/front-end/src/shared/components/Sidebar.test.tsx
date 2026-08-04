import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
} from '@tanstack/react-router'
import { act } from 'react'
import { Sidebar } from './Sidebar'
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

async function renderSidebar(initialPath = '/') {
  const rootRoute = createRootRoute({ component: Sidebar })
  const catchAllRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '$',
    component: () => null,
  })
  const routeTree = rootRoute.addChildren([catchAllRoute])
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

describe('Sidebar', () => {
  beforeEach(() => {
    act(() => useSessionStore.getState().clearSession())
  })

  it('Sidebar - renders - has main navigation landmark', async () => {
    await renderSidebar()
    expect(screen.getByRole('navigation', { name: /main navigation/i })).toBeInTheDocument()
  })

  it('Sidebar - no session - Profile link is not rendered', async () => {
    await renderSidebar()
    expect(screen.queryByRole('link', { name: /profile/i })).not.toBeInTheDocument()
  })

  it('Sidebar - session exists - Profile link is rendered', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    await renderSidebar()
    expect(screen.getByRole('link', { name: /profile/i })).toBeInTheDocument()
  })

  it('Sidebar - active route /profile - Profile link has active styles', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    await renderSidebar('/profile')
    const profileLink = screen.getByRole('link', { name: /profile/i })
    expect(profileLink).toHaveAttribute('aria-current', 'page')
  })

  it('Sidebar - no session - Accounts link is not rendered', async () => {
    await renderSidebar()
    expect(screen.queryByRole('link', { name: /accounts/i })).not.toBeInTheDocument()
  })

  it('Sidebar - session exists - Accounts link is rendered', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    await renderSidebar()
    expect(screen.getByRole('link', { name: /accounts/i })).toBeInTheDocument()
  })

  it('Sidebar - active route /accounts - Accounts link has active styles', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    await renderSidebar('/accounts')
    const accountsLink = screen.getByRole('link', { name: /accounts/i })
    expect(accountsLink).toHaveAttribute('aria-current', 'page')
  })

  it('Sidebar - no session - Stock Trading link is not rendered', async () => {
    await renderSidebar()
    expect(screen.queryByRole('link', { name: /stock trading/i })).not.toBeInTheDocument()
  })

  it('Sidebar - session exists - Stock Trading link is rendered', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    await renderSidebar()
    expect(screen.getByRole('link', { name: /stock trading/i })).toBeInTheDocument()
  })

  it('Sidebar - active route /trade - Stock Trading link has active styles', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    await renderSidebar('/trade')
    const tradeLink = screen.getByRole('link', { name: /stock trading/i })
    expect(tradeLink).toHaveAttribute('aria-current', 'page')
  })
})
