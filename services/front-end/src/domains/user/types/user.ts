export interface RegisterUserRequest {
  firstName: string
  lastName: string
  address: string
  email: string
}

export interface RegisterUserResponse {
  userId: string
}
