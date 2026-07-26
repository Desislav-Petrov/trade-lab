package org.dpp.tradelab.user.service

import org.dpp.tradelab.user.model.FeedType
import org.dpp.tradelab.user.model.UserSettings
import org.dpp.tradelab.user.repository.UserSettingsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserSettingsService(
    private val userSettingsRepository: UserSettingsRepository
) {
    @Transactional
    fun createDefaultSettings(userId: UUID): UserSettings {
        val settings = UserSettings(
            id = UUID.randomUUID(),
            userId = userId,
            feedType = FeedType.getDefault()
        )
        return userSettingsRepository.save(settings)
    }
}
