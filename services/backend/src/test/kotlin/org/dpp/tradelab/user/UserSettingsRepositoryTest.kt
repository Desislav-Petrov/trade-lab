package org.dpp.tradelab.user

import org.dpp.tradelab.user.model.FeedType
import org.dpp.tradelab.user.model.UserSettings
import org.dpp.tradelab.user.repository.UserSettingsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class UserSettingsRepositoryTest @Autowired constructor(
    private val userSettingsRepository: UserSettingsRepository
) {

    @Test
    fun findByUserId_settingsExist_returnsSettings() {
        val userId = UUID.randomUUID()
        userSettingsRepository.save(
            UserSettings(id = UUID.randomUUID(), userId = userId, feedType = FeedType.SYNTHETIC)
        )

        val result = userSettingsRepository.findByUserId(userId)

        assertEquals(userId, result?.userId)
        assertEquals(FeedType.SYNTHETIC, result?.feedType)
    }

    @Test
    fun findByUserId_settingsDoNotExist_returnsNull() {
        val result = userSettingsRepository.findByUserId(UUID.randomUUID())
        assertNull(result)
    }
}
