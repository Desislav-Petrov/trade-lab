import axiosInstance from '../../../shared/api/axiosInstance'
import type { RegisterUserRequest, RegisterUserResponse } from '../types/user'

export const REGISTER_USER_KEY = ['users', 'register'] as const

export async function createUser(request: RegisterUserRequest): Promise<RegisterUserResponse> {
  const response = await axiosInstance.post<RegisterUserResponse>('/v1/users', request)
  return response.data
}
