package org.dpp.tradelab.marketdata.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.marketdata.model.MarketDataSnapshot
import org.dpp.tradelab.common.service.JwtService
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import java.math.BigDecimal
import java.net.URI
import java.time.Instant
import java.util.UUID

class MarketDataWebSocketHandlerTest : FunSpec({

    val marketDataFeedService = mock<MarketDataFeedService>()
    val jwtService = mock<JwtService>()
    val handler = MarketDataWebSocketHandler(marketDataFeedService, jwtService)

    val userId = UUID.randomUUID()
    val token = "valid.jwt.token"

    beforeEach {
        reset(marketDataFeedService, jwtService)
    }

    val aaplSnapshot = MarketDataSnapshot(
        ticker = "AAPL",
        companyName = "Apple Inc.",
        currentPrice = BigDecimal("150.000"),
        open = BigDecimal("148.000"),
        dayLow = BigDecimal("147.500"),
        dayHigh = BigDecimal("155.000"),
        fiftyTwoWeekHigh = BigDecimal("200.000"),
        updatedAt = Instant.now()
    )

    fun mockSession(
        uri: URI = URI("ws://localhost/api/v1/market-data/feed"),
        protocols: List<String> = emptyList()
    ): WebSocketSession {
        val session = mock<WebSocketSession>()
        val headers = HttpHeaders().apply {
            protocols.forEach { add("Sec-WebSocket-Protocol", it) }
        }
        whenever(session.uri).thenReturn(uri)
        whenever(session.handshakeHeaders).thenReturn(headers)
        whenever(session.attributes).thenReturn(mutableMapOf())
        whenever(session.isOpen).thenReturn(true)
        return session
    }

    fun assertClosedWith(session: WebSocketSession, expectedCode: Int) {
        val statusCaptor = argumentCaptor<CloseStatus>()
        verify(session).close(statusCaptor.capture())
        statusCaptor.firstValue.code shouldBe expectedCode
    }

    // ── afterConnectionEstablished ──────────────────────────────────────────

    test("afterConnectionEstablished_missingProtocol_closesWithStatus4401") {
        val session = mockSession()

        handler.afterConnectionEstablished(session)

        assertClosedWith(session, 4401)
        verify(jwtService, never()).validateAndExtractUserId(any())
        verify(marketDataFeedService, never()).registerSession(any(), any())
    }

    test("afterConnectionEstablished_protocolWithoutBearer_closesWithStatus4401") {
        val session = mockSession(protocols = listOf("not-bearer", token))

        handler.afterConnectionEstablished(session)

        assertClosedWith(session, 4401)
        verify(jwtService, never()).validateAndExtractUserId(any())
        verify(marketDataFeedService, never()).registerSession(any(), any())
    }

    test("afterConnectionEstablished_blankToken_closesWithStatus4401") {
        val session = mockSession(protocols = listOf("bearer", "  "))

        handler.afterConnectionEstablished(session)

        assertClosedWith(session, 4401)
        verify(jwtService, never()).validateAndExtractUserId(any())
        verify(marketDataFeedService, never()).registerSession(any(), any())
    }

    test("afterConnectionEstablished_invalidToken_closesWithStatus4401") {
        val session = mockSession(protocols = listOf("bearer, invalid.jwt"))
        whenever(jwtService.validateAndExtractUserId("invalid.jwt"))
            .thenThrow(IllegalArgumentException("invalid token"))

        handler.afterConnectionEstablished(session)

        assertClosedWith(session, 4401)
        verify(marketDataFeedService, never()).registerSession(any(), any())
    }

    test("afterConnectionEstablished_expiredToken_closesWithStatus4401") {
        val session = mockSession(protocols = listOf("bearer", "expired.jwt"))
        whenever(jwtService.validateAndExtractUserId("expired.jwt"))
            .thenThrow(IllegalStateException("expired token"))

        handler.afterConnectionEstablished(session)

        assertClosedWith(session, 4401)
        verify(marketDataFeedService, never()).registerSession(any(), any())
    }

    test("afterConnectionEstablished_validBearerToken_registersAndSendsSnapshot") {
        val queryUserId = UUID.randomUUID()
        val session = mockSession(
            uri = URI("ws://localhost/api/v1/market-data/feed?userId=$queryUserId"),
            protocols = listOf("bearer", "  $token  ")
        )
        whenever(jwtService.validateAndExtractUserId(token)).thenReturn(userId)
        whenever(marketDataFeedService.getSnapshotForUser(userId)).thenReturn(listOf(aaplSnapshot))

        handler.afterConnectionEstablished(session)

        verify(jwtService).validateAndExtractUserId(token)
        verify(marketDataFeedService).registerSession(userId, session)
        verify(marketDataFeedService).sendSnapshot(session, listOf(aaplSnapshot))
        session.attributes["userId"] shouldBe userId
    }

    test("afterConnectionEstablished_snapshotFailure_closesWithStatus4500") {
        val session = mockSession(protocols = listOf("bearer", token))
        whenever(jwtService.validateAndExtractUserId(token)).thenReturn(userId)
        whenever(marketDataFeedService.getSnapshotForUser(userId))
            .thenThrow(RuntimeException("unexpected failure"))

        handler.afterConnectionEstablished(session)

        assertClosedWith(session, 4500)
    }

    // ── afterConnectionClosed ─────────────────────────────────────────────

    test("afterConnectionClosed_userIdInAttributes_removesSession") {
        val queryUserId = UUID.randomUUID()
        val session = mockSession(uri = URI("ws://localhost/feed?userId=$queryUserId"))
        session.attributes["userId"] = userId

        handler.afterConnectionClosed(session, CloseStatus.NORMAL)

        verify(marketDataFeedService).removeSession(userId)
    }

    test("afterConnectionClosed_missingUserIdAttribute_doesNotUseQueryString") {
        val session = mockSession(uri = URI("ws://localhost/feed?userId=$userId"))

        handler.afterConnectionClosed(session, CloseStatus.NORMAL)

        verify(marketDataFeedService, never()).removeSession(any())
    }
})
