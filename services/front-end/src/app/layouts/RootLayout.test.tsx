import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { act } from 'react'
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
} from '@tanstack/react-router'
import { RootLayout } from './RootLayout'

async function renderLayout(initialPath = '/') {
  const rootRoute = createRootRoute({ component: RootLayout })
  const catchAllRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '$',
    component: () => <div>Page content</div>,
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

describe('RootLayout', () => {
  it('RootLayout - renders - displays topbar', async () => {
    await renderLayout()

    expect(screen.getByRole('banner')).toBeInTheDocument()
  })

  it('RootLayout - renders - displays sidebar navigation', async () => {
    await renderLayout()

    expect(screen.getByRole('navigation', { name: /main navigation/i })).toBeInTheDocument()
  })

  it('RootLayout - renders - renders child route content via Outlet', async () => {
    await renderLayout()

    expect(screen.getByText('Page content')).toBeInTheDocument()
  })

  it('RootLayout - renders - main content region exists', async () => {
    await renderLayout()

    expect(screen.getByRole('main')).toBeInTheDocument()
  })
})
