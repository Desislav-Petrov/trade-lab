import type { AxiosError } from 'axios'

/**
 * Extracts a user-facing error message from an unknown error, following the
 * backend's error response contract (`{ error: string }`).
 *
 * Consolidates the ad-hoc `axiosError?.response?.data?.error` parsing that was
 * previously duplicated across pages.
 */
export function getApiErrorMessage(
  error: unknown,
  fallback: string = 'Something went wrong. Please try again.',
): string {
  const axiosError = error as AxiosError<{ error?: string }>
  return axiosError?.response?.data?.error ?? fallback
}
