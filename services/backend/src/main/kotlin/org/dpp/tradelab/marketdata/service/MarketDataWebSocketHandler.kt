package org.dpp.tradelab.marketdata.service

import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.UUID

/**
 * WebSocket handler for the real-time market data feed.
 *
 * On connection:
 *  1. Extracts `userId` from the query string. Closes with 4401 if absent/invalid.
 *  2. Registers the session with [MarketDataFeedService].
 *  3. Sends an immediate SNAPSHOT JSON message for the user's subscribed tickers.
 *  4. Closes with 4500 on any uncaught exception.
 *
 * On disconnect:
 *  - Removes the session from [MarketDataFeedService] if `userId` is parseable.
 */
@Component
class MarketDataWebSocketHandler(
    private val marketDataFeedService: MarketDataFeedService
) : TextWebSocketHandler() {

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val query = session.uri?.query

        val userIdString = extractUserId(query)
        if (userIdString.isNullOrBlank()) {
            session.close(CloseStatus(4401, "userId required"))
            return
        }

        val userId: UUID = try {
            UUID.fromString(userIdString)
        } catch (ex: IllegalArgumentException) {
            session.close(CloseStatus(4401, "invalid userId"))
            return
        }

        try {
            marketDataFeedService.registerSession(userId, session)
            val snapshots = marketDataFeedService.getSnapshotForUser(userId)
            marketDataFeedService.sendSnapshot(session, snapshots)
        } catch (ex: Exception) {
            session.close(CloseStatus(4500, "internal error"))
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val query = session.uri?.query
        val userIdString = extractUserId(query) ?: return
        val userId: UUID = try {
            UUID.fromString(userIdString)
        } catch (ex: IllegalArgumentException) {
            return
        }
        marketDataFeedService.removeSession(userId)
    }

    // handleTextMessage is intentionally not overridden — the server only sends, never receives

    private fun extractUserId(query: String?): String? {
        if (query.isNullOrBlank()) return null
        return query.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == "userId" }
            ?.get(1)
    }
}
