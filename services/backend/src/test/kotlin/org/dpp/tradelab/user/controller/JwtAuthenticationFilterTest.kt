package org.dpp.tradelab.user.controller

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse
import org.dpp.tradelab.common.exception.InvalidTokenException
import org.dpp.tradelab.common.service.JwtService
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class JwtAuthenticationFilterTest : FunSpec() {

    private val jwtService = mock<JwtService>()
    private val filter = JwtAuthenticationFilter(jwtService)

    init {
        beforeTest {
            reset(jwtService)
            SecurityContextHolder.clearContext()
        }
        afterTest { SecurityContextHolder.clearContext() }

        test("doFilterInternal_validToken_setsAuthenticationAndInvokesChainExactlyOnce") {
            val userId = UUID.randomUUID()
            whenever(jwtService.validateAndExtractUserId("good-token")).thenReturn(userId)
            val request = MockHttpServletRequest("GET", "/api/v1/accounts")
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer good-token")
            val response = MockHttpServletResponse()
            val chain = mock<FilterChain>()

            filter.doFilter(request, response, chain)

            verify(chain, times(1)).doFilter(request, response)
            SecurityContextHolder.getContext().authentication?.principal shouldBe userId
            response.status shouldBe HttpServletResponse.SC_OK
        }

        test("doFilterInternal_invalidToken_returns401AndDoesNotInvokeChain") {
            whenever(jwtService.validateAndExtractUserId(any()))
                .thenThrow(InvalidTokenException("bad token"))
            val request = MockHttpServletRequest("GET", "/api/v1/accounts")
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
            val response = MockHttpServletResponse()
            val chain = mock<FilterChain>()

            filter.doFilter(request, response, chain)

            verify(chain, never()).doFilter(any(), any())
            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            response.contentType!! shouldContain "application/json"
            response.contentAsString shouldContain "Unauthorized"
            SecurityContextHolder.getContext().authentication.shouldBeNull()
        }

        test("doFilterInternal_noAuthHeader_invokesChainOnceWithoutAuthentication") {
            val request = MockHttpServletRequest("GET", "/api/v1/users/register")
            val response = MockHttpServletResponse()
            val chain = mock<FilterChain>()

            filter.doFilter(request, response, chain)

            verify(chain, times(1)).doFilter(request, response)
            SecurityContextHolder.getContext().authentication.shouldBeNull()
            response.status shouldBe HttpServletResponse.SC_OK
        }

        test("doFilterInternal_nonBearerHeader_invokesChainOnceWithoutValidation") {
            val request = MockHttpServletRequest("GET", "/api/v1/accounts")
            request.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
            val response = MockHttpServletResponse()
            val chain = mock<FilterChain>()

            filter.doFilter(request, response, chain)

            verify(chain, times(1)).doFilter(request, response)
            verify(jwtService, never()).validateAndExtractUserId(any())
            SecurityContextHolder.getContext().authentication.shouldBeNull()
        }

        // Regression for issue #233: a downstream exception must NOT cause the filter
        // chain to be dispatched a second time.
        test("doFilterInternal_downstreamThrows_doesNotReinvokeChainAndPropagates") {
            val userId = UUID.randomUUID()
            whenever(jwtService.validateAndExtractUserId("good-token")).thenReturn(userId)
            val request = MockHttpServletRequest("GET", "/api/v1/accounts")
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer good-token")
            val response = MockHttpServletResponse()
            val chain = mock<FilterChain>()
            doThrow(RuntimeException("boom")).whenever(chain).doFilter(any(), any())

            shouldThrow<RuntimeException> {
                filter.doFilter(request, response, chain)
            }

            verify(chain, times(1)).doFilter(request, response)
        }
    }
}
