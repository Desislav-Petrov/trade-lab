import { create } from 'zustand'
import type { UserProfile } from '../types/user'

interface SessionState {
  user: UserProfile | null
  loggedInAt: string | null // ISO 8601 client-side timestamp
  setSession: (user: UserProfile) => void
  clearSession: () => void
}

export const useSessionStore = create<SessionState>((set) => ({
  user: null,
  loggedInAt: null,
  setSession: (user) => set({ user, loggedInAt: new Date().toISOString() }),
  clearSession: () => set({ user: null, loggedInAt: null }),
}))
