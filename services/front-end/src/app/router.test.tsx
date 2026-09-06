import { afterEach, describe, expect, it, vi } from 'vitest'

async function getRoutePaths(enableNoAuth?: string) {
  vi.resetModules()
  if (enableNoAuth === undefined) {
    vi.unstubAllEnvs()
  } else {
    vi.stubEnv('VITE_ENABLE_NO_AUTH', enableNoAuth)
  }

  const { routeTree } = await import('./router')

  return routeTree.children?.map((route) => route.fullPath) ?? []
}

describe('router', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.resetModules()
  })

  it('router - VITE_ENABLE_NO_AUTH=true - includes no-auth routes', async () => {
    const routePaths = await getRoutePaths('true')

    expect(routePaths).toContain('/login')
    expect(routePaths).toContain('/register')
  })

  it('router - VITE_ENABLE_NO_AUTH=false - excludes no-auth routes', async () => {
    const routePaths = await getRoutePaths('false')

    expect(routePaths).not.toContain('/login')
    expect(routePaths).not.toContain('/register')
  })

  it('router - VITE_ENABLE_NO_AUTH unset - excludes no-auth routes', async () => {
    const routePaths = await getRoutePaths()

    expect(routePaths).not.toContain('/login')
    expect(routePaths).not.toContain('/register')
  })
})
