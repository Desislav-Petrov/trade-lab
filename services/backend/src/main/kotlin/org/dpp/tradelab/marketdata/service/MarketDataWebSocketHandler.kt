package org.dpp.tradelab.marketdata.service

import org.dpp.tradelab.user.api.TokenValidationApi
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.UUID

/**
 * WebSocket handler for the real-time market data feed.
 *
 * On connection:
 *  1. Validates the JWT from the `Sec-WebSocket-Protocol` header. Closes with 4401 if absent/invalid.
 *  2. Registers the session with [MarketDataFeedService].
 *  3. Sends an immediate SNAPSHOT JSON message for the user's subscribed tickers.
 *  4. Closes with 4500 on any uncaught exception.
 *
 * On disconnect:
 *  - Removes the session from [MarketDataFeedService] using its validated user ID.
 */
@Component
class MarketDataWebSocketHandler(
    private val marketDataFeedService: MarketDataFeedService,
    private val tokenValidationApi: TokenValidationApi
) : TextWebSocketHandler() {

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val token = extractToken(session)
        if (token == null) {
            session.close(CloseStatus(4401, "token required"))
            return
        }

        val userId = try {
            tokenValidationApi.validateAndExtractUserId(token)
        } catch (ex: Exception) {
            session.close(CloseStatus(4401, "invalid token"))
            return
        }

        session.attributes[USER_ID_ATTRIBUTE] = userId

        try {
            marketDataFeedService.registerSession(userId, session)
            val snapshots = marketDataFeedService.getSnapshotForUser(userId)
            marketDataFeedService.sendSnapshot(session, snapshots)
        } catch (ex: Exception) {
            session.close(CloseStatus(4500, "internal error"))
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = session.attributes[USER_ID_ATTRIBUTE] as? UUID ?: return
        marketDataFeedService.removeSession(userId)
    }

    // handleTextMessage is intentionally not overridden — the server only sends, never receives

    private fun extractToken(session: WebSocketSession): String? {
        val protocols = session.handshakeHeaders[SEC_WEBSOCKET_PROTOCOL_HEADER]
            .orEmpty()
            .flatMap { headerValue -> headerValue.split(',').map { it.trim() } }

        if (protocols.firstOrNull() != BEARER_PROTOCOL) return null
        return protocols.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val USER_ID_ATTRIBUTE = "userId"
        const val SEC_WEBSOCKET_PROTOCOL_HEADER = "Sec-WebSocket-Protocol"
        const val BEARER_PROTOCOL = "bearer"
    }
}
