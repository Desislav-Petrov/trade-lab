import { create } from 'zustand'
import type { UserProfile, UserResponse } from '../types/user'
import type { UserSettingsResponse } from '../types/userSettings'

interface SessionState {
  user: UserProfile | null
  settings: UserSettingsResponse | null
  loggedInAt: string | null // ISO 8601 client-side timestamp
  setSession: (response: UserResponse) => void
  clearSession: () => void
  updateSettings: (settings: UserSettingsResponse) => void
}

export const useSessionStore = create<SessionState>((set) => ({
  user: null,
  settings: null,
  loggedInAt: null,
  setSession: (response) => {
    const { settings, ...profile } = response
    set({ user: profile, settings, loggedInAt: new Date().toISOString() })
  },
  clearSession: () => set({ user: null, settings: null, loggedInAt: null }),
  updateSettings: (settings) => set({ settings }),
}))
