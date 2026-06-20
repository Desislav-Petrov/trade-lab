import { useMutation } from '@tanstack/react-query'
import { loginUser } from '../api/userApi'
import type { LoginResponse } from '../types/user'

interface UseLoginUserOptions {
  onSuccess?: (data: LoginResponse) => void
}

export function useLoginUser({ onSuccess }: UseLoginUserOptions = {}) {
  return useMutation({
    mutationFn: loginUser,
    onSuccess: (data) => {
      onSuccess?.(data)
    },
  })
}
