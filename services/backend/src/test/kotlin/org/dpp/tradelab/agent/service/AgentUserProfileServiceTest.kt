package org.dpp.tradelab.agent.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.config.AGENT_APP_NAME
import org.dpp.tradelab.user.api.UserLookupApi
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class AgentUserProfileServiceTest : FunSpec({
    val persistentSessionService = mock<PersistentSessionService>()
    val userLookupApi = mock<UserLookupApi>()
    val service = AgentUserProfileService(persistentSessionService, userLookupApi)
    val userId = UUID.randomUUID()

    beforeEach {
        reset(persistentSessionService, userLookupApi)
    }

    test("resolveName_cachedName_returnsCacheWithoutLookup") {
        whenever(
            persistentSessionService.getUserScopedValue(AGENT_APP_NAME, userId.toString(), "user:user_profile")
        ).thenReturn("Jane Doe")

        service.resolveName(userId) shouldBe "Jane Doe"

        verify(userLookupApi, never()).getName(any())
    }

    test("resolveName_cacheMiss_fetchesAndCachesName") {
        whenever(
            persistentSessionService.getUserScopedValue(AGENT_APP_NAME, userId.toString(), "user:user_profile")
        ).thenReturn(null)
        whenever(userLookupApi.getName(userId)).thenReturn("Jane Doe")

        service.resolveName(userId) shouldBe "Jane Doe"

        verify(persistentSessionService).updateUserScopedValue(
            AGENT_APP_NAME,
            userId.toString(),
            "user:user_profile",
            "Jane Doe"
        )
    }

    test("resolveName_nullName_returnsNullWithoutCaching") {
        whenever(
            persistentSessionService.getUserScopedValue(AGENT_APP_NAME, userId.toString(), "user:user_profile")
        ).thenReturn(null)
        whenever(userLookupApi.getName(userId)).thenReturn(null)

        service.resolveName(userId) shouldBe null

        verify(persistentSessionService, never()).updateUserScopedValue(any(), any(), any(), any())
    }

    test("resolveName_blankName_returnsNullWithoutCaching") {
        whenever(
            persistentSessionService.getUserScopedValue(AGENT_APP_NAME, userId.toString(), "user:user_profile")
        ).thenReturn(null)
        whenever(userLookupApi.getName(userId)).thenReturn("  ")

        service.resolveName(userId) shouldBe null

        verify(persistentSessionService, never()).updateUserScopedValue(any(), any(), any(), any())
    }

    test("resolveName_lookupThrows_returnsNullWithoutCaching") {
        whenever(
            persistentSessionService.getUserScopedValue(AGENT_APP_NAME, userId.toString(), "user:user_profile")
        ).thenReturn(null)
        whenever(userLookupApi.getName(userId)).thenThrow(IllegalStateException("lookup failed"))

        service.resolveName(userId) shouldBe null

        verify(persistentSessionService, never()).updateUserScopedValue(any(), any(), any(), any())
    }

    test("resolveName_stateReadThrows_returnsNullWithoutLookup") {
        whenever(
            persistentSessionService.getUserScopedValue(AGENT_APP_NAME, userId.toString(), "user:user_profile")
        ).thenThrow(IllegalStateException("state read failed"))

        service.resolveName(userId) shouldBe null

        verify(userLookupApi, never()).getName(any())
    }

    test("resolveName_stateWriteThrows_returnsNull") {
        whenever(
            persistentSessionService.getUserScopedValue(AGENT_APP_NAME, userId.toString(), "user:user_profile")
        ).thenReturn(null)
        whenever(userLookupApi.getName(userId)).thenReturn("Jane Doe")
        org.mockito.kotlin.doThrow(IllegalStateException("state write failed"))
            .whenever(persistentSessionService)
            .updateUserScopedValue(eq(AGENT_APP_NAME), eq(userId.toString()), eq("user:user_profile"), eq("Jane Doe"))

        service.resolveName(userId) shouldBe null
    }
})
