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
 * The allowed origin is taken from `app.cors.allowed-origin` to stay consistent
 * with the global CORS policy and avoid conflicts with `allowCredentials = true`.
 */
@Configuration
@EnableWebSocket
class MarketDataWebSocketConfig(
    private val marketDataWebSocketHandler: MarketDataWebSocketHandler,
    @Value("\${app.cors.allowed-origin}")
    private val corsAllowedOrigin: String,
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(
                object : WebSocketHandlerDecorator(marketDataWebSocketHandler), SubProtocolCapable {
                    override fun getSubProtocols() = listOf("bearer")
                },
                "/api/v1/market-data/feed"
            )
            .setAllowedOrigins(corsAllowedOrigin)
    }
}
