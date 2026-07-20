import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { act, createElement } from 'react'
import { ProfilePage } from './ProfilePage'
import { useSessionStore } from '../hooks/useSessionStore'
import type { UserProfile } from '../types/user'

const mockProfile: UserProfile = {
  userId: 'u1',
  firstName: 'Jane',
  lastName: 'Doe',
  address: '123 Main St',
  email: 'jane@example.com',
  status: 'active',
  createdAt: '2026-01-01T00:00:00Z',
}

function renderProfilePage() {
  return render(
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
  )
}

describe('ProfilePage', () => {
  beforeEach(() => {
    act(() => useSessionStore.getState().clearSession())
    vi.clearAllMocks()
  })

  it('ProfilePage - no session - redirects to /login', () => {
    renderProfilePage()
    expect(screen.getByText('Login Page')).toBeInTheDocument()
  })

  it('ProfilePage - session exists - renders user name', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    renderProfilePage()
    expect(screen.getByRole('heading', { name: /jane doe/i })).toBeInTheDocument()
  })

  it('ProfilePage - session exists - renders email, address, status, member since', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    renderProfilePage()
    expect(screen.getByText('jane@example.com')).toBeInTheDocument()
    expect(screen.getByText('123 Main St')).toBeInTheDocument()
    expect(screen.getByText('active')).toBeInTheDocument()
    expect(screen.getByText(/january 1, 2026/i)).toBeInTheDocument()
  })

  it("ProfilePage - session exists - renders today's date", () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    renderProfilePage()
    const today = new Date().toLocaleDateString(undefined, {
      weekday: 'long',
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    })
    expect(screen.getByText(today)).toBeInTheDocument()
  })
})
