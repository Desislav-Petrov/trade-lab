package org.dpp.tradelab.marketdata.controller

import io.kotest.core.spec.style.FunSpec
import org.dpp.tradelab.marketdata.service.MarketDataWebSocketHandler
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * Verifies that [MarketDataWebSocketConfig] applies every configured origin to the
 * WebSocket handshake. A browser whose `Origin` is not in this list is rejected
 * with a 403 (surfacing as a 1006 close in the browser), so both `localhost` and
 * `127.0.0.1` must be registered.
 */
class MarketDataWebSocketConfigOriginsTest : FunSpec({

    test("registerWebSocketHandlers_appliesAllConfiguredOriginsToHandshake") {
        val origins = listOf("http://localhost:5173", "http://127.0.0.1:5173")
        val config = MarketDataWebSocketConfig(
            mock<MarketDataWebSocketHandler>(),
            mock<MarketDataHandshakeLoggingInterceptor>(),
            origins,
        )

        val registration = mock<WebSocketHandlerRegistration>()
        whenever(registration.addInterceptors(any())).thenReturn(registration)
        whenever(registration.setAllowedOrigins(any())).thenReturn(registration)
        val registry = mock<WebSocketHandlerRegistry>()
        whenever(registry.addHandler(any(), any())).thenReturn(registration)

        config.registerWebSocketHandlers(registry)

        verify(registration).setAllowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
    }
})
