import { useMutation } from '@tanstack/react-query'
import { loginUser } from '../api/userApi'

interface UseLoginUserOptions {
  onSuccess?: (redirectUrl: string) => void
}

export function useLoginUser({ onSuccess }: UseLoginUserOptions = {}) {
  return useMutation({
    mutationFn: loginUser,
    onSuccess: (token) => {
      window.location.assign(`/auth/callback?token=${token}`)
      onSuccess?.(token)
    },
  })
}
