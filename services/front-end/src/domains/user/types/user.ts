export interface RegisterUserRequest {
  firstName: string
  lastName: string
  address: string
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

export interface LoginResponse {
  userId: string
  email: string
}
