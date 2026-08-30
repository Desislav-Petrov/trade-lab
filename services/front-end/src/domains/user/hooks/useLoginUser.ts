import { useMutation } from '@tanstack/react-query'
import { loginUser } from '../api/userApi'

interface UseLoginUserOptions {
  onSuccess?: (redirectUrl: string) => void
}

export function useLoginUser({ onSuccess }: UseLoginUserOptions = {}) {
  return useMutation({
    mutationFn: loginUser,
    onSuccess: (redirectUrl) => {
      window.location.assign(redirectUrl)
      onSuccess?.(redirectUrl)
    },
  })
}
