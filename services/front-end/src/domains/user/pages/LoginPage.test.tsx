import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { createElement } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { LoginResponse } from '../types/user'
import { LoginPage } from './LoginPage'
import * as useFetchUserProfileModule from '../hooks/useFetchUserProfile'
import type { UserProfile } from '../types/user'

vi.mock('../components/LoginForm', () => ({
  LoginForm: ({ onSuccess }: { onSuccess?: (data: LoginResponse) => void }) =>
    createElement('div', null,
      createElement('span', null, 'LoginForm'),
      createElement('button', { onClick: () => onSuccess?.({ userId: 'u1', email: 'a@example.com' }) }, 'Trigger Success'),
    ),
}))

function renderPage(initialPath = '/login', state?: Record<string, unknown>) {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return render(
    createElement(QueryClientProvider, { client: queryClient },
      createElement(MemoryRouter, { initialEntries: [{ pathname: initialPath, state: state ?? null }] },
        createElement(Routes, null,
          createElement(Route, { path: '/login', element: createElement(LoginPage) }),
          createElement(Route, { path: '/trade', element: createElement('div', null, 'Trade Page') }),
        ),
      ),
    ),
  )
}

describe('LoginPage', () => {
  it('LoginPage - renders - displays LoginForm', () => {
    renderPage()
    expect(screen.getByText('LoginForm')).toBeInTheDocument()
  })

  it('LoginPage - renders - displays heading', () => {
    renderPage()
    expect(screen.getByRole('heading', { name: /log in/i })).toBeInTheDocument()
  })

  it('LoginPage - with banner state - displays success banner', () => {
    renderPage('/login', { banner: 'Account created. Please log in.' })
    expect(screen.getByRole('status')).toBeInTheDocument()
    expect(screen.getByText(/account created/i)).toBeInTheDocument()
  })

  it('LoginPage - without banner state - does not render banner', () => {
    renderPage()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('LoginPage - profile fetch succeeds - navigates to /trade', async () => {
    const mockProfile: UserProfile = {
      userId: 'u1', firstName: 'Jane', lastName: 'Doe',
      address: '123 Main St', email: 'a@example.com',
      status: 'active', createdAt: '2026-01-01T00:00:00Z',
    }
    vi.spyOn(useFetchUserProfileModule, 'useFetchUserProfile').mockReturnValue({
      mutate: (userId: string, options?: { onSuccess?: (profile: UserProfile) => void }) => {
        options?.onSuccess?.(mockProfile)
      },
      isPending: false,
    } as ReturnType<typeof useFetchUserProfileModule.useFetchUserProfile>)

    renderPage()
    screen.getByRole('button', { name: /trigger success/i }).click()
    expect(await screen.findByText('Trade Page')).toBeInTheDocument()
  })

  it('LoginPage - profile fetch fails - shows profile error message', async () => {
    vi.spyOn(useFetchUserProfileModule, 'useFetchUserProfile').mockReturnValue({
      mutate: (_userId: string, options?: { onError?: () => void }) => {
        options?.onError?.()
      },
      isPending: false,
    } as ReturnType<typeof useFetchUserProfileModule.useFetchUserProfile>)

    renderPage()
    screen.getByRole('button', { name: /trigger success/i }).click()
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByText(/unable to load your profile/i)).toBeInTheDocument()
  })

  it('LoginPage - renders - shows register link', () => {
    renderPage()
    expect(screen.getByRole('link', { name: /register/i })).toBeInTheDocument()
  })
})
