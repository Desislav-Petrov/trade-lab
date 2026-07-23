package org.dpp.tradelab.user.api

import org.dpp.tradelab.user.repository.UserSettingsRepository
import org.springframework.stereotype.Component

@Component
class UserSettingsApiImpl(
    private val userSettingsRepository: UserSettingsRepository
) : UserSettingsApi {

    override fun getAllUserSettings(): List<UserSettingsDto> =
        userSettingsRepository.findAllBy().map { settings ->
            UserSettingsDto(userId = settings.userId, feedType = settings.feedType)
        }
}
