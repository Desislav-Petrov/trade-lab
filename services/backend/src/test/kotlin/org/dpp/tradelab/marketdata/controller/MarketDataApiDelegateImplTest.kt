package org.dpp.tradelab.marketdata.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.dpp.tradelab.marketdata.exception.SubscriptionLimitExceededException
import org.dpp.tradelab.marketdata.exception.SubscriptionNotFoundException
import org.dpp.tradelab.marketdata.exception.TickerAlreadySubscribedException
import org.dpp.tradelab.marketdata.exception.UnsupportedTickerException
import org.dpp.tradelab.marketdata.model.AssetSubscription
import org.dpp.tradelab.marketdata.service.AssetSubscriptionService
import org.dpp.tradelab.marketdata.service.MarketDataFeedService
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class MarketDataApiDelegateImplTest(
    @Autowired val mockMvc: MockMvc,
    @MockitoBean val assetSubscriptionService: AssetSubscriptionService,
    @MockitoBean val marketDataFeedService: MarketDataFeedService
) : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    private val objectMapper = ObjectMapper()

    init {
        val userId = UUID.randomUUID()

        fun makeSubscription(ticker: String, companyName: String) = AssetSubscription(
            subscriptionId = UUID.randomUUID(),
            userId = userId,
            ticker = ticker,
            companyName = companyName
        )

        // ── GET /api/v1/market-data/supported-tickers ────────────────────────

        test("getSupportedTickers_returns200WithFullList") {
            whenever(assetSubscriptionService.getSupportedTickers()).thenReturn(
                listOf(Pair("AAPL", "Apple Inc."), Pair("MSFT", "Microsoft Corporation"))
            )

            mockMvc.perform(get("/api/v1/market-data/supported-tickers"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].companyName").value("Apple Inc."))
                .andExpect(jsonPath("$[1].ticker").value("MSFT"))
                .andExpect(jsonPath("$[1].companyName").value("Microsoft Corporation"))
        }

        // ── GET /api/v1/market-data/subscriptions ────────────────────────────

        test("getSubscriptions_withItems_returns200WithSubscriptionList") {
            val subscriptions = listOf(
                makeSubscription("AAPL", "Apple Inc."),
                makeSubscription("MSFT", "Microsoft Corp.")
            )
            whenever(assetSubscriptionService.getSubscriptions(userId)).thenReturn(subscriptions)

            mockMvc.perform(
                get("/api/v1/market-data/subscriptions")
                    .param("userId", userId.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].companyName").value("Apple Inc."))
                .andExpect(jsonPath("$[1].ticker").value("MSFT"))
                .andExpect(jsonPath("$[1].companyName").value("Microsoft Corp."))
        }

        test("getSubscriptions_empty_returns200WithEmptyList") {
            whenever(assetSubscriptionService.getSubscriptions(userId)).thenReturn(emptyList())

            mockMvc.perform(
                get("/api/v1/market-data/subscriptions")
                    .param("userId", userId.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$").isEmpty)
        }

        // ── POST /api/v1/market-data/subscriptions ───────────────────────────

        test("bulkAddSubscriptions_validRequest_returns201WithCreatedSubscriptions") {
            val tickers = listOf("AAPL", "MSFT")
            val saved = listOf(
                makeSubscription("AAPL", "Apple Inc."),
                makeSubscription("MSFT", "Microsoft Corp.")
            )
            whenever(assetSubscriptionService.bulkAdd(eq(userId), eq(tickers))).thenReturn(saved)

            val requestBody = mapOf("userId" to userId.toString(), "tickers" to tickers)

            mockMvc.perform(
                post("/api/v1/market-data/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.subscriptions[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.subscriptions[0].companyName").value("Apple Inc."))
                .andExpect(jsonPath("$.subscriptions[1].ticker").value("MSFT"))
                .andExpect(jsonPath("$.subscriptions[1].companyName").value("Microsoft Corp."))
        }

        test("bulkAddSubscriptions_alreadySubscribed_returns409") {
            whenever(assetSubscriptionService.bulkAdd(any(), any()))
                .thenThrow(TickerAlreadySubscribedException("One or more tickers are already subscribed"))

            val requestBody = mapOf("userId" to userId.toString(), "tickers" to listOf("AAPL"))

            mockMvc.perform(
                post("/api/v1/market-data/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.status").value(409))
        }

        test("bulkAddSubscriptions_unsupportedTicker_returns400") {
            whenever(assetSubscriptionService.bulkAdd(any(), any()))
                .thenThrow(UnsupportedTickerException("Ticker BADTICKER is not in the supported list"))

            val requestBody = mapOf("userId" to userId.toString(), "tickers" to listOf("BADTICKER"))

            mockMvc.perform(
                post("/api/v1/market-data/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.status").value(400))
        }

        test("bulkAddSubscriptions_subscriptionLimitExceeded_returns422") {
            whenever(assetSubscriptionService.bulkAdd(any(), any()))
                .thenThrow(SubscriptionLimitExceededException("Adding these tickers would exceed your 1000 subscription limit"))

            val requestBody = mapOf("userId" to userId.toString(), "tickers" to listOf("AAPL"))

            mockMvc.perform(
                post("/api/v1/market-data/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
            )
                .andExpect(status().`is`(422))
                .andExpect(jsonPath("$.status").value(422))
        }

        // ── DELETE /api/v1/market-data/subscriptions ─────────────────────────

        test("bulkRemoveSubscriptions_validRequest_returns204") {
            val requestBody = mapOf("userId" to userId.toString(), "tickers" to listOf("AAPL"))

            mockMvc.perform(
                delete("/api/v1/market-data/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
            )
                .andExpect(status().isNoContent)
        }

        test("bulkRemoveSubscriptions_tickerNotFound_returns404") {
            whenever(assetSubscriptionService.bulkRemove(any(), any()))
                .thenThrow(SubscriptionNotFoundException("One or more tickers not found in your subscriptions"))

            val requestBody = mapOf("userId" to userId.toString(), "tickers" to listOf("AAPL"))

            mockMvc.perform(
                delete("/api/v1/market-data/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.status").value(404))
        }

        // ── GET /api/v1/market-data/price ─────────────────────────────────────

        test("getPrice_supportedTicker_returns200WithPrice") {
            whenever(marketDataFeedService.getPrice("AAPL")).thenReturn(java.math.BigDecimal("182.500"))

            mockMvc.perform(get("/api/v1/market-data/price").param("ticker", "AAPL"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.indicativePrice").value(182.5))
        }

        test("getPrice_unsupportedTicker_returns404") {
            whenever(marketDataFeedService.getPrice("UNKNOWN"))
                .thenThrow(UnsupportedTickerException("Ticker 'UNKNOWN' is not in the supported tickers list."))

            mockMvc.perform(get("/api/v1/market-data/price").param("ticker", "UNKNOWN"))
                .andExpect(status().isNotFound)
        }
    }
}
