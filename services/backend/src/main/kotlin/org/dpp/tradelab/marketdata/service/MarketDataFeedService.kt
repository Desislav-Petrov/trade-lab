package org.dpp.tradelab.marketdata.service

import jakarta.annotation.PostConstruct
import org.dpp.tradelab.marketdata.api.MarketDataApi
import org.dpp.tradelab.marketdata.api.MarketDataSupportedTickersApi
import org.dpp.tradelab.marketdata.config.SupportedTickerConfig
import org.dpp.tradelab.marketdata.messaging.AssetSubscribedEvent
import org.dpp.tradelab.marketdata.messaging.AssetUnsubscribedEvent
import org.dpp.tradelab.marketdata.model.MarketDataSnapshot
import org.dpp.tradelab.marketdata.repository.AssetSubscriptionRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Central service managing the in-memory snapshot cache, per-user subscription lookups,
 * active WebSocket session registry, and the scheduled price-tick dispatcher.
 *
 * Thread safety: all four maps are [ConcurrentHashMap]. The nested [MutableSet] instances
 * are created as [ConcurrentHashMap.newKeySet()] to ensure thread-safe mutations.
 *
 * Prices are serialised to exactly 3 decimal places in all outbound JSON messages.
 *
 * Event handling entry-points ([handleAssetSubscribed], [handleAssetUnsubscribed]) are
 * called by [org.dpp.tradelab.marketdata.messaging.MarketDataEventListener] — they must
 * not be annotated with `@EventListener` directly.
 */
@Service
class MarketDataFeedService(
    private val assetSubscriptionRepository: AssetSubscriptionRepository,
    private val priceFeedGenerator: PriceFeedGenerator,
    private val supportedTickerConfig: SupportedTickerConfig
) : MarketDataApi, MarketDataSupportedTickersApi {

    // ── Internal state ──────────────────────────────────────────────────

    internal val snapshotCache: ConcurrentHashMap<String, MarketDataSnapshot> = ConcurrentHashMap()
    internal val tickerToUsers: ConcurrentHashMap<String, MutableSet<UUID>> = ConcurrentHashMap()
    internal val userToTickers: ConcurrentHashMap<UUID, MutableSet<String>> = ConcurrentHashMap()
    internal val activeSessions: ConcurrentHashMap<UUID, WebSocketSession> = ConcurrentHashMap()

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
            val ticks = priceFeedGenerator.generateTick()
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

    // ── Scheduled dispatch ────────────────────────────────────────────

    @Scheduled(fixedDelay = 250)
    fun dispatchTicks() {
        val ticks = priceFeedGenerator.generateTick()
        ticks.forEach { snapshot ->
            snapshotCache[snapshot.ticker] = snapshot
            val subscribedUsers = tickerToUsers[snapshot.ticker] ?: return@forEach
            subscribedUsers.forEach { userId ->
                val session = activeSessions[userId] ?: return@forEach
                if (session.isOpen) {
                    sendTick(session, snapshot)
                }
            }
        }
    }

    // ── Session management ────────────────────────────────────────────

    fun registerSession(userId: UUID, session: WebSocketSession) {
        activeSessions[userId] = session
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

    // ── MarketDataSupportedTickersApi implementation ───────────────────────

    override fun isTickerSupported(ticker: String): Boolean =
        supportedTickerConfig.resolve(ticker) != null

    // ── WebSocket message senders ──────────────────────────────────────────────

    fun sendTick(session: WebSocketSession, snapshot: MarketDataSnapshot) {
        val json = buildTickJson(snapshot)
        session.sendMessage(TextMessage(json))
    }

    fun sendSnapshot(session: WebSocketSession, snapshots: List<MarketDataSnapshot>) {
        val json = buildSnapshotJson(snapshots)
        session.sendMessage(TextMessage(json))
    }

    // ── Event handlers (called by MarketDataEventListener) ───────────────────────

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

    // ── JSON building helpers ──────────────────────────────────────────────

    private fun buildTickJson(snapshot: MarketDataSnapshot): String {
        return """{"type":"TICK","data":${snapshotToJson(snapshot)}}"""
    }

    private fun buildSnapshotJson(snapshots: List<MarketDataSnapshot>): String {
        val dataJson = snapshots.joinToString(",") { snapshotToJson(it) }
        return """{"type":"SNAPSHOT","data":[$dataJson]}"""
    }

    internal fun snapshotToJson(snapshot: MarketDataSnapshot): String {
        val currentPrice = snapshot.currentPrice.setScale(3, RoundingMode.HALF_UP).toPlainString()
        val open = snapshot.open.setScale(3, RoundingMode.HALF_UP).toPlainString()
        val dayLow = snapshot.dayLow.setScale(3, RoundingMode.HALF_UP).toPlainString()
        val fiftyTwoWeekHigh = snapshot.fiftyTwoWeekHigh.setScale(3, RoundingMode.HALF_UP).toPlainString()
        val ticker = escapeJson(snapshot.ticker)
        val companyName = escapeJson(snapshot.companyName)
        return """{"ticker":"$ticker","companyName":"$companyName","currentPrice":$currentPrice,"open":$open,"dayLow":$dayLow,"fiftyTwoWeekHigh":$fiftyTwoWeekHigh}"""
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
