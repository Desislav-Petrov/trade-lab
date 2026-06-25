package org.dpp.tradelab.marketdata.service

import org.dpp.tradelab.marketdata.config.SupportedTickerConfig
import org.dpp.tradelab.marketdata.exception.SubscriptionLimitExceededException
import org.dpp.tradelab.marketdata.exception.SubscriptionNotFoundException
import org.dpp.tradelab.marketdata.exception.TickerAlreadySubscribedException
import org.dpp.tradelab.marketdata.exception.UnsupportedTickerException
import org.dpp.tradelab.marketdata.messaging.AssetSubscribedEvent
import org.dpp.tradelab.marketdata.messaging.AssetUnsubscribedEvent
import org.dpp.tradelab.marketdata.model.AssetSubscription
import org.dpp.tradelab.marketdata.repository.AssetSubscriptionRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AssetSubscriptionService(
    private val repository: AssetSubscriptionRepository,
    private val supportedTickerConfig: SupportedTickerConfig,
    private val eventPublisher: ApplicationEventPublisher
) {

    @Transactional(readOnly = true)
    fun getSubscriptions(userId: UUID): List<AssetSubscription> =
        repository.findAllByUserIdOrderByTickerAsc(userId)

    fun getSupportedTickers(): List<Pair<String, String>> =
        supportedTickerConfig.getAll().entries
            .map { Pair(it.key, it.value) }
            .sortedBy { it.first }

    @Transactional
    fun bulkAdd(userId: UUID, tickers: List<String>): List<AssetSubscription> {
        val resolvedNames: Map<String, String> = tickers.associateWith { ticker ->
            supportedTickerConfig.resolve(ticker)
                ?: throw UnsupportedTickerException("Ticker $ticker is not in the supported list")
        }

        val existing = repository.findAllByUserIdAndTickerIn(userId, tickers)
        if (existing.isNotEmpty()) {
            throw TickerAlreadySubscribedException("One or more tickers are already subscribed")
        }

        val count = repository.countByUserId(userId)
        if (count + tickers.size > 1000) {
            throw SubscriptionLimitExceededException("Adding these tickers would exceed your 1000 subscription limit")
        }

        val entities = tickers.map { ticker ->
            AssetSubscription(
                subscriptionId = UUID.randomUUID(),
                userId = userId,
                ticker = ticker,
                companyName = resolvedNames.getValue(ticker)
            )
        }

        val saved = repository.saveAll(entities)

        eventPublisher.publishEvent(AssetSubscribedEvent(userId = userId, tickers = tickers, timestamp = Instant.now()))

        return saved
    }

    @Transactional
    fun bulkRemove(userId: UUID, tickers: List<String>) {
        val found = repository.findAllByUserIdAndTickerIn(userId, tickers)
        if (found.size != tickers.size) {
            throw SubscriptionNotFoundException("One or more tickers not found in your subscriptions")
        }

        repository.deleteAll(found)

        eventPublisher.publishEvent(AssetUnsubscribedEvent(userId = userId, tickers = tickers, timestamp = Instant.now()))
    }
}
