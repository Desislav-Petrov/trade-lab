import { create } from 'zustand'
import type { Session, UserResponse } from '../types/user'
import { SESSION_STORAGE_KEY } from '../types/user'
import type { UserSettingsResponse } from '../types/userSettings'

function decodeJwtExp(token: string): number | null {
  try {
    const payload = token.split('.')[1]
    const decoded = JSON.parse(atob(payload))
    return typeof decoded.exp === 'number' ? decoded.exp : null
  } catch {
    return null
  }
}

function isTokenExpired(token: string): boolean {
  const exp = decodeJwtExp(token)
  if (exp === null) return true
  return Date.now() / 1000 > exp
}

function loadSessionFromStorage(): Session | null {
  try {
    const raw = localStorage.getItem(SESSION_STORAGE_KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Session
    if (!session.accessToken || isTokenExpired(session.accessToken)) return null
    return session
  } catch {
    return null
  }
}

interface SessionState {
  session: Session | null
  establishSession: (response: UserResponse, accessToken: string) => void
  restoreSession: () => void
  clearSession: () => void
  updateSettings: (settings: UserSettingsResponse) => void
  // Legacy setSession kept for backward compatibility with existing components
  setSession: (response: UserResponse) => void
}

export const useSessionStore = create<SessionState>((set, get) => ({
  session: null,

  establishSession: (response: UserResponse, accessToken: string) => {
    const { settings, ...profile } = response
    const newSession: Session = {
      ...profile,
      settings,
      accessToken,
      loggedInAt: new Date().toISOString(),
    }
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(newSession))
    set({ session: newSession })
  },

  restoreSession: () => {
    const restored = loadSessionFromStorage()
    if (restored) {
      set({ session: restored })
    } else {
      localStorage.removeItem(SESSION_STORAGE_KEY)
    }
  },

  clearSession: () => {
    localStorage.removeItem(SESSION_STORAGE_KEY)
    set({ session: null })
  },

  updateSettings: (settings: UserSettingsResponse) => {
    const current = get().session
    if (!current) return
    const updated = { ...current, settings }
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(updated))
    set({ session: updated })
  },

  // Legacy — kept for backward compat. Sets session without an accessToken.
  setSession: (response: UserResponse) => {
    const { settings, ...profile } = response
    // Preserve existing token if present, otherwise use empty string
    const existingToken = get().session?.accessToken ?? ''
    const newSession: Session = {
      ...profile,
      settings,
      accessToken: existingToken,
      loggedInAt: new Date().toISOString(),
    }
    set({ session: newSession })
  },
}))
