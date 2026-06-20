import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { act } from 'react'
import { Topbar } from './Topbar'
import { useSessionStore } from '../../domains/user/hooks/useSessionStore'
import type { UserProfile } from '../../domains/user/types/user'

const mockProfile: UserProfile = {
  userId: 'u1',
  firstName: 'Jane',
  lastName: 'Doe',
  address: '123 Main St',
  email: 'jane@example.com',
  status: 'active',
  createdAt: '2026-01-01T00:00:00Z',
}

function renderTopbar(initialPath = '/') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="*" element={<><Topbar /><div data-testid="outlet" /></>} />
        <Route path="/login" element={<div>Login Page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('Topbar', () => {
  beforeEach(() => {
    act(() => useSessionStore.getState().clearSession())
  })

  it('Topbar - renders - displays platform name', () => {
    renderTopbar()
    expect(screen.getByText('TRADE-LAB')).toBeInTheDocument()
  })

  it('Topbar - renders - has top bar landmark', () => {
    renderTopbar()
    expect(screen.getByRole('banner')).toBeInTheDocument()
  })

  it('Topbar - renders - has user area', () => {
    renderTopbar()
    expect(screen.getByLabelText(/user area/i)).toBeInTheDocument()
  })

  it('Topbar - no session - shows Login or Register', () => {
    renderTopbar()
    expect(screen.getByText('Login or Register')).toBeInTheDocument()
    expect(screen.queryByText(/logged in as/i)).not.toBeInTheDocument()
  })

  it('Topbar - session exists - shows logged in as name', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    renderTopbar()
    expect(screen.getByText(/logged in as jane doe/i)).toBeInTheDocument()
    expect(screen.queryByText('Login or Register')).not.toBeInTheDocument()
  })

  it('Topbar - session exists - shows today date', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    renderTopbar()
    const today = new Date().toLocaleDateString(undefined, {
      weekday: 'short', year: 'numeric', month: 'short', day: 'numeric',
    })
    expect(screen.getByText(today)).toBeInTheDocument()
  })

  it('Topbar - logout button clicked - clears session', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    renderTopbar()
    fireEvent.click(screen.getByRole('button', { name: /logout/i }))
    expect(useSessionStore.getState().user).toBeNull()
  })
})
