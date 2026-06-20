import { useMutation } from '@tanstack/react-query'
import { createUser } from '../api/userApi'
import type { RegisterUserRequest } from '../types/user'

interface UseRegisterUserOptions {
  onSuccess?: () => void
}

export function useRegisterUser({ onSuccess }: UseRegisterUserOptions = {}) {
  return useMutation({
    mutationFn: (request: RegisterUserRequest) => createUser(request),
    onSuccess: () => {
      onSuccess?.()
    },
  })
}
