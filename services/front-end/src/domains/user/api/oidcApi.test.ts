import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { redirectToGoogleLogin } from './oidcApi'

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

  it('redirectToGoogleLogin - called - sets window.location.href to Google OAuth2 path', () => {
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

    expect(hrefAssignments).toContain('/oauth2/authorization/google')

    // Restore original location
    Object.defineProperty(window, 'location', {
      value: originalLocation,
      writable: true,
      configurable: true,
    })
  })
})
