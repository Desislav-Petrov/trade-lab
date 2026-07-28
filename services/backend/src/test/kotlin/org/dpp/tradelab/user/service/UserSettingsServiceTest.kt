package org.dpp.tradelab.user.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.dpp.tradelab.user.model.FeedType
import org.dpp.tradelab.user.model.UserSettings
import org.dpp.tradelab.user.repository.UserSettingsRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class UserSettingsServiceTest : FunSpec() {

    private val userSettingsRepository = mock<UserSettingsRepository>()
    private val userSettingsService = UserSettingsService(userSettingsRepository)

    init {
        test("createDefaultSettings_createsSettingsWithDefaultFeedType") {
            val userId = UUID.randomUUID()
            val savedSettings = UserSettings(
                id = UUID.randomUUID(),
                userId = userId,
                feedType = FeedType.SYNTHETIC
            )

            whenever(userSettingsRepository.save(any())).thenReturn(savedSettings)

            val result = userSettingsService.createDefaultSettings(userId)

            result shouldNotBe null
            result.userId shouldBe userId
            result.feedType shouldBe FeedType.SYNTHETIC
            verify(userSettingsRepository, times(1)).save(any())
        }

        test("createDefaultSettings_usesDefaultFeedTypeFromEnum") {
            val userId = UUID.randomUUID()
            val defaultFeedType = FeedType.getDefault()

            val captor = argumentCaptor<UserSettings>()
            whenever(userSettingsRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

            userSettingsService.createDefaultSettings(userId)

            val savedSettings = captor.firstValue
            savedSettings.feedType shouldBe defaultFeedType
            savedSettings.feedType shouldBe FeedType.SYNTHETIC
        }

        test("createDefaultSettings_generatesNewIdForSettings") {
            val userId = UUID.randomUUID()
            val settingsCaptor = argumentCaptor<UserSettings>()
            
            whenever(userSettingsRepository.save(settingsCaptor.capture())).thenAnswer { it.arguments[0] }

            val result1 = userSettingsService.createDefaultSettings(userId)
            val result2 = userSettingsService.createDefaultSettings(userId)

            result1.id shouldNotBe result2.id
        }
    }
}
