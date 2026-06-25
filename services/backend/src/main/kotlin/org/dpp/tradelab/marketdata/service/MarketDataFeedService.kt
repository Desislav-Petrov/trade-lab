package org.dpp.tradelab.marketdata.service

import jakarta.annotation.PostConstruct
import org.dpp.tradelab.marketdata.config.SupportedTickerConfig
import org.dpp.tradelab.marketdata.messaging.AssetSubscribedEvent
import org.dpp.tradelab.marketdata.messaging.AssetUnsubscribedEvent
import org.dpp.tradelab.marketdata.model.MarketDataSnapshot
import org.dpp.tradelab.marketdata.repository.AssetSubscriptionRepository
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
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
 * Prices are serialised to exactly 3 decimal places as JSON numbers in all outbound messages.
 * This is achieved via a custom [ValueSerializer] for [BigDecimal] registered on a private
 * [ObjectMapper] derived from the Spring-managed one.
 */
@Service
class MarketDataFeedService(
    private val assetSubscriptionRepository: AssetSubscriptionRepository,
    private val priceFeedGenerator: PriceFeedGenerator,
    private val supportedTickerConfig: SupportedTickerConfig,
    objectMapper: ObjectMapper
) {

    // ── ObjectMapper with 3 d.p. BigDecimal serialisation ────────────────────

    private val mapper: ObjectMapper = objectMapper
        .rebuild()
        .addModule(
            SimpleModule().addSerializer(
                BigDecimal::class.java,
                object : ValueSerializer<BigDecimal>() {
                    override fun serialize(
                        value: BigDecimal,
                        gen: JsonGenerator,
                        ctxt: SerializationContext
                    ) {
                        gen.writeNumber(value.setScale(3, RoundingMode.HALF_UP).toPlainString())
                    }
                }
            )
        )
        .build()

    // ── Internal state ───────────────────────────────────────────────────────

    internal val snapshotCache: ConcurrentHashMap<String, MarketDataSnapshot> = ConcurrentHashMap()
    internal val tickerToUsers: ConcurrentHashMap<String, MutableSet<UUID>> = ConcurrentHashMap()
    internal val userToTickers: ConcurrentHashMap<UUID, MutableSet<String>> = ConcurrentHashMap()
    internal val activeSessions: ConcurrentHashMap<UUID, WebSocketSession> = ConcurrentHashMap()

    // ── Startup initialisation ────────────────────────────────────────────────

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

    // ── Scheduled dispatch ────────────────────────────────────────────────────

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

    // ── Session management ────────────────────────────────────────────────────

    fun registerSession(userId: UUID, session: WebSocketSession) {
        activeSessions[userId] = session
    }

    fun removeSession(userId: UUID) {
        activeSessions.remove(userId)
    }

    // ── Snapshot query ────────────────────────────────────────────────────────

    fun getSnapshotForUser(userId: UUID): List<MarketDataSnapshot> {
        val tickers = userToTickers[userId] ?: return emptyList()
        return tickers.mapNotNull { ticker -> snapshotCache[ticker] }
    }

    // ── WebSocket message senders ─────────────────────────────────────────────

    fun sendTick(session: WebSocketSession, snapshot: MarketDataSnapshot) {
        val json = mapper.writeValueAsString(
            mapOf("type" to "TICK", "data" to snapshotToMap(snapshot))
        )
        session.sendMessage(TextMessage(json))
    }

    fun sendSnapshot(session: WebSocketSession, snapshots: List<MarketDataSnapshot>) {
        val json = mapper.writeValueAsString(
            mapOf("type" to "SNAPSHOT", "data" to snapshots.map { snapshotToMap(it) })
        )
        session.sendMessage(TextMessage(json))
    }

    // ── Event listeners ───────────────────────────────────────────────────────

    @EventListener
    fun onAssetSubscribed(event: AssetSubscribedEvent) {
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

    @EventListener
    fun onAssetUnsubscribed(event: AssetUnsubscribedEvent) {
        event.tickers.forEach { ticker ->
            tickerToUsers[ticker]?.remove(event.userId)
            userToTickers[event.userId]?.remove(ticker)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun snapshotToMap(snapshot: MarketDataSnapshot): Map<String, Any> = mapOf(
        "ticker" to snapshot.ticker,
        "companyName" to snapshot.companyName,
        "currentPrice" to snapshot.currentPrice,
        "open" to snapshot.open,
        "dayLow" to snapshot.dayLow,
        "fiftyTwoWeekHigh" to snapshot.fiftyTwoWeekHigh
    )
}
