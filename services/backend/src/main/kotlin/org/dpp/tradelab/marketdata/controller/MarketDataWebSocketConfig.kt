package org.dpp.tradelab.marketdata.controller

import org.dpp.tradelab.marketdata.service.MarketDataWebSocketHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * Registers the [MarketDataWebSocketHandler] at the `/api/v1/market-data/feed` path.
 *
 * The `bearer` subprotocol is advertised by the handler itself via [org.springframework.web.socket.SubProtocolCapable]
 * so it survives Spring's `WebSocketHandlerDecorator.unwrap()` during handshake negotiation and
 * is echoed back in the 101 `Sec-WebSocket-Protocol` header. Declaring it on a wrapping decorator
 * does NOT work: unwrap recurses past decorators to the innermost handler, so the subprotocol would
 * be dropped and browsers requesting it would fail the connection right after the upgrade.
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
    private val handshakeLoggingInterceptor: MarketDataHandshakeLoggingInterceptor,
    @Value("\${app.cors.allowed-origins}")
    private val corsAllowedOrigins: List<String>,
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(marketDataWebSocketHandler, "/api/v1/market-data/feed")
            .addInterceptors(handshakeLoggingInterceptor)
            .setAllowedOrigins(*corsAllowedOrigins.toTypedArray())
    }
}
