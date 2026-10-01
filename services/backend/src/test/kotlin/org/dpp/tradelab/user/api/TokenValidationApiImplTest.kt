package org.dpp.tradelab.user.api

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.user.exception.InvalidTokenException
import org.dpp.tradelab.user.service.JwtService
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class TokenValidationApiImplTest : FunSpec({

    val jwtService = mock<JwtService>()
    val tokenValidationApi = TokenValidationApiImpl(jwtService)

    test("validateAndExtractUserId_validToken_returnsUserId") {
        val token = "valid-token"
        val userId = UUID.randomUUID()
        whenever(jwtService.validateAndExtractUserId(token)).thenReturn(userId)

        val result = tokenValidationApi.validateAndExtractUserId(token)

        result shouldBe userId
        verify(jwtService).validateAndExtractUserId(token)
    }

    test("validateAndExtractUserId_invalidToken_propagatesException") {
        val token = "invalid-token"
        val exception = InvalidTokenException("Token validation failed")
        whenever(jwtService.validateAndExtractUserId(token)).thenThrow(exception)

        val thrown = shouldThrow<InvalidTokenException> {
            tokenValidationApi.validateAndExtractUserId(token)
        }

        thrown shouldBe exception
    }
})
