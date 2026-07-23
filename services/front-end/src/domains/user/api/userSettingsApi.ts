import axiosInstance from '../../../shared/api/axiosInstance'
import type { UpdateUserSettingsRequest, UserSettingsResponse } from '../types/userSettings'

export const PATCH_USER_SETTINGS_KEY = ['users', 'settings'] as const

export async function patchUserSettings(
  userId: string,
  body: UpdateUserSettingsRequest,
): Promise<UserSettingsResponse> {
  const response = await axiosInstance.patch<UserSettingsResponse>(
    `/v1/users/${userId}/settings`,
    body,
  )
  return response.data
}
