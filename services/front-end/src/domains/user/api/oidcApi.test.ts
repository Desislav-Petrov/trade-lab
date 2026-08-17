import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { redirectToGoogleLogin, redirectToGithubLogin } from './oidcApi'

describe('redirectToGoogleLogin', () => {
  let originalLocation: Location

  beforeEach(() => {
    originalLocation = window.location
    // jsdom doesn't allow redefining window.location directly,
    // so we store the original and track changes
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('redirectToGoogleLogin - called - sets window.location.href to absolute backend OAuth2 URL', () => {
    // Track href assignments by monitoring window property changes
    const hrefAssignments: string[] = []

    // Use Object.defineProperty to intercept href writes
    Object.defineProperty(window, 'location', {
      value: {
        ...originalLocation,
        href: '',
      },
      writable: true,
      configurable: true,
    })

    // Create a setter that tracks assignments
    Object.defineProperty(window.location, 'href', {
      set: (value: string) => {
        hrefAssignments.push(value)
      },
      get: () => originalLocation.href,
      configurable: true,
    })

    redirectToGoogleLogin()

    // Must be an absolute URL pointing to the backend — not a relative path
    // that would resolve to the Vite dev server (localhost:5173).
    // VITE_API_BASE_URL defaults to http://localhost:8080 in tests.
    expect(hrefAssignments[0]).toMatch(/^http:\/\/.+\/oauth2\/authorization\/google$/)

    // Restore original location
    Object.defineProperty(window, 'location', {
      value: originalLocation,
      writable: true,
      configurable: true,
    })
  })
})

describe('redirectToGithubLogin', () => {
  let originalLocation: Location

  beforeEach(() => {
    originalLocation = window.location
  })

  afterEach(() => {
    vi.restoreAllMocks()
    Object.defineProperty(window, 'location', {
      value: originalLocation,
      writable: true,
      configurable: true,
    })
  })

  it('redirectToGithubLogin - called - sets window.location.href to http://localhost:8080/oauth2/authorization/github', () => {
    const hrefAssignments: string[] = []

    Object.defineProperty(window, 'location', {
      value: { ...originalLocation, href: '' },
      writable: true,
      configurable: true,
    })

    Object.defineProperty(window.location, 'href', {
      set: (value: string) => { hrefAssignments.push(value) },
      get: () => originalLocation.href,
      configurable: true,
    })

    redirectToGithubLogin()

    expect(hrefAssignments[0]).toBe('http://localhost:8080/oauth2/authorization/github')
  })
})

