/**
 * CLI-2: OIDC API client
 * Triggers the backend-driven OAuth2 dance by redirecting to Spring Security's
 * OAuth2 authorisation endpoint for Google.
 *
 * Uses an absolute URL so the browser navigates directly to the backend
 * (localhost:8080 in dev). A relative path would resolve to the Vite dev
 * server origin (localhost:5173), which cannot handle the OAuth2 dance.
 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export function redirectToGoogleLogin(): void {
  window.location.href = `${API_BASE_URL}/oauth2/authorization/google`
}

export function redirectToGithubLogin(): void {
  window.location.href = `${API_BASE_URL}/oauth2/authorization/github`
}
