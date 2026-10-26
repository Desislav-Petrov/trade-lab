package org.dpp.tradelab.marketdata.service

import com.fasterxml.jackson.core.StreamWriteFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import jakarta.annotation.PostConstruct
import org.dpp.tradelab.marketdata.api.MarketDataApi
import org.dpp.tradelab.marketdata.config.SupportedTickerConfig
import org.dpp.tradelab.marketdata.messaging.AssetSubscribedEvent
import org.dpp.tradelab.marketdata.messaging.AssetUnsubscribedEvent
import org.dpp.tradelab.marketdata.messaging.MarketDataTickEvent
import org.dpp.tradelab.marketdata.model.MarketDataSnapshot
import org.dpp.tradelab.marketdata.repository.AssetSubscriptionRepository
import org.dpp.tradelab.marketdata.exception.UnsupportedTickerException
import org.dpp.tradelab.user.api.UserSettingsApi
import org.dpp.tradelab.user.messaging.UserSettingsChangedEvent
import org.dpp.tradelab.user.model.FeedType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * Central service managing the in-memory snapshot cache, per-user subscription lookups,
 * active WebSocket session registry, feed-type cache, and the price-tick dispatcher.
 *
 * Thread safety: all maps are [ConcurrentHashMap]. The nested [MutableSet] instances
 * are created as [ConcurrentHashMap.newKeySet()] to ensure thread-safe mutations.
 *
 * Prices are serialised to exactly 3 decimal places in all outbound JSON messages.
 *
 * Event handling entry-points ([handleAssetSubscribed], [handleAssetUnsubscribed],
 * [handleUserSettingsChanged], [handleMarketDataTick]) are called by dedicated listener
 * classes — they must not be annotated with `@EventListener` directly.
 */
@Service
class MarketDataFeedService(
    private val assetSubscriptionRepository: AssetSubscriptionRepository,
    private val syntheticPriceFeedAdapter: SyntheticPriceFeedAdapter,
    private val supportedTickerConfig: SupportedTickerConfig,
    private val userSettingsApi: UserSettingsApi,
    @Qualifier("marketDataTickDispatchExecutor")
    private val tickDispatchExecutor: Executor
) : MarketDataApi {

    companion object {
        /** Max time a single send may hold the per-session lock before the session is force-closed. */
        private const val SEND_TIME_LIMIT_MS = 10_000
        /** Max bytes buffered per session before it is force-closed as too slow. */
        private const val BUFFER_SIZE_LIMIT = 512 * 1024
    }

    // ── Internal state ──────────────────────────────────────────────────

    internal val snapshotCache: ConcurrentHashMap<String, MarketDataSnapshot> = ConcurrentHashMap()
    internal val tickerToUsers: ConcurrentHashMap<String, MutableSet<UUID>> = ConcurrentHashMap()
    internal val userToTickers: ConcurrentHashMap<UUID, MutableSet<String>> = ConcurrentHashMap()
    internal val activeSessions: ConcurrentHashMap<UUID, WebSocketSession> = ConcurrentHashMap()
    internal val feedTypeCache: ConcurrentHashMap<UUID, FeedType> = ConcurrentHashMap()

    private val logger = LoggerFactory.getLogger(MarketDataFeedService::class.java)

    /**
     * Dedicated mapper owning the WebSocket wire format. Configured locally — rather than
     * injecting the shared Spring bean — so the outbound message format stays deterministic
     * and immune to global Jackson configuration changes. [StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN]
     * keeps prices as plain JSON numbers (e.g. `150.000`) with no exponent notation.
     */
    private val objectMapper: ObjectMapper = JsonMapper.builder()
        .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
        .build()

    // ── Startup initialisation ───────────────────────────────────────────────

    @PostConstruct
    fun init() {
        seedSnapshotCache()
        loadSubscriptions()
    }

    private fun seedSnapshotCache() {
        val allSupported = supportedTickerConfig.getAll().keys
        var seededTickers = snapshotCache.keys.toMutableSet()

        // Keep calling generateTick() until all supported tickers have a cache entry.
        // generateTick() returns 1–10 random tickers per call, so multiple calls are needed.
        var attempts = 0
        val maxAttempts = allSupported.size * 20 + 100
        while (seededTickers.size < allSupported.size && attempts < maxAttempts) {
            val ticks = syntheticPriceFeedAdapter.generateTick()
            ticks.forEach { snapshot ->
                if (snapshot.ticker in allSupported) {
                    snapshotCache[snapshot.ticker] = snapshot
                }
            }
            seededTickers = snapshotCache.keys.toMutableSet()
            attempts++
        }
    }

    private fun loadSubscriptions() {
        val allSubscriptions = assetSubscriptionRepository.findAll()
        allSubscriptions.forEach { subscription ->
            tickerToUsers
                .getOrPut(subscription.ticker) { ConcurrentHashMap.newKeySet() }
                .add(subscription.userId)
            userToTickers
                .getOrPut(subscription.userId) { ConcurrentHashMap.newKeySet() }
                .add(subscription.ticker)
        }
    }

    internal fun resolveFeedType(userId: UUID): FeedType =
        feedTypeCache.getOrPut(userId) {
            userSettingsApi.getUserSettings(userId)?.feedType
                ?: run {
                    logger.warn("No feed type settings found for userId=$userId, defaulting to SYNTHETIC")
                    FeedType.SYNTHETIC
                }
        }

    // ── Market data tick handler (called by MarketDataEventListener) ──────────

    fun handleMarketDataTick(event: MarketDataTickEvent) {
        val snapshot = event.snapshot
        snapshotCache[snapshot.ticker] = snapshot
        val subscribedUsers = tickerToUsers[snapshot.ticker] ?: return
        subscribedUsers.forEach { userId ->
            val session = activeSessions[userId] ?: return@forEach
            if (session.isOpen && resolveFeedType(userId) == event.feedType) {
                // Offload the blocking sendMessage I/O so a slow client cannot
                // stall delivery to other subscribers or delay the next tick.
                tickDispatchExecutor.execute { dispatchTick(session, snapshot) }
            }
        }
    }

    private fun dispatchTick(session: WebSocketSession, snapshot: MarketDataSnapshot) {
        try {
            if (session.isOpen) {
                sendTick(session, snapshot)
            }
        } catch (ex: Exception) {
            logger.warn("Failed to dispatch tick for ticker=${snapshot.ticker}: ${ex.message}")
        }
    }

    // ── Session management ────────────────────────────────────────────

    fun registerSession(userId: UUID, session: WebSocketSession) {
        // Wrap in a decorator so concurrent sends from the dispatch pool are
        // serialised per session and a slow client is buffered (then dropped)
        // rather than blocking a shared worker thread.
        activeSessions[userId] = ConcurrentWebSocketSessionDecorator(
            session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT
        )
    }

    fun removeSession(userId: UUID) {
        activeSessions.remove(userId)
    }

    // ── Snapshot query ────────────────────────────────────────────

    fun getSnapshotForUser(userId: UUID): List<MarketDataSnapshot> {
        val tickers = userToTickers[userId] ?: return emptyList()
        return tickers.mapNotNull { ticker -> snapshotCache[ticker] }
    }

    // ── MarketDataApi implementation ────────────────────────────────────────

    override fun getCurrentPrice(ticker: String): BigDecimal {
        val snapshot = snapshotCache[ticker.uppercase()]
            ?: throw IllegalStateException(
                "No cache entry for ticker '$ticker'. " +
                    "The cache should be fully seeded at startup — this is a programming error."
            )
        return snapshot.currentPrice
    }

    // ── MarketDataApi.isTickerSupported implementation ────────────────────
    override fun isTickerSupported(ticker: String): Boolean =
        supportedTickerConfig.resolve(ticker) != null

    // ── MarketDataApi.getPrices implementation ────────────────────────────────
    override fun getPrices(tickers: List<String>): Map<String, BigDecimal> {
        if (tickers.isEmpty()) {
            return emptyMap()
        }
        return tickers.mapNotNull { ticker ->
            val snapshot = snapshotCache[ticker.uppercase()]
            snapshot?.let { ticker.uppercase() to it.currentPrice }
        }.toMap()
    }

    // ── getPrice — validated price lookup for REST endpoint ───────────────────
    fun getPrice(ticker: String): BigDecimal {
        if (supportedTickerConfig.resolve(ticker) == null) {
            throw UnsupportedTickerException("Ticker '$ticker' is not in the supported tickers list.")
        }
        return snapshotCache[ticker.uppercase()]?.currentPrice
            ?: throw UnsupportedTickerException("Ticker '$ticker' is not in the supported tickers list.")
    }

    // ── WebSocket message senders ──────────────────────────────────────────────

    fun sendTick(session: WebSocketSession, snapshot: MarketDataSnapshot) {
        val json = buildTickJson(snapshot)
        session.sendMessage(TextMessage(json))
    }

    fun sendSnapshot(session: WebSocketSession, snapshots: List<MarketDataSnapshot>) {
        val json = buildSnapshotJson(snapshots)
        session.sendMessage(TextMessage(json))
    }

    // ── Event handlers (called by MarketDataEventListener / UserEventListener) ───────────────────────

    fun handleAssetSubscribed(event: AssetSubscribedEvent) {
        event.tickers.forEach { ticker ->
            tickerToUsers
                .getOrPut(ticker) { ConcurrentHashMap.newKeySet() }
                .add(event.userId)
            userToTickers
                .getOrPut(event.userId) { ConcurrentHashMap.newKeySet() }
                .add(ticker)
        }

        val session = activeSessions[event.userId]
        if (session != null && session.isOpen) {
            event.tickers.forEach { ticker ->
                val snapshot = snapshotCache[ticker] ?: return@forEach
                sendTick(session, snapshot)
            }
        }
    }

    fun handleAssetUnsubscribed(event: AssetUnsubscribedEvent) {
        event.tickers.forEach { ticker ->
            tickerToUsers[ticker]?.remove(event.userId)
            userToTickers[event.userId]?.remove(ticker)
        }
    }

    fun handleUserSettingsChanged(event: UserSettingsChangedEvent) {
        feedTypeCache[event.userId] = event.feedType
    }

    // ── JSON building helpers ──────────────────────────────────────────────
    //
    // Outbound messages are serialised with Jackson rather than built by hand so that
    // every message is valid JSON regardless of field contents. Jackson correctly escapes
    // control characters (newline, tab, etc.) and quotes/backslashes in string fields such
    // as [MarketDataSnapshot.companyName], which naive string concatenation did not.

    private fun buildTickJson(snapshot: MarketDataSnapshot): String =
        objectMapper.writeValueAsString(
            linkedMapOf("type" to "TICK", "data" to snapshotToMap(snapshot))
        )

    private fun buildSnapshotJson(snapshots: List<MarketDataSnapshot>): String =
        objectMapper.writeValueAsString(
            linkedMapOf("type" to "SNAPSHOT", "data" to snapshots.map { snapshotToMap(it) })
        )

    internal fun snapshotToJson(snapshot: MarketDataSnapshot): String =
        objectMapper.writeValueAsString(snapshotToMap(snapshot))

    /**
     * Builds an ordered map representation of a snapshot for JSON serialisation.
     * Prices are scaled to exactly 3 decimal places. Field order is preserved via
     * [LinkedHashMap] so the wire format stays byte-stable, and serialisation is delegated
     * to Jackson, which guarantees valid JSON (including correct control-character escaping).
     */
    private fun snapshotToMap(snapshot: MarketDataSnapshot): Map<String, Any> =
        linkedMapOf(
            "ticker" to snapshot.ticker,
            "companyName" to snapshot.companyName,
            "currentPrice" to snapshot.currentPrice.setScale(3, RoundingMode.HALF_UP),
            "open" to snapshot.open.setScale(3, RoundingMode.HALF_UP),
            "dayLow" to snapshot.dayLow.setScale(3, RoundingMode.HALF_UP),
            "dayHigh" to snapshot.dayHigh.setScale(3, RoundingMode.HALF_UP),
            "fiftyTwoWeekHigh" to snapshot.fiftyTwoWeekHigh.setScale(3, RoundingMode.HALF_UP)
        )
}
