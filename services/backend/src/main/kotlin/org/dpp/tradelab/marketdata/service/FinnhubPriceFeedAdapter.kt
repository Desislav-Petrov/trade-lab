package org.dpp.tradelab.marketdata.service

import jakarta.annotation.PostConstruct
import org.dpp.tradelab.marketdata.generated.finnhub.model.QuoteResponse
import org.dpp.tradelab.marketdata.messaging.MarketDataTickEvent
import org.dpp.tradelab.marketdata.model.MarketDataSnapshot
import org.dpp.tradelab.user.model.FeedType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.io.ClassPathResource
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * Real market data feed adapter powered by Finnhub's `/quote` REST endpoint.
 *
 * On startup ([PostConstruct]) the supported ticker universe is loaded from the classpath
 * CSV file and a round-robin cursor is initialised at 0. Every second the cursor advances
 * (wrapping at the end of the list) and the current ticker is fetched from Finnhub.
 * The response is mapped to a [MarketDataSnapshot] and published to the Spring application
 * event bus via [emitSnapshotEvent] so that users with [FeedType.REAL] receive the updated tick.
 *
 * Any Finnhub API failure is silently dropped — the cache is not modified and the cursor
 * still advances on the next invocation.
 *
 * HTTP traffic (method, URI, request body, response status, response body) is logged at
 * DEBUG level by the [org.dpp.tradelab.marketdata.config.FinnhubLoggingInterceptor] which
 * is wired into the injected [RestClient] bean. The API key is owned by
 * [org.dpp.tradelab.marketdata.config.FinnhubRestClientConfig] and applied as a default
 * header on the client — this class has no knowledge of it.
 */
@Component
@ConditionalOnProperty(name = ["app.features.enable-real-data"], havingValue = "true")
class FinnhubPriceFeedAdapter(
    private val eventPublisher: ApplicationEventPublisher,
    @Qualifier("finnhubRestClient") private val restClient: RestClient
) : MarketDataFeedAdapter {

    private val logger = LoggerFactory.getLogger(FinnhubPriceFeedAdapter::class.java)

    internal var tickers: List<String> = emptyList()
    internal var companyNames: Map<String, String> = emptyMap()
    internal val cursor = AtomicInteger(0)

    // Overridable in tests via direct assignment
    internal var quoteSupplier: (String) -> QuoteResponse? = { ticker ->
        restClient
            .get()
            .uri("/quote?symbol={symbol}", ticker)
            .retrieve()
            .body(QuoteResponse::class.java)
    }

    @PostConstruct
    fun init() {
        val nameMap = mutableMapOf<String, String>()
        val resource = ClassPathResource("supported-tickers.csv")
        resource.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    val commaIndex = trimmed.indexOf(',')
                    if (commaIndex > 0) {
                        val ticker = trimmed.substring(0, commaIndex).trim().uppercase()
                        val companyName = trimmed.substring(commaIndex + 1).trim()
                        nameMap[ticker] = companyName
                    }
                }
            }
        }
        companyNames = nameMap
        tickers = nameMap.keys.toList()
        cursor.set(0)
        logger.info("FinnhubPriceFeedAdapter initialised with ${tickers.size} tickers")
    }

    @Scheduled(fixedDelay = 1000)
    fun fetchAndDispatch() {
        if (tickers.isEmpty()) return

        val index = cursor.getAndUpdate { current ->
            if (current + 1 >= tickers.size) 0 else current + 1
        }
        val ticker = tickers[index]

        val response = try {
            quoteSupplier(ticker)
        } catch (ex: Exception) {
            logger.debug("Finnhub quote fetch failed for ticker={} — dropping tick: {}", ticker, ex.message)
            return
        }

        if (response == null) return

        val currentPrice = response.c ?: return
        val open = response.o ?: BigDecimal.ZERO
        val dayHigh = response.h ?: BigDecimal.ZERO
        val dayLow = response.l ?: BigDecimal.ZERO

        val companyName = companyNames[ticker] ?: run {
            logger.warn("No company name found for ticker={} in supported-tickers.csv", ticker)
            ""
        }

        val snapshot = MarketDataSnapshot(
            ticker = ticker,
            companyName = companyName,
            currentPrice = currentPrice,
            open = open,
            dayHigh = dayHigh,
            dayLow = dayLow,
            fiftyTwoWeekHigh = dayHigh,
            updatedAt = Instant.now()
        )

        emitSnapshotEvent(snapshot)
    }

    override fun emitSnapshotEvent(snapshot: MarketDataSnapshot) {
        eventPublisher.publishEvent(MarketDataTickEvent(snapshot = snapshot, feedType = FeedType.REAL))
    }
}
