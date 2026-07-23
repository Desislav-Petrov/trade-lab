package org.dpp.tradelab.user.api

import org.dpp.tradelab.user.repository.UserSettingsRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class UserSettingsApiImpl(
    private val userSettingsRepository: UserSettingsRepository
) : UserSettingsApi {

    override fun getUserSettings(userId: UUID): UserSettingsDto? =
        userSettingsRepository.findByUserId(userId)?.let { settings ->
            UserSettingsDto(userId = settings.userId, feedType = settings.feedType)
        }
}
