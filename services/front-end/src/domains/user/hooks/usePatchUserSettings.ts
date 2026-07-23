import { useMutation } from '@tanstack/react-query'
import { patchUserSettings } from '../api/userSettingsApi'
import { useSessionStore } from './useSessionStore'
import type { UpdateUserSettingsRequest, UserSettingsResponse } from '../types/userSettings'

export function usePatchUserSettings(userId: string) {
  const updateSettings = useSessionStore((s) => s.updateSettings)

  const { mutate, isPending, isError, isSuccess, error } = useMutation({
    mutationFn: (body: UpdateUserSettingsRequest) => patchUserSettings(userId, body),
    onSuccess: (data: UserSettingsResponse) => {
      updateSettings(data)
    },
  })

  return { mutate, isPending, isError, isSuccess, error }
}
