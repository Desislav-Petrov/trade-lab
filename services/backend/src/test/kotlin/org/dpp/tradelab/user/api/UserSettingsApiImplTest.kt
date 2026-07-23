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

    test("getAllUserSettings_returnsAllRows") {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val settings = listOf(
            UserSettings(id = UUID.randomUUID(), userId = id1, feedType = FeedType.SYNTHETIC),
            UserSettings(id = UUID.randomUUID(), userId = id2, feedType = FeedType.REAL)
        )
        whenever(userSettingsRepository.findAllBy()).thenReturn(settings)

        val result = userSettingsApiImpl.getAllUserSettings()

        result.size shouldBe 2
        result[0].userId shouldBe id1
        result[0].feedType shouldBe FeedType.SYNTHETIC
        result[1].userId shouldBe id2
        result[1].feedType shouldBe FeedType.REAL
    }

    test("getAllUserSettings_emptyRepository_returnsEmptyList") {
        whenever(userSettingsRepository.findAllBy()).thenReturn(emptyList())

        val result = userSettingsApiImpl.getAllUserSettings()

        result shouldBe emptyList()
    }
})
