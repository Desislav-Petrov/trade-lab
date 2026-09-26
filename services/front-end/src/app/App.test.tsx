import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import App from './App'
import { useSessionStore } from '../domains/user/hooks/useSessionStore'

vi.mock('@tanstack/react-router', () => ({
  RouterProvider: () => <div>Mock router</div>,
}))

vi.mock('./router', () => ({
  router: {},
}))

// The global AccountSelectionBootstrap reads active accounts via this hook.
// Stub it so the App-level tests do not trigger a real network fetch.
vi.mock('../domains/ledger/hooks/useLedger', () => ({
  useActiveAccounts: () => ({ data: undefined }),
}))

describe('App', () => {
  beforeEach(() => {
    useSessionStore.setState({
      session: null,
      user: null,
      settings: null,
      loggedInAt: null,
    })
  })

  it('App - active session exists - renders assistant widget', () => {
    useSessionStore.setState({
      session: {
        userId: 'u1',
        firstName: 'Jane',
        lastName: 'Doe',
        address: null,
        email: 'jane@example.com',
        status: 'active',
        createdAt: '2026-01-01T00:00:00Z',
        settings: { feedType: 'SYNTHETIC', updatedAt: '2026-01-01T00:00:00Z' },
        accessToken: 'token',
        loggedInAt: '2026-01-01T00:00:00Z',
      },
      user: {
        userId: 'u1',
        firstName: 'Jane',
        lastName: 'Doe',
        address: null,
        email: 'jane@example.com',
        status: 'active',
        createdAt: '2026-01-01T00:00:00Z',
      },
      settings: { feedType: 'SYNTHETIC', updatedAt: '2026-01-01T00:00:00Z' },
      loggedInAt: '2026-01-01T00:00:00Z',
    })

    render(<App />)

    expect(screen.getByRole('button', { name: 'Trade Lab AI Assistant' })).toBeInTheDocument()
  })

  it('App - no active session - does not render assistant widget', () => {
    render(<App />)

    expect(screen.queryByRole('button', { name: 'Trade Lab AI Assistant' })).not.toBeInTheDocument()
  })
})
