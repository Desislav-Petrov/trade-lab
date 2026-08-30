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
import { LoginPage } from './LoginPage'
import * as oidcApiModule from '../api/oidcApi'
import * as useLoginUserModule from '../hooks/useLoginUser'

vi.mock('../components/LoginForm', () => ({
  LoginForm: () => {
    const { mutate } = useLoginUserModule.useLoginUser()
    return (
    createElement(
      'div',
      null,
      createElement('span', null, 'LoginForm'),
      createElement('button', { onClick: () => mutate({ email: 'a@example.com' }) }, 'Trigger Login'),
    ))
  },
}))

async function renderPage(initialPath = '/login', state?: Record<string, unknown>) {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  const rootRoute = createRootRoute()
  const loginRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/login',
    component: LoginPage,
  })
  const routeTree = rootRoute.addChildren([loginRoute])
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
  const routeTree = rootRoute.addChildren([loginRoute])
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

  it('LoginPage - error=google_oidc_failed query param - shows google oidc error banner', async () => {
    await renderPageWithSearch('?error=google_oidc_failed')
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

  it('LoginPage - login success - assigns redirect url', async () => {
    const assign = vi.fn()
    vi.stubGlobal('window', { location: { assign } })
    vi.spyOn(useLoginUserModule, 'useLoginUser').mockReturnValue({
      mutate: () => assign('/auth/callback?token=jwt-token'),
      isPending: false,
      isError: false,
      error: null,
    } as unknown as ReturnType<typeof useLoginUserModule.useLoginUser>)

    await renderPage()
    fireEvent.click(screen.getByRole('button', { name: /trigger login/i }))
    expect(assign).toHaveBeenCalledWith('/auth/callback?token=jwt-token')
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
