import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { act, createElement } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ProfilePage } from './ProfilePage'
import { useSessionStore } from '../hooks/useSessionStore'
import type { UserResponse } from '../types/user'

const mockResponse: UserResponse = {
  userId: 'u1',
  firstName: 'Jane',
  lastName: 'Doe',
  address: '123 Main St',
  email: 'jane@example.com',
  status: 'active',
  createdAt: '2026-01-01T00:00:00Z',
  settings: { feedType: 'SYNTHETIC', updatedAt: '2026-01-01T00:00:00Z' },
}

function renderProfilePage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    createElement(
      QueryClientProvider,
      { client: queryClient },
      createElement(
        MemoryRouter,
        { initialEntries: ['/profile'] },
        createElement(
          Routes,
          null,
          createElement(Route, { path: '/profile', element: createElement(ProfilePage) }),
          createElement(Route, { path: '/login', element: createElement('div', null, 'Login Page') }),
        ),
      ),
    ),
  )
}

describe('ProfilePage', () => {
  beforeEach(() => {
    act(() => useSessionStore.getState().clearSession())
    vi.clearAllMocks()
  })

  it('ProfilePage - redirects to login when no session', () => {
    renderProfilePage()
    expect(screen.getByText('Login Page')).toBeInTheDocument()
  })

  it('ProfilePage - renders Profile Information tab by default', () => {
    act(() => useSessionStore.getState().setSession(mockResponse))
    renderProfilePage()
    expect(screen.getByRole('heading', { name: /jane doe/i })).toBeInTheDocument()
    expect(screen.getByText('jane@example.com')).toBeInTheDocument()
    expect(screen.getByText('123 Main St')).toBeInTheDocument()
    expect(screen.getByText('active')).toBeInTheDocument()
    expect(screen.getByText(/january 1, 2026/i)).toBeInTheDocument()
  })

  it("ProfilePage - session exists - renders today's date", () => {
    act(() => useSessionStore.getState().setSession(mockResponse))
    renderProfilePage()
    const today = new Date().toLocaleDateString(undefined, {
      weekday: 'long',
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    })
    expect(screen.getByText(today)).toBeInTheDocument()
  })

  it('ProfilePage - renders Platform Settings tab when selected', async () => {
    act(() => useSessionStore.getState().setSession(mockResponse))
    renderProfilePage()

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /platform settings/i }))

    expect(screen.getByText('General Platform Settings')).toBeInTheDocument()
  })
})
