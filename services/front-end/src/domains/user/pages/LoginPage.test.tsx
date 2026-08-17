import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
} from '@tanstack/react-router'
import { createElement, act } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { LoginResponse } from '../types/user'
import { LoginPage } from './LoginPage'
import * as useFetchUserProfileModule from '../hooks/useFetchUserProfile'
import * as oidcApiModule from '../api/oidcApi'

vi.mock('../components/LoginForm', () => ({
  LoginForm: ({ onSuccess }: { onSuccess?: (data: LoginResponse) => void }) =>
    createElement(
      'div',
      null,
      createElement('span', null, 'LoginForm'),
      createElement(
        'button',
        { onClick: () => onSuccess?.({ userId: 'u1', email: 'a@example.com' }) },
        'Trigger Success',
      ),
    ),
}))

async function renderPage(initialPath = '/login', state?: Record<string, unknown>) {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  const rootRoute = createRootRoute()
  const loginRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/login',
    component: LoginPage,
  })
  const profileRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/profile',
    component: () => createElement('div', null, 'Profile Page'),
  })
  const routeTree = rootRoute.addChildren([loginRoute, profileRoute])
  const memoryHistory = createMemoryHistory({ initialEntries: [initialPath] })
  if (state) {
    memoryHistory.push(initialPath, state)
  }
  const router = createRouter({ routeTree, history: memoryHistory })
  await router.load()
  let result!: ReturnType<typeof render>
  await act(async () => {
    result = render(
    createElement(QueryClientProvider, { client: queryClient }, createElement(RouterProvider, { router })),
    )
  })
  return result
}

async function renderPageWithSearch(search: string) {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  const rootRoute = createRootRoute()
  const loginRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/login',
    component: LoginPage,
  })
  const profileRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/profile',
    component: () => createElement('div', null, 'Profile Page'),
  })
  const routeTree = rootRoute.addChildren([loginRoute, profileRoute])
  const router = createRouter({
    routeTree,
    history: createMemoryHistory({ initialEntries: [`/login${search}`] }),
  })
  await router.load()
  let result!: ReturnType<typeof render>
  await act(async () => {
    result = render(
    createElement(QueryClientProvider, { client: queryClient }, createElement(RouterProvider, { router })),
    )
  })
  return result
}

describe('LoginPage', () => {
  afterEach(() => vi.restoreAllMocks())

  it('LoginPage - renders - displays LoginForm', async () => {
    await renderPage()
    expect(screen.getByText('LoginForm')).toBeInTheDocument()
  })

  it('LoginPage - renders - displays heading', async () => {
    await renderPage()
    expect(screen.getByRole('heading', { name: /log in/i })).toBeInTheDocument()
  })

  it('LoginPage - renders - displays Login with Google button', async () => {
    await renderPage()
    expect(screen.getByRole('button', { name: /login with google/i })).toBeInTheDocument()
  })

  it('LoginPage - with banner state - displays success banner', async () => {
    await renderPage('/login', { banner: 'Account created. Please log in.' })
    expect(screen.getByRole('status')).toBeInTheDocument()
    expect(screen.getByText(/account created/i)).toBeInTheDocument()
  })

  it('LoginPage - without banner state - does not render banner', async () => {
    await renderPage()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('LoginPage - error=oidc_failed query param - shows oidc error banner', async () => {
    await renderPageWithSearch('?error=oidc_failed')
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText(/google authentication failed/i)).toBeInTheDocument()
  })

  it('LoginPage - error=server_error query param - shows server error banner', async () => {
    await renderPageWithSearch('?error=server_error')
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText(/something went wrong/i)).toBeInTheDocument()
  })

  it('LoginPage - no error query param - does not show error banner', async () => {
    await renderPage()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('LoginPage - google button click - calls redirectToGoogleLogin', async () => {
    const redirectSpy = vi.spyOn(oidcApiModule, 'redirectToGoogleLogin').mockReturnValue(undefined)
    await renderPage()
    fireEvent.click(screen.getByRole('button', { name: /login with google/i }))
    expect(redirectSpy).toHaveBeenCalledTimes(1)
  })

  it('LoginPage - profile fetch succeeds - navigates to /profile', async () => {
    vi.spyOn(useFetchUserProfileModule, 'useFetchUserProfile').mockImplementation(
      ({ onSuccess } = {}) =>
        ({
          mutate: () => {
            onSuccess?.()
          },
          isPending: false,
        }) as unknown as ReturnType<typeof useFetchUserProfileModule.useFetchUserProfile>,
    )

    await renderPage()
    fireEvent.click(screen.getByRole('button', { name: /trigger success/i }))
    expect(await screen.findByText('Profile Page')).toBeInTheDocument()
  })

  it('LoginPage - profile fetch fails - shows profile error message', async () => {
    vi.spyOn(useFetchUserProfileModule, 'useFetchUserProfile').mockImplementation(
      ({ onError } = {}) =>
        ({
          mutate: () => {
            onError?.()
          },
          isPending: false,
        }) as unknown as ReturnType<typeof useFetchUserProfileModule.useFetchUserProfile>,
    )

    await renderPage()
    fireEvent.click(screen.getByRole('button', { name: /trigger success/i }))
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByText(/unable to load your profile/i)).toBeInTheDocument()
  })

  it('LoginPage - renders - shows register link', async () => {
    await renderPage()
    expect(screen.getByRole('link', { name: /register/i })).toBeInTheDocument()
  })

  it('LoginPage - renders - displays Login with GitHub button', async () => {
    await renderPage()
    expect(screen.getByRole('button', { name: /login with github/i })).toBeInTheDocument()
  })

  it('LoginPage - github button click - calls redirectToGithubLogin', async () => {
    const redirectSpy = vi.spyOn(oidcApiModule, 'redirectToGithubLogin').mockReturnValue(undefined)
    await renderPage()
    fireEvent.click(screen.getByRole('button', { name: /login with github/i }))
    expect(redirectSpy).toHaveBeenCalledTimes(1)
  })

  it('LoginPage - error=github_oidc_failed query param - shows github oidc error banner', async () => {
    await renderPageWithSearch('?error=github_oidc_failed')
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText(/github authentication failed/i)).toBeInTheDocument()
  })

  it('LoginPage - error=github_no_email query param - shows github no email error banner', async () => {
    await renderPageWithSearch('?error=github_no_email')
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText(/no public email/i)).toBeInTheDocument()
  })
})
