import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { act } from 'react'
import { Sidebar } from './Sidebar'
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

function renderSidebar(initialPath = '/') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Sidebar />
    </MemoryRouter>,
  )
}

describe('Sidebar', () => {
  beforeEach(() => {
    act(() => useSessionStore.getState().clearSession())
  })

  it('Sidebar - renders - has main navigation landmark', () => {
    renderSidebar()
    expect(screen.getByRole('navigation', { name: /main navigation/i })).toBeInTheDocument()
  })

  it('Sidebar - no session - Profile link is not rendered', () => {
    renderSidebar()
    expect(screen.queryByRole('link', { name: /profile/i })).not.toBeInTheDocument()
  })

  it('Sidebar - session exists - Profile link is rendered', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    renderSidebar()
    expect(screen.getByRole('link', { name: /profile/i })).toBeInTheDocument()
  })

  it('Sidebar - active route /profile - Profile link has active styles', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    renderSidebar('/profile')
    const profileLink = screen.getByRole('link', { name: /profile/i })
    expect(profileLink.className).toContain('text-[var(--color-accent)]')
  })

  it('Sidebar - no session - Accounts link is not rendered', () => {
    renderSidebar()
    expect(screen.queryByRole('link', { name: /accounts/i })).not.toBeInTheDocument()
  })

  it('Sidebar - session exists - Accounts link is rendered', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    renderSidebar()
    expect(screen.getByRole('link', { name: /accounts/i })).toBeInTheDocument()
  })

  it('Sidebar - active route /accounts - Accounts link has active styles', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    renderSidebar('/accounts')
    const accountsLink = screen.getByRole('link', { name: /accounts/i })
    expect(accountsLink.className).toContain('text-[var(--color-accent)]')
  })
})
