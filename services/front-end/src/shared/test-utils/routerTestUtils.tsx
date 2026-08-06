import { type ReactNode } from 'react'
import { render } from '@testing-library/react'
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
} from '@tanstack/react-router'

interface RouteConfig {
  path: string
  component: () => ReactNode
}

export interface RenderWithRouterOptions {
  /** The route configs to register */
  routes: RouteConfig[]
  /** Initial URL including pathname and optional search */
  initialUrl?: string
  /** Optional router-level state to set on the initial history entry */
  initialState?: Record<string, unknown>
}

/**
 * Renders components inside a TanStack Router instance backed by an in-memory
 * history. Awaits router.load() before rendering so routes are resolved.
 * Use this instead of react-router-dom's MemoryRouter/Routes/Route in tests.
 */
export async function renderWithRouter({ routes, initialUrl = '/', initialState }: RenderWithRouterOptions) {
  const rootRoute = createRootRoute()

  const childRoutes = routes.map(({ path, component }) =>
    createRoute({
      getParentRoute: () => rootRoute,
      path,
      component,
    }),
  )

  const routeTree = rootRoute.addChildren(childRoutes)

  const memoryHistory = createMemoryHistory({
    initialEntries: [initialUrl],
  })

  if (initialState) {
    memoryHistory.push(initialUrl, initialState)
  }

  const router = createRouter({ routeTree, history: memoryHistory })
  await router.load()

  return render(<RouterProvider router={router} />)
}
