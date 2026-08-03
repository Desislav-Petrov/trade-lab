import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { patchUserSettings } from '../api/userSettingsApi'
import { useSessionStore } from './useSessionStore'
import type { UpdateUserSettingsRequest, UserSettingsResponse } from '../types/userSettings'

export function usePatchUserSettings(userId: string) {
  const updateSettings = useSessionStore((s) => s.updateSettings)
  const navigate = useNavigate()

  const { mutate, isPending, isError, isSuccess, error } = useMutation({
    mutationFn: (body: UpdateUserSettingsRequest) => patchUserSettings(userId, body),
    onSuccess: (data: UserSettingsResponse) => {
      updateSettings(data)
    },
    onError: (err: unknown) => {
      if (isAxiosError(err) && err.response?.status === 401) {
        navigate('/login', { replace: true })
      }
    },
  })

  const errorStatus: number | null =
    isAxiosError(error) && error.response?.status != null ? error.response.status : null

  return { mutate, isPending, isError, isSuccess, error, errorStatus }
}
