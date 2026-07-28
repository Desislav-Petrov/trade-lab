/**
 * CLI-2: OIDC API client
 * Triggers the backend-driven OAuth2 dance by redirecting to Spring Security's
 * OAuth2 authorisation endpoint for Google.
 */
export function redirectToGoogleLogin(): void {
  window.location.href = '/oauth2/authorization/google'
}
