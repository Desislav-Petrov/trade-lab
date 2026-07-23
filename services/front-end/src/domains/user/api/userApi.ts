import axiosInstance from '../../../shared/api/axiosInstance'
import type {
  RegisterUserRequest,
  RegisterUserResponse,
  UserEmailsResponse,
  LoginRequest,
  LoginResponse,
  UserResponse,
} from '../types/user'

export const REGISTER_USER_KEY = ['users', 'register'] as const
export const ACTIVE_USER_EMAILS_KEY = ['users', 'emails'] as const
export const LOGIN_USER_KEY = ['users', 'login'] as const
export const GET_USER_KEY = (userId: string) => ['users', userId] as const

export async function createUser(request: RegisterUserRequest): Promise<RegisterUserResponse> {
  const response = await axiosInstance.post<RegisterUserResponse>('/v1/users', request)
  return response.data
}

export async function getActiveUserEmails(): Promise<UserEmailsResponse> {
  const response = await axiosInstance.get<UserEmailsResponse>('/v1/users/emails')
  return response.data
}

export async function loginUser(request: LoginRequest): Promise<LoginResponse> {
  const response = await axiosInstance.post<LoginResponse>('/v1/users/login', request)
  return response.data
}

export async function fetchUserById(userId: string): Promise<UserResponse> {
  const response = await axiosInstance.get<UserResponse>(`/v1/users/${userId}`)
  return response.data
}
