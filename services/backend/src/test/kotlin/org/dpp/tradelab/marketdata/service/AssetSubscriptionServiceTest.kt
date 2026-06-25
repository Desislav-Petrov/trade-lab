package org.dpp.tradelab.marketdata.service

import io.kotest.assertions.throwables.shouldThrow
import org.dpp.tradelab.marketdata.config.SupportedTickerConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.dpp.tradelab.marketdata.exception.SubscriptionLimitExceededException
import org.dpp.tradelab.marketdata.exception.SubscriptionNotFoundException
import org.dpp.tradelab.marketdata.exception.TickerAlreadySubscribedException
import org.dpp.tradelab.marketdata.exception.UnsupportedTickerException
import org.dpp.tradelab.marketdata.messaging.AssetSubscribedEvent
import org.dpp.tradelab.marketdata.messaging.AssetUnsubscribedEvent
import org.dpp.tradelab.marketdata.model.AssetSubscription
import org.dpp.tradelab.marketdata.repository.AssetSubscriptionRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

class AssetSubscriptionServiceTest : FunSpec({

    val repository = mock<AssetSubscriptionRepository>()
    val supportedTickerConfig = mock<SupportedTickerConfig>()
    val eventPublisher = mock<ApplicationEventPublisher>()
    val service = AssetSubscriptionService(repository, supportedTickerConfig, eventPublisher)

    val userId = UUID.randomUUID()

    beforeEach {
        reset(repository, supportedTickerConfig, eventPublisher)
    }

    fun makeSubscription(ticker: String, companyName: String) = AssetSubscription(
        subscriptionId = UUID.randomUUID(),
        userId = userId,
        ticker = ticker,
        companyName = companyName
    )

    // ── getSupportedTickers ──────────────────────────────────────────────────

    test("getSupportedTickers_happyPath_returnsSortedTickerPairs") {
        whenever(supportedTickerConfig.getAll()).thenReturn(
            mapOf("MSFT" to "Microsoft Corporation", "AAPL" to "Apple Inc.", "GOOGL" to "Alphabet Inc.")
        )

        val result = service.getSupportedTickers()

        result.map { it.first } shouldBe listOf("AAPL", "GOOGL", "MSFT")
        result.map { it.second } shouldBe listOf("Apple Inc.", "Alphabet Inc.", "Microsoft Corporation")
    }

    // ── getSubscriptions ─────────────────────────────────────────────────────

    test("getSubscriptions_happyPath_returnsSubscriptionsOrderedByTicker") {
        val subscriptions = listOf(
            makeSubscription("AAPL", "Apple Inc."),
            makeSubscription("MSFT", "Microsoft Corp.")
        )
        whenever(repository.findAllByUserIdOrderByTickerAsc(userId)).thenReturn(subscriptions)

        val result = service.getSubscriptions(userId)

        result shouldBe subscriptions
        verify(repository).findAllByUserIdOrderByTickerAsc(userId)
    }

    // ── bulkAdd ──────────────────────────────────────────────────────────────

    test("bulkAdd_happyPath_persistsEntitiesAndPublishesEvent") {
        val tickers = listOf("AAPL", "MSFT")
        whenever(supportedTickerConfig.resolve("AAPL")).thenReturn("Apple Inc.")
        whenever(supportedTickerConfig.resolve("MSFT")).thenReturn("Microsoft Corp.")
        whenever(repository.findAllByUserIdAndTickerIn(userId, tickers)).thenReturn(emptyList())
        whenever(repository.countByUserId(userId)).thenReturn(0L)
        whenever(repository.saveAll(any<List<AssetSubscription>>())).thenAnswer { invocation ->
            invocation.getArgument<List<AssetSubscription>>(0)
        }

        val result = service.bulkAdd(userId, tickers)

        result.map { it.ticker } shouldContainExactlyInAnyOrder tickers
        verify(repository).saveAll(any<List<AssetSubscription>>())

        val eventCaptor = argumentCaptor<AssetSubscribedEvent>()
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        eventCaptor.firstValue.userId shouldBe userId
        eventCaptor.firstValue.tickers shouldContainExactlyInAnyOrder tickers
        eventCaptor.firstValue.timestamp shouldNotBe null
    }

    test("bulkAdd_unsupportedTicker_throwsUnsupportedTickerException") {
        whenever(supportedTickerConfig.resolve("BADTICKER")).thenReturn(null)

        shouldThrow<UnsupportedTickerException> {
            service.bulkAdd(userId, listOf("BADTICKER"))
        }

        verify(repository, never()).saveAll(any<List<AssetSubscription>>())
        verify(eventPublisher, never()).publishEvent(any())
    }

    test("bulkAdd_alreadySubscribedTicker_throwsTickerAlreadySubscribedException") {
        val tickers = listOf("AAPL")
        whenever(supportedTickerConfig.resolve("AAPL")).thenReturn("Apple Inc.")
        whenever(repository.findAllByUserIdAndTickerIn(userId, tickers))
            .thenReturn(listOf(makeSubscription("AAPL", "Apple Inc.")))

        shouldThrow<TickerAlreadySubscribedException> {
            service.bulkAdd(userId, tickers)
        }

        verify(repository, never()).saveAll(any<List<AssetSubscription>>())
        verify(eventPublisher, never()).publishEvent(any())
    }

    test("bulkAdd_subscriptionLimitExceeded_throwsSubscriptionLimitExceededException") {
        val tickers = listOf("AAPL", "MSFT")
        whenever(supportedTickerConfig.resolve("AAPL")).thenReturn("Apple Inc.")
        whenever(supportedTickerConfig.resolve("MSFT")).thenReturn("Microsoft Corp.")
        whenever(repository.findAllByUserIdAndTickerIn(userId, tickers)).thenReturn(emptyList())
        whenever(repository.countByUserId(userId)).thenReturn(999L)

        shouldThrow<SubscriptionLimitExceededException> {
            service.bulkAdd(userId, tickers)
        }

        verify(repository, never()).saveAll(any<List<AssetSubscription>>())
        verify(eventPublisher, never()).publishEvent(any())
    }

    // ── bulkRemove ───────────────────────────────────────────────────────────

    test("bulkRemove_happyPath_deletesSubscriptionsAndPublishesEvent") {
        val tickers = listOf("AAPL", "MSFT")
        val found = listOf(
            makeSubscription("AAPL", "Apple Inc."),
            makeSubscription("MSFT", "Microsoft Corp.")
        )
        whenever(repository.findAllByUserIdAndTickerIn(userId, tickers)).thenReturn(found)

        service.bulkRemove(userId, tickers)

        verify(repository).deleteAll(found)

        val eventCaptor = argumentCaptor<AssetUnsubscribedEvent>()
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        eventCaptor.firstValue.userId shouldBe userId
        eventCaptor.firstValue.tickers shouldContainExactlyInAnyOrder tickers
        eventCaptor.firstValue.timestamp shouldNotBe null
    }

    test("bulkRemove_tickerNotFound_throwsSubscriptionNotFoundException") {
        val tickers = listOf("AAPL", "MSFT")
        whenever(repository.findAllByUserIdAndTickerIn(userId, tickers))
            .thenReturn(listOf(makeSubscription("AAPL", "Apple Inc.")))

        shouldThrow<SubscriptionNotFoundException> {
            service.bulkRemove(userId, tickers)
        }

        verify(repository, never()).deleteAll(any<List<AssetSubscription>>())
        verify(eventPublisher, never()).publishEvent(any())
    }
})
