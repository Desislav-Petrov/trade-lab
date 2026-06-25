package org.dpp.tradelab.marketdata.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.marketdata.model.MarketDataSnapshot
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.math.BigDecimal
import java.net.URI
import java.time.Instant
import java.util.UUID

class MarketDataWebSocketHandlerTest : FunSpec({

    val marketDataFeedService = mock<MarketDataFeedService>()
    val handler = MarketDataWebSocketHandler(marketDataFeedService)

    val userId = UUID.randomUUID()

    beforeEach {
        reset(marketDataFeedService)
    }

    val aaplSnapshot = MarketDataSnapshot(
        ticker = "AAPL",
        companyName = "Apple Inc.",
        currentPrice = BigDecimal("150.000"),
        open = BigDecimal("148.000"),
        dayLow = BigDecimal("147.500"),
        fiftyTwoWeekHigh = BigDecimal("200.000"),
        updatedAt = Instant.now()
    )

    fun mockSession(uri: URI, open: Boolean = true): WebSocketSession {
        val session = mock<WebSocketSession>()
        whenever(session.uri).thenReturn(uri)
        whenever(session.isOpen).thenReturn(open)
        return session
    }

    // ── afterConnectionEstablished ──────────────────────────────────────────

    test("afterConnectionEstablished_missingUserId_closesWithStatus4401") {
        val session = mockSession(URI("ws://localhost/api/v1/market-data/feed"))

        handler.afterConnectionEstablished(session)

        val statusCaptor = argumentCaptor<CloseStatus>()
        verify(session).close(statusCaptor.capture())
        statusCaptor.firstValue.code shouldBe 4401
        verify(marketDataFeedService, never()).registerSession(any(), any())
    }

    test("afterConnectionEstablished_blankUserId_closesWithStatus4401") {
        val session = mockSession(URI("ws://localhost/api/v1/market-data/feed?userId="))

        handler.afterConnectionEstablished(session)

        val statusCaptor = argumentCaptor<CloseStatus>()
        verify(session).close(statusCaptor.capture())
        statusCaptor.firstValue.code shouldBe 4401
        verify(marketDataFeedService, never()).registerSession(any(), any())
    }

    test("afterConnectionEstablished_invalidUUID_closesWithStatus4401") {
        val session = mockSession(URI("ws://localhost/api/v1/market-data/feed?userId=not-a-valid-uuid"))

        handler.afterConnectionEstablished(session)

        val statusCaptor = argumentCaptor<CloseStatus>()
        verify(session).close(statusCaptor.capture())
        statusCaptor.firstValue.code shouldBe 4401
        verify(marketDataFeedService, never()).registerSession(any(), any())
    }

    test("afterConnectionEstablished_validUserId_registersSessionAndSendsSnapshot") {
        val session = mockSession(URI("ws://localhost/api/v1/market-data/feed?userId=$userId"))
        whenever(marketDataFeedService.getSnapshotForUser(userId)).thenReturn(listOf(aaplSnapshot))

        handler.afterConnectionEstablished(session)

        verify(marketDataFeedService).registerSession(userId, session)
        verify(marketDataFeedService).sendSnapshot(session, listOf(aaplSnapshot))
    }

    test("afterConnectionEstablished_exceptionDuringSnapshot_closesWithStatus4500") {
        val session = mockSession(URI("ws://localhost/api/v1/market-data/feed?userId=$userId"))
        whenever(marketDataFeedService.getSnapshotForUser(userId))
            .thenThrow(RuntimeException("unexpected failure"))

        handler.afterConnectionEstablished(session)

        val statusCaptor = argumentCaptor<CloseStatus>()
        verify(session).close(statusCaptor.capture())
        statusCaptor.firstValue.code shouldBe 4500
    }

    // ── afterConnectionClosed ─────────────────────────────────────────────

    test("afterConnectionClosed_validUserId_removesSession") {
        val session = mockSession(URI("ws://localhost/api/v1/market-data/feed?userId=$userId"))

        handler.afterConnectionClosed(session, CloseStatus.NORMAL)

        verify(marketDataFeedService).removeSession(userId)
    }

    test("afterConnectionClosed_missingUserId_doesNotCallRemoveSession") {
        val session = mockSession(URI("ws://localhost/api/v1/market-data/feed"))

        handler.afterConnectionClosed(session, CloseStatus.NORMAL)

        verify(marketDataFeedService, never()).removeSession(any())
    }

    test("afterConnectionClosed_invalidUserId_doesNotCallRemoveSession") {
        val session = mockSession(URI("ws://localhost/api/v1/market-data/feed?userId=bad-uuid"))

        handler.afterConnectionClosed(session, CloseStatus.NORMAL)

        verify(marketDataFeedService, never()).removeSession(any())
    }
})
