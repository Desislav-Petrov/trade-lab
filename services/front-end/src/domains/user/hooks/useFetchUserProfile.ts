import { useMutation } from '@tanstack/react-query'
import { fetchUserById } from '../api/userApi'
import { useSessionStore } from './useSessionStore'

interface UseFetchUserProfileOptions {
  onSuccess?: () => void
  onError?: () => void
}

export function useFetchUserProfile({ onSuccess, onError }: UseFetchUserProfileOptions = {}) {
  const setSession = useSessionStore((s) => s.setSession)

  return useMutation({
    mutationFn: (userId: string) => fetchUserById(userId),
    onSuccess: (profile) => {
      setSession(profile)
      onSuccess?.()
    },
    onError: () => {
      onError?.()
    },
  })
}
