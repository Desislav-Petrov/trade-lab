import axiosInstance from '../../../shared/api/axiosInstance'
import type {
  RegisterUserRequest,
  RegisterUserResponse,
  UserEmailsResponse,
  LoginRequest,
  UserResponse,
} from '../types/user'

export const REGISTER_USER_KEY = ['users', 'register'] as const
export const ACTIVE_USER_EMAILS_KEY = ['users', 'emails'] as const
export const GET_USER_KEY = (userId: string) => ['users', userId] as const

export async function createUser(request: RegisterUserRequest): Promise<RegisterUserResponse> {
  const response = await axiosInstance.post<RegisterUserResponse>('/v1/users', request)
  return response.data
}

export async function getActiveUserEmails(): Promise<UserEmailsResponse> {
  const response = await axiosInstance.get<UserEmailsResponse>('/v1/users/emails')
  return response.data
}

export async function loginUser(request: LoginRequest): Promise<string> {
  const response = await axiosInstance.post('/v1/users/login', request, {
    maxRedirects: 0,
    validateStatus: (s) => s === 302,
  })

  return response.headers.location as string
}

/**
 * Fetch a user profile by ID.
 *
 * @param userId   - The UUID of the user to fetch.
 * @param accessToken - Optional bearer token to use for this request.
 *   Required during the OAuth2 callback bootstrap where the session has not
 *   yet been persisted to localStorage and the Axios interceptor would
 *   therefore send the request without an Authorization header.
 */
export async function fetchUserById(
  userId: string,
  accessToken?: string,
): Promise<UserResponse> {
  const headers = accessToken ? { Authorization: `Bearer ${accessToken}` } : undefined
  const response = await axiosInstance.get<UserResponse>(`/v1/users/${userId}`, { headers })
  return response.data
}
