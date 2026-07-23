package org.dpp.tradelab.user.api

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.user.model.FeedType
import org.dpp.tradelab.user.model.UserSettings
import org.dpp.tradelab.user.repository.UserSettingsRepository
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class UserSettingsApiImplTest : FunSpec({

    val userSettingsRepository = mock<UserSettingsRepository>()
    val userSettingsApiImpl = UserSettingsApiImpl(userSettingsRepository)

    test("getUserSettings_settingsExist_returnsDto") {
        val userId = UUID.randomUUID()
        val settings = UserSettings(id = UUID.randomUUID(), userId = userId, feedType = FeedType.REAL)
        whenever(userSettingsRepository.findByUserId(userId)).thenReturn(settings)

        val result = userSettingsApiImpl.getUserSettings(userId)

        result shouldBe UserSettingsDto(userId = userId, feedType = FeedType.REAL)
    }

    test("getUserSettings_settingsDoNotExist_returnsNull") {
        val userId = UUID.randomUUID()
        whenever(userSettingsRepository.findByUserId(userId)).thenReturn(null)

        val result = userSettingsApiImpl.getUserSettings(userId)

        result shouldBe null
    }
})
