package org.dpp.tradelab.user.api

import org.dpp.tradelab.user.model.FeedType
import java.util.UUID

data class UserSettingsDto(
    val userId: UUID,
    val feedType: FeedType
)

interface UserSettingsApi {
    fun getUserSettings(userId: UUID): UserSettingsDto?
}
