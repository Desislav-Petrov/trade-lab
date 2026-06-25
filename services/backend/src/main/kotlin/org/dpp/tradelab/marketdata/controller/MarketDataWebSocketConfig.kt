package org.dpp.tradelab.marketdata.controller

import org.dpp.tradelab.marketdata.service.MarketDataWebSocketHandler
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * Registers the [MarketDataWebSocketHandler] at the `/api/v1/market-data/feed` path.
 *
 * All origins are allowed (`*`) — in a production setting this should be restricted
 * to trusted frontend origins.
 */
@Configuration
@EnableWebSocket
class MarketDataWebSocketConfig(
    private val marketDataWebSocketHandler: MarketDataWebSocketHandler
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(marketDataWebSocketHandler, "/api/v1/market-data/feed")
            .setAllowedOrigins("*")
    }
}
