import { describe, it, vi, afterEach } from 'vitest'
import { redirectToGoogleLogin } from './oidcApi'

describe('redirectToGoogleLogin', () => {
  afterEach(() => vi.restoreAllMocks())

  it('redirectToGoogleLogin - called - sets window.location.href to Google OAuth2 path', () => {
    // jsdom does not throw on location.href assignment
    const assignSpy = vi.spyOn(window.location, 'href', 'set')
    redirectToGoogleLogin()
    expect(assignSpy).toHaveBeenCalledWith('/oauth2/authorization/google')
  })
})
