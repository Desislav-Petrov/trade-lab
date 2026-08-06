import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
} from '@tanstack/react-router'
import { createElement, act } from 'react'
import { RegistrationPage } from './RegistrationPage'

vi.mock('../components/RegistrationForm', () => ({
  RegistrationForm: ({ onSuccess }: { onSuccess?: () => void }) =>
    createElement(
      'div',
      null,
      createElement('span', null, 'RegistrationForm'),
      createElement('button', { onClick: onSuccess }, 'Trigger Success'),
    ),
}))

async function renderPage() {
  const rootRoute = createRootRoute()
  const registerRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/register',
    component: RegistrationPage,
  })
  const loginRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/login',
    component: () => createElement('div', null, 'Login Page'),
  })
  const routeTree = rootRoute.addChildren([registerRoute, loginRoute])
  const router = createRouter({
    routeTree,
    history: createMemoryHistory({ initialEntries: ['/register'] }),
  })
  await router.load()
  let result!: ReturnType<typeof render>
  await act(async () => {
    result = render(createElement(RouterProvider, { router }))
  })
  return result
}

describe('RegistrationPage', () => {
  it('RegistrationPage - renders - displays RegistrationForm', async () => {
    await renderPage()
    expect(screen.getByText('RegistrationForm')).toBeInTheDocument()
  })

  it('RegistrationPage - success - redirects to /login', async () => {
    await renderPage()
    screen.getByRole('button', { name: /trigger success/i }).click()
    expect(await screen.findByText('Login Page')).toBeInTheDocument()
  })
})
