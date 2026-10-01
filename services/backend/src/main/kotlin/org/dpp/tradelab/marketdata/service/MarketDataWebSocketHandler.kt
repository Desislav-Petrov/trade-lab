package org.dpp.tradelab.marketdata.service

import org.dpp.tradelab.common.service.JwtService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.SubProtocolCapable
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.io.EOFException
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
    private val jwtService: JwtService
) : TextWebSocketHandler(), SubProtocolCapable {

    /**
     * Advertises the `bearer` subprotocol so the handshake echoes it in the 101 response's
     * `Sec-WebSocket-Protocol` header. This MUST be implemented on the handler itself (the
     * innermost, non-decorator handler): Spring's handshake negotiation calls
     * `WebSocketHandlerDecorator.unwrap()`, which recurses past every decorator down to this
     * handler. If it is declared on a wrapping decorator instead, unwrap skips it, no
     * subprotocol is selected, and browsers that requested one (e.g. `['bearer', token]`)
     * fail the connection immediately after the upgrade.
     */
    override fun getSubProtocols(): List<String> = listOf(BEARER_PROTOCOL)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val origin = session.handshakeHeaders.getFirst("Origin")
        val rawProtocolHeader = session.handshakeHeaders[SEC_WEBSOCKET_PROTOCOL_HEADER]
        logger.info(
            "WS connection established sessionId={} uri={} origin={} acceptedSubProtocol={} Sec-WebSocket-Protocol={}",
            session.id,
            session.uri,
            origin,
            session.acceptedProtocol,
            rawProtocolHeader
        )

        val token = extractToken(session)
        if (token == null) {
            logger.warn(
                "WS auth rejected: no bearer token in Sec-WebSocket-Protocol sessionId={} origin={} Sec-WebSocket-Protocol={}",
                session.id,
                origin,
                rawProtocolHeader
            )
            session.close(CloseStatus(4401, "token required"))
            return
        }

        val userId = try {
            jwtService.validateAndExtractUserId(token)
        } catch (ex: Exception) {
            logger.warn(
                "WS auth rejected: token validation failed sessionId={} origin={} reason={}",
                session.id,
                origin,
                ex.message,
                ex
            )
            session.close(CloseStatus(4401, "invalid token"))
            return
        }

        session.attributes[USER_ID_ATTRIBUTE] = userId
        logger.info("WS authenticated sessionId={} userId={}", session.id, userId)

        try {
            marketDataFeedService.registerSession(userId, session)
            val snapshots = marketDataFeedService.getSnapshotForUser(userId)
            marketDataFeedService.sendSnapshot(session, snapshots)
            logger.info(
                "WS session ready sessionId={} userId={} snapshotTickers={}",
                session.id,
                userId,
                snapshots.size
            )
        } catch (ex: Exception) {
            logger.error(
                "WS internal error during session setup sessionId={} userId={}",
                session.id,
                userId,
                ex
            )
            session.close(CloseStatus(4500, "internal error"))
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        logger.info(
            "WS connection closed sessionId={} code={} reason={}",
            session.id,
            status.code,
            status.reason
        )
        val userId = session.attributes[USER_ID_ATTRIBUTE] as? UUID ?: return
        marketDataFeedService.removeSession(userId)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        // A client going away (EOF/connection reset) is a normal WebSocket lifecycle event,
        // not an application error — keep it at DEBUG to avoid log noise. Anything else is
        // genuinely unexpected and logged at WARN.
        if (exception is EOFException) {
            logger.debug("WS client disconnected sessionId={}", session.id)
        } else {
            logger.warn("WS transport error sessionId={}", session.id, exception)
        }
    }

    // handleTextMessage is intentionally not overridden — the server only sends, never receives

    private fun extractToken(session: WebSocketSession): String? {
        val protocols = session.handshakeHeaders[SEC_WEBSOCKET_PROTOCOL_HEADER]
            .orEmpty()
            .flatMap { headerValue -> headerValue.split(',').map { it.trim() } }

        logger.debug("WS parsed subprotocols sessionId={} protocols={}", session.id, protocols)

        if (protocols.firstOrNull() != BEARER_PROTOCOL) {
            logger.warn(
                "WS first subprotocol is not '{}' sessionId={} protocols={}",
                BEARER_PROTOCOL,
                session.id,
                protocols
            )
            return null
        }
        return protocols.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(MarketDataWebSocketHandler::class.java)
        const val USER_ID_ATTRIBUTE = "userId"
        const val SEC_WEBSOCKET_PROTOCOL_HEADER = "Sec-WebSocket-Protocol"
        const val BEARER_PROTOCOL = "bearer"
    }
}
