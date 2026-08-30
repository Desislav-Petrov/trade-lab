import type { UserSettingsResponse } from './userSettings'

export interface RegisterUserRequest {
  firstName: string
  lastName: string
  address?: string
  email: string
}

export interface RegisterUserResponse {
  userId: string
}

export interface UserEmailsResponse {
  emails: string[]
}

export interface LoginRequest {
  email: string
}

export type UserStatus = 'active' | 'suspended' | 'closed'

export interface UserProfile {
  userId: string
  firstName: string
  lastName: string
  address: string | null
  email: string
  status: UserStatus
  createdAt: string // UTC ISO 8601 — convert to local only at display layer
}

export interface UserResponse extends UserProfile {
  settings: UserSettingsResponse
}

export interface Session extends UserProfile {
  settings: UserSettingsResponse
  accessToken: string
  loggedInAt: string // ISO 8601 client-side timestamp
}

export const SESSION_STORAGE_KEY = 'trade-lab-session'
