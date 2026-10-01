package org.dpp.tradelab.marketdata.controller

import org.dpp.tradelab.marketdata.service.MarketDataWebSocketHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.SubProtocolCapable
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.handler.WebSocketHandlerDecorator

/**
 * Registers the [MarketDataWebSocketHandler] at the `/api/v1/market-data/feed` path.
 * The `bearer` subprotocol is advertised for browser WebSocket negotiation.
 *
 * The allowed origins are taken from `app.cors.allowed-origins` to stay consistent
 * with the global CORS policy and avoid conflicts with `allowCredentials = true`.
 * A browser whose `Origin` is not in this list has its handshake rejected with 403
 * (surfacing in the browser as a 1006 close), so the list must include every host
 * the frontend is served from (e.g. both `localhost` and `127.0.0.1`).
 */
@Configuration
@EnableWebSocket
class MarketDataWebSocketConfig(
    private val marketDataWebSocketHandler: MarketDataWebSocketHandler,
    @Value("\${app.cors.allowed-origins}")
    private val corsAllowedOrigins: List<String>,
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(
                object : WebSocketHandlerDecorator(marketDataWebSocketHandler), SubProtocolCapable {
                    override fun getSubProtocols() = listOf("bearer")
                },
                "/api/v1/market-data/feed"
            )
            .setAllowedOrigins(*corsAllowedOrigins.toTypedArray())
    }
}
