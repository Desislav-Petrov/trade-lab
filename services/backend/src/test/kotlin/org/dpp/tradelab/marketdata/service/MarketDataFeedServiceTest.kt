package org.dpp.tradelab.marketdata.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.dpp.tradelab.marketdata.config.SupportedTickerConfig
import org.dpp.tradelab.marketdata.messaging.AssetSubscribedEvent
import org.dpp.tradelab.marketdata.messaging.AssetUnsubscribedEvent
import org.dpp.tradelab.marketdata.model.AssetSubscription
import org.dpp.tradelab.marketdata.model.MarketDataSnapshot
import org.dpp.tradelab.marketdata.repository.AssetSubscriptionRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.math.BigDecimal
import java.net.URI
import java.time.Instant
import java.util.UUID

class MarketDataFeedServiceTest : FunSpec({

    val repository = mock<AssetSubscriptionRepository>()
    val priceFeedGenerator = mock<PriceFeedGenerator>()
    val supportedTickerConfig = mock<SupportedTickerConfig>()

    fun buildService() = MarketDataFeedService(repository, priceFeedGenerator, supportedTickerConfig)

    val userId = UUID.randomUUID()
    val aaplSnapshot = MarketDataSnapshot(
        ticker = "AAPL",
        companyName = "Apple Inc.",
        currentPrice = BigDecimal("150.000"),
        open = BigDecimal("148.000"),
        dayLow = BigDecimal("147.500"),
        fiftyTwoWeekHigh = BigDecimal("200.000"),
        updatedAt = Instant.now()
    )
    val msftSnapshot = MarketDataSnapshot(
        ticker = "MSFT",
        companyName = "Microsoft Corporation",
        currentPrice = BigDecimal("300.000"),
        open = BigDecimal("298.000"),
        dayLow = BigDecimal("295.500"),
        fiftyTwoWeekHigh = BigDecimal("400.000"),
        updatedAt = Instant.now()
    )

    fun makeSubscription(userId: UUID, ticker: String, companyName: String) = AssetSubscription(
        subscriptionId = UUID.randomUUID(),
        userId = userId,
        ticker = ticker,
        companyName = companyName
    )

    fun mockSession(userId: UUID, open: Boolean = true): WebSocketSession {
        val session = mock<WebSocketSession>()
        whenever(session.isOpen).thenReturn(open)
        whenever(session.uri).thenReturn(URI("ws://localhost/api/v1/market-data/feed?userId=$userId"))
        return session
    }

    beforeEach {
        reset(repository, priceFeedGenerator, supportedTickerConfig)
    }

    // ── @PostConstruct seeding ──────────────────────────────────────────────────────────

    test("init_seedsSnapshotCacheForAllSupportedTickers") {
        val supportedTickers = mapOf(
            "AAPL" to "Apple Inc.",
            "MSFT" to "Microsoft Corporation"
        )
        whenever(supportedTickerConfig.getAll()).thenReturn(supportedTickers)
        // Alternate which ticker is returned on each call to eventually cover both
        var callCount = 0
        whenever(priceFeedGenerator.generateTick()).thenAnswer {
            callCount++
            if (callCount % 2 == 0) listOf(aaplSnapshot) else listOf(msftSnapshot)
        }
        whenever(repository.findAll()).thenReturn(emptyList())

        val service = buildService()
        service.init()

        service.snapshotCache.containsKey("AAPL") shouldBe true
        service.snapshotCache.containsKey("MSFT") shouldBe true
    }

    test("init_loadsSubscriptionsIntoLookupMaps") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("AAPL" to "Apple Inc."))
        whenever(priceFeedGenerator.generateTick()).thenReturn(listOf(aaplSnapshot))
        val subscription = makeSubscription(userId, "AAPL", "Apple Inc.")
        whenever(repository.findAll()).thenReturn(listOf(subscription))

        val service = buildService()
        service.init()

        service.tickerToUsers["AAPL"] shouldNotBe null
        service.tickerToUsers["AAPL"]!!.contains(userId) shouldBe true
        service.userToTickers[userId] shouldNotBe null
        service.userToTickers[userId]!!.contains("AAPL") shouldBe true
    }

    // ── dispatchTicks ─────────────────────────────────────────────────────────────

    test("dispatchTicks_dispatchesOnlyToSubscribedConnectedUsers") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("AAPL" to "Apple Inc."))
        whenever(priceFeedGenerator.generateTick())
            .thenReturn(listOf(aaplSnapshot))
            .thenReturn(listOf(aaplSnapshot))
        whenever(repository.findAll()).thenReturn(emptyList())

        val service = buildService()

        val session = mockSession(userId)
        service.registerSession(userId, session)
        service.tickerToUsers.getOrPut("AAPL") { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add(userId)
        service.userToTickers.getOrPut(userId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add("AAPL")

        service.dispatchTicks()

        val captor = argumentCaptor<TextMessage>()
        verify(session).sendMessage(captor.capture())
        val payload = captor.firstValue.payload
        payload.contains("\"type\":\"TICK\"") shouldBe true
        payload.contains("\"ticker\":\"AAPL\"") shouldBe true
    }

    test("dispatchTicks_doesNotDispatchToUnsubscribedUser") {
        val otherUserId = UUID.randomUUID()
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("AAPL" to "Apple Inc."))
        whenever(priceFeedGenerator.generateTick())
            .thenReturn(listOf(aaplSnapshot))
            .thenReturn(listOf(aaplSnapshot))
        whenever(repository.findAll()).thenReturn(emptyList())

        val service = buildService()

        val session = mockSession(otherUserId)
        service.registerSession(otherUserId, session)
        // otherUserId is NOT subscribed to AAPL
        service.tickerToUsers.getOrPut("AAPL") { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add(userId)
        service.userToTickers.getOrPut(userId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add("AAPL")

        service.dispatchTicks()

        verify(session, never()).sendMessage(any<TextMessage>())
    }

    test("dispatchTicks_doesNotDispatchToUserWithNoActiveSession") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("AAPL" to "Apple Inc."))
        whenever(priceFeedGenerator.generateTick())
            .thenReturn(listOf(aaplSnapshot))
            .thenReturn(listOf(aaplSnapshot))
        whenever(repository.findAll()).thenReturn(emptyList())

        val service = buildService()

        // userId subscribed to AAPL but has NO session registered
        service.tickerToUsers.getOrPut("AAPL") { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add(userId)
        service.userToTickers.getOrPut(userId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add("AAPL")

        // Should not throw
        service.dispatchTicks()
    }

    // ── onAssetSubscribed ──────────────────────────────────────────────────────────

    test("onAssetSubscribed_updatesLookupMapsAndSendsImmediateTickWhenConnected") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("MSFT" to "Microsoft Corporation"))
        whenever(priceFeedGenerator.generateTick()).thenReturn(listOf(msftSnapshot))
        whenever(repository.findAll()).thenReturn(emptyList())

        val service = buildService()
        service.snapshotCache["MSFT"] = msftSnapshot

        val session = mockSession(userId)
        service.registerSession(userId, session)

        val event = AssetSubscribedEvent(userId = userId, tickers = listOf("MSFT"), timestamp = Instant.now())
        service.onAssetSubscribed(event)

        service.tickerToUsers["MSFT"]!!.contains(userId) shouldBe true
        service.userToTickers[userId]!!.contains("MSFT") shouldBe true

        val captor = argumentCaptor<TextMessage>()
        verify(session).sendMessage(captor.capture())
        val payload = captor.firstValue.payload
        payload.contains("\"type\":\"TICK\"") shouldBe true
        payload.contains("\"ticker\":\"MSFT\"") shouldBe true
    }

    test("onAssetSubscribed_updatesLookupMapsSilentlyWhenDisconnected") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("MSFT" to "Microsoft Corporation"))
        whenever(priceFeedGenerator.generateTick()).thenReturn(listOf(msftSnapshot))
        whenever(repository.findAll()).thenReturn(emptyList())

        val service = buildService()
        service.snapshotCache["MSFT"] = msftSnapshot
        // No session registered for userId

        val event = AssetSubscribedEvent(userId = userId, tickers = listOf("MSFT"), timestamp = Instant.now())
        service.onAssetSubscribed(event)

        service.tickerToUsers["MSFT"]!!.contains(userId) shouldBe true
        service.userToTickers[userId]!!.contains("MSFT") shouldBe true
        // No exception; no session to send to
    }

    // ── onAssetUnsubscribed ───────────────────────────────────────────────────────

    test("onAssetUnsubscribed_removesTickers_fromBothMaps") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("AAPL" to "Apple Inc."))
        whenever(priceFeedGenerator.generateTick()).thenReturn(listOf(aaplSnapshot))
        whenever(repository.findAll()).thenReturn(emptyList())

        val service = buildService()

        service.tickerToUsers.getOrPut("AAPL") { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add(userId)
        service.userToTickers.getOrPut(userId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add("AAPL")

        val event = AssetUnsubscribedEvent(userId = userId, tickers = listOf("AAPL"), timestamp = Instant.now())
        service.onAssetUnsubscribed(event)

        service.tickerToUsers["AAPL"]!!.contains(userId) shouldBe false
        service.userToTickers[userId]!!.contains("AAPL") shouldBe false
    }

    test("onAssetUnsubscribed_withNoSession_doesNotThrow") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("AAPL" to "Apple Inc."))
        whenever(priceFeedGenerator.generateTick()).thenReturn(listOf(aaplSnapshot))
        whenever(repository.findAll()).thenReturn(emptyList())

        val service = buildService()

        service.tickerToUsers.getOrPut("AAPL") { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add(userId)
        service.userToTickers.getOrPut(userId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add("AAPL")

        // No session registered - should not throw
        val event = AssetUnsubscribedEvent(userId = userId, tickers = listOf("AAPL"), timestamp = Instant.now())
        service.onAssetUnsubscribed(event)
    }

    // ── removeSession ─────────────────────────────────────────────────────────────

    test("removeSession_stopsDispatchToUser") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("AAPL" to "Apple Inc."))
        whenever(priceFeedGenerator.generateTick())
            .thenReturn(listOf(aaplSnapshot))
            .thenReturn(listOf(aaplSnapshot))
        whenever(repository.findAll()).thenReturn(emptyList())

        val service = buildService()

        val session = mockSession(userId)
        service.registerSession(userId, session)
        service.tickerToUsers.getOrPut("AAPL") { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add(userId)
        service.userToTickers.getOrPut(userId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add("AAPL")

        service.removeSession(userId)

        // After removeSession, dispatchTicks should NOT send anything to that session
        service.dispatchTicks()

        verify(session, never()).sendMessage(any<TextMessage>())
    }

    // ── getSnapshotForUser ─────────────────────────────────────────────────────────

    test("getSnapshotForUser_returnsOnlySubscribedTickers") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf(
            "AAPL" to "Apple Inc.",
            "MSFT" to "Microsoft Corporation"
        ))
        whenever(priceFeedGenerator.generateTick()).thenReturn(listOf(aaplSnapshot, msftSnapshot))
        whenever(repository.findAll()).thenReturn(emptyList())

        val service = buildService()
        service.snapshotCache["AAPL"] = aaplSnapshot
        service.snapshotCache["MSFT"] = msftSnapshot

        service.userToTickers.getOrPut(userId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add("AAPL")

        val result = service.getSnapshotForUser(userId)

        result.size shouldBe 1
        result[0].ticker shouldBe "AAPL"
    }

    test("getSnapshotForUser_withNoSubscriptions_returnsEmpty") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("AAPL" to "Apple Inc."))
        whenever(priceFeedGenerator.generateTick()).thenReturn(listOf(aaplSnapshot))
        whenever(repository.findAll()).thenReturn(emptyList())

        val service = buildService()

        val result = service.getSnapshotForUser(userId)
        result.size shouldBe 0
    }

    // ── JSON shape ──────────────────────────────────────────────────────────────

    test("sendTick_jsonPayload_hasCorrectShapeAndPricesTo3dp") {
        whenever(supportedTickerConfig.getAll()).thenReturn(mapOf("AAPL" to "Apple Inc."))
        whenever(priceFeedGenerator.generateTick()).thenReturn(listOf(aaplSnapshot))
        whenever(repository.findAll()).thenReturn(emptyList())

        val service = buildService()
        val session = mockSession(userId)

        service.sendTick(session, aaplSnapshot)

        val captor = argumentCaptor<TextMessage>()
        verify(session).sendMessage(captor.capture())
        val payload = captor.firstValue.payload

        payload.contains("\"type\":\"TICK\"") shouldBe true
        payload.contains("\"ticker\":\"AAPL\"") shouldBe true
        payload.contains("\"currentPrice\":150.000") shouldBe true
    }
})
