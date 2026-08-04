package org.dpp.tradelab.marketdata.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.marketdata.generated.finnhub.model.QuoteResponse
import org.dpp.tradelab.marketdata.messaging.MarketDataTickEvent
import org.dpp.tradelab.marketdata.model.MarketDataSnapshot
import org.dpp.tradelab.user.model.FeedType
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.web.client.RestClient
import java.math.BigDecimal

class FinnhubPriceFeedAdapterTest : FunSpec({

    fun buildAdapter(
        tickerList: List<String> = listOf("AAPL"),
        names: Map<String, String> = mapOf("AAPL" to "Apple Inc."),
        supplier: (String) -> QuoteResponse? = { null }
    ): Pair<ApplicationEventPublisher, FinnhubPriceFeedAdapter> {
        val publisher = mock<ApplicationEventPublisher>()
        val restClient = mock<RestClient>()
        val adapter = FinnhubPriceFeedAdapter(publisher, "demo", restClient)
        adapter.init()
        adapter.tickers = tickerList
        adapter.companyNames = names
        adapter.cursor.set(0)
        adapter.quoteSupplier = supplier
        return publisher to adapter
    }

    test("init_loadsTickersFromCsvAndInitialisesRoundRobinCursorAtZero") {
        val publisher = mock<ApplicationEventPublisher>()
        val restClient = mock<RestClient>()
        val adapter = FinnhubPriceFeedAdapter(publisher, "demo", restClient)
        adapter.init()

        adapter.tickers.isEmpty() shouldBe false
        adapter.tickers.contains("AAPL") shouldBe true
        adapter.cursor.get() shouldBe 0
    }

    test("fetchAndDispatch_successfulResponse_publishesMarketDataTickEventWithFeedTypeReal") {
        val quoteResponse = QuoteResponse(
            c = BigDecimal("182.500"),
            o = BigDecimal("180.000"),
            h = BigDecimal("185.000"),
            l = BigDecimal("179.000"),
            d = null, dp = null, pc = null
        )
        val (publisher, adapter) = buildAdapter(
            tickerList = listOf("AAPL"),
            names = mapOf("AAPL" to "Apple Inc."),
            supplier = { quoteResponse }
        )

        adapter.fetchAndDispatch()

        val captor = argumentCaptor<MarketDataTickEvent>()
        verify(publisher).publishEvent(captor.capture())
        val event = captor.firstValue
        event.feedType shouldBe FeedType.REAL
        val snapshot = event.snapshot
        snapshot.ticker shouldBe "AAPL"
        snapshot.companyName shouldBe "Apple Inc."
        snapshot.currentPrice shouldBe BigDecimal("182.500")
        snapshot.open shouldBe BigDecimal("180.000")
        snapshot.dayHigh shouldBe BigDecimal("185.000")
        snapshot.dayLow shouldBe BigDecimal("179.000")
        snapshot.fiftyTwoWeekHigh shouldBe BigDecimal("185.000")
    }

    test("fetchAndDispatch_apiFails_doesNotPublishEvent") {
        val (publisher, adapter) = buildAdapter(
            supplier = { throw RuntimeException("Finnhub API error") }
        )

        adapter.fetchAndDispatch()

        verify(publisher, never()).publishEvent(org.mockito.kotlin.any<MarketDataTickEvent>())
    }

    test("fetchAndDispatch_missingCompanyName_setsEmptyStringAndLogsWarn") {
        val quoteResponse = QuoteResponse(c = BigDecimal("50.000"), o = BigDecimal("49.000"),
            h = BigDecimal("51.000"), l = BigDecimal("48.000"), d = null, dp = null, pc = null)
        val (publisher, adapter) = buildAdapter(
            tickerList = listOf("UNKWN"),
            names = emptyMap(),
            supplier = { quoteResponse }
        )

        adapter.fetchAndDispatch()

        val captor = argumentCaptor<MarketDataTickEvent>()
        verify(publisher).publishEvent(captor.capture())
        captor.firstValue.snapshot.companyName shouldBe ""
    }

    test("fetchAndDispatch_roundRobinWraps_cyclesThroughAllTickers") {
        val capturedTickers = mutableListOf<String>()
        val quote = QuoteResponse(c = BigDecimal("100.000"), o = BigDecimal("99.000"),
            h = BigDecimal("101.000"), l = BigDecimal("98.000"), d = null, dp = null, pc = null)
        val (_, adapter) = buildAdapter(
            tickerList = listOf("AAPL", "MSFT"),
            names = mapOf("AAPL" to "Apple Inc.", "MSFT" to "Microsoft"),
            supplier = { ticker -> capturedTickers.add(ticker); quote }
        )
        adapter.cursor.set(0)

        // First call: index 0 -> AAPL, cursor becomes 1
        adapter.fetchAndDispatch()
        adapter.cursor.get() shouldBe 1

        // Second call: index 1 -> MSFT, cursor wraps to 0
        adapter.fetchAndDispatch()
        adapter.cursor.get() shouldBe 0

        // Third call: index 0 -> AAPL again, cursor becomes 1
        adapter.fetchAndDispatch()
        adapter.cursor.get() shouldBe 1

        capturedTickers shouldBe listOf("AAPL", "MSFT", "AAPL")
    }
})
