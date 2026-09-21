import type { Session } from '../../domains/user/types/user'
import { SESSION_STORAGE_KEY } from '../../domains/user/types/user'

export function getSessionAuthorizationHeader(): string | undefined {
  try {
    const raw = localStorage.getItem(SESSION_STORAGE_KEY)
    if (!raw) return undefined

    const session = JSON.parse(raw) as Partial<Session>
    if (typeof session.accessToken !== 'string' || session.accessToken.length === 0) {
      return undefined
    }

    return 'Bearer ' + session.accessToken
  } catch {
    return undefined
  }
}
