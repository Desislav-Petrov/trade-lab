package org.dpp.tradelab.marketdata.controller

import org.dpp.tradelab.marketdata.exception.UnsupportedTickerException
import org.dpp.tradelab.marketdata.generated.api.MarketDataApiDelegate
import org.dpp.tradelab.marketdata.generated.model.BulkAddSubscriptionsRequest
import org.dpp.tradelab.marketdata.generated.model.BulkAddSubscriptionsResponse
import org.dpp.tradelab.marketdata.generated.model.BulkRemoveSubscriptionsRequest
import org.dpp.tradelab.marketdata.generated.model.PriceResponse
import org.dpp.tradelab.marketdata.generated.model.SubscriptionResponse
import org.dpp.tradelab.marketdata.service.AssetSubscriptionService
import org.dpp.tradelab.marketdata.service.MarketDataFeedService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class MarketDataApiDelegateImpl(
    private val service: AssetSubscriptionService,
    private val marketDataFeedService: MarketDataFeedService
) : MarketDataApiDelegate {

    override fun getSupportedTickers(): ResponseEntity<List<SubscriptionResponse>> {
        val tickers = service.getSupportedTickers()
            .map { SubscriptionResponse(ticker = it.first, companyName = it.second) }
        return ResponseEntity.ok(tickers)
    }

    override fun getSubscriptions(userId: UUID): ResponseEntity<List<SubscriptionResponse>> {
        val subscriptions = service.getSubscriptions(userId)
            .map { SubscriptionResponse(ticker = it.ticker, companyName = it.companyName) }
        return ResponseEntity.ok(subscriptions)
    }

    override fun bulkAddSubscriptions(
        bulkAddSubscriptionsRequest: BulkAddSubscriptionsRequest
    ): ResponseEntity<BulkAddSubscriptionsResponse> {
        val saved = service.bulkAdd(
            userId = bulkAddSubscriptionsRequest.userId,
            tickers = bulkAddSubscriptionsRequest.tickers
        )
        val response = BulkAddSubscriptionsResponse(
            subscriptions = saved.map { SubscriptionResponse(ticker = it.ticker, companyName = it.companyName) }
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    override fun bulkRemoveSubscriptions(
        bulkRemoveSubscriptionsRequest: BulkRemoveSubscriptionsRequest
    ): ResponseEntity<Unit> {
        service.bulkRemove(
            userId = bulkRemoveSubscriptionsRequest.userId,
            tickers = bulkRemoveSubscriptionsRequest.tickers
        )
        return ResponseEntity.noContent().build()
    }

    override fun getPrice(ticker: String): ResponseEntity<PriceResponse> {
        return try {
            val price = marketDataFeedService.getPrice(ticker)
            ResponseEntity.ok(PriceResponse(ticker = ticker.uppercase(), indicativePrice = price))
        } catch (ex: UnsupportedTickerException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }
}
