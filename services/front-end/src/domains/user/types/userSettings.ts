export type FeedType = 'SYNTHETIC' | 'REAL'

export interface UpdateUserSettingsRequest {
  feedType: FeedType
}

export interface UserSettingsResponse {
  feedType: FeedType
  updatedAt: string // UTC ISO 8601
}
