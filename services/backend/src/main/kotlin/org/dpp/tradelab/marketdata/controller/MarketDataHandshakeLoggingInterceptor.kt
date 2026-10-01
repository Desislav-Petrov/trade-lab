package org.dpp.tradelab.marketdata.controller

import org.slf4j.LoggerFactory
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

/**
 * Logs every WebSocket handshake attempt on the market data feed endpoint, including
 * handshakes that are rejected before the upgrade completes (e.g. disallowed Origin -> 403,
 * which surfaces in the browser as an opaque 1006 close with no handler callback).
 *
 * This complements the post-upgrade logging in [org.dpp.tradelab.marketdata.service.MarketDataWebSocketHandler]:
 *  - If nothing from this interceptor is logged, the request never reached the WebSocket machinery
 *    (wrong path, blocked by a filter/proxy, or the servlet security chain).
 *  - If `beforeHandshake` logs but the response status is not 101, the handshake was rejected
 *    (most commonly an Origin that is not present in `app.cors.allowed-origins`).
 */
@Component
class MarketDataHandshakeLoggingInterceptor : HandshakeInterceptor {

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val headers = request.headers
        logger.info(
            "WS handshake attempt uri={} remote={} origin={} upgrade={} connection={} Sec-WebSocket-Protocol={} Sec-WebSocket-Version={}",
            request.uri,
            request.remoteAddress,
            headers.getFirst("Origin"),
            headers.getFirst("Upgrade"),
            headers.getFirst("Connection"),
            headers["Sec-WebSocket-Protocol"],
            headers.getFirst("Sec-WebSocket-Version"),
        )
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) {
        val status = (response as? ServletServerHttpResponse)?.servletResponse?.status
        if (exception != null) {
            logger.error(
                "WS handshake failed uri={} origin={} responseStatus={}",
                request.uri,
                request.headers.getFirst("Origin"),
                status,
                exception,
            )
            return
        }
        if (status != null && status != 101) {
            logger.warn(
                "WS handshake not upgraded uri={} origin={} responseStatus={} (expected 101; a 403 usually means the Origin is not in app.cors.allowed-origins)",
                request.uri,
                request.headers.getFirst("Origin"),
                status,
            )
        } else {
            logger.info(
                "WS handshake upgraded uri={} origin={} responseStatus={}",
                request.uri,
                request.headers.getFirst("Origin"),
                status,
            )
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(MarketDataHandshakeLoggingInterceptor::class.java)
    }
}
