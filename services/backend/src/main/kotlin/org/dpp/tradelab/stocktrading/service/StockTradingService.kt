package org.dpp.tradelab.stocktrading.service

import org.dpp.tradelab.ledger.api.LedgerAccountApi
import org.dpp.tradelab.ledger.api.LedgerApi
import org.dpp.tradelab.ledger.api.TransactionAssetType
import org.dpp.tradelab.ledger.api.TransactionType
import org.dpp.tradelab.marketdata.api.MarketDataApi
import org.dpp.tradelab.stocktrading.exception.DuplicateIdempotencyKeyException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotActiveException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotFoundException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotOwnedException
import org.dpp.tradelab.stocktrading.exception.TickerNotFoundException
import org.dpp.tradelab.stocktrading.messaging.OrderFilledEvent
import org.dpp.tradelab.stocktrading.messaging.OrderRejectedEvent
import org.dpp.tradelab.stocktrading.messaging.OrderType as OrderSideType
import org.dpp.tradelab.stocktrading.model.Order
import org.dpp.tradelab.stocktrading.model.OrderStatus
import org.dpp.tradelab.stocktrading.model.OrderType
import org.dpp.tradelab.stocktrading.repository.OrderRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class StockTradingService(
    private val orderRepository: OrderRepository,
    private val ledgerApi: LedgerApi,
    private val ledgerAccountApi: LedgerAccountApi,
    private val marketDataApi: MarketDataApi,
    private val eventPublisher: ApplicationEventPublisher
) {

    @Transactional
    fun placeOrder(
        idempotencyKey: UUID,
        accountId: UUID,
        userId: UUID,
        ticker: String,
        quantity: BigDecimal,
        orderType: OrderType,
        priceSnapshot: BigDecimal
    ): Order {
        // Step 1: Validate quantity > 0
        require(quantity > BigDecimal.ZERO) { "quantity must be greater than zero" }

        // Step 2: Validate orderType == MARKET
        require(orderType == OrderType.MARKET) { "orderType must be MARKET" }

        // Step 3: Check ticker is supported
        if (!marketDataApi.isTickerSupported(ticker)) {
            throw TickerNotFoundException(ticker)
        }

        // Step 4: Get account — throws OrderAccountNotFoundException if not found
        val accountSummary = try {
            ledgerAccountApi.getAccount(accountId)
        } catch (ex: org.dpp.tradelab.ledger.exception.AccountNotFoundException) {
            throw OrderAccountNotFoundException(accountId)
        }

        // Step 5: Check account.userId == userId
        if (accountSummary.userId != userId) {
            throw OrderAccountNotOwnedException(accountId, userId)
        }

        // Step 6: Check account is active
        if (accountSummary.status.lowercase() != "active") {
            throw OrderAccountNotActiveException(accountId)
        }

        // Step 7: Check idempotency key
        if (orderRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw DuplicateIdempotencyKeyException(idempotencyKey)
        }

        // Step 8: Save Order with status=PENDING
        val order = Order(
            orderId = UUID.randomUUID(),
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            orderType = orderType,
            status = OrderStatus.PENDING,
            priceSnapshot = priceSnapshot
        )
        orderRepository.save(order)

        // Step 9: Get execution price from market data cache
        val executionPrice = marketDataApi.getCurrentPrice(ticker)

        // Step 10: Calculate required cash
        val requiredCash = quantity.multiply(executionPrice)

        // Step 11: Fund check
        if (accountSummary.balance < requiredCash) {
            order.status = OrderStatus.REJECTED
            order.rejectionReason = "Insufficient funds"
            orderRepository.save(order)
            eventPublisher.publishEvent(
                OrderRejectedEvent(
                    orderId = order.orderId,
                    accountId = accountId,
                    userId = userId,
                    ticker = ticker,
                    quantity = quantity,
                    rejectionReason = "Insufficient funds",
                    timestamp = Instant.now()
                )
            )
            return order
        }

        // Step 12: Record DEBIT/CASH ledger entry (deduct cash)
        ledgerApi.recordTransaction(
            accountId = accountId,
            userId = userId,
            type = TransactionType.DEBIT,
            assetType = TransactionAssetType.CASH,
            amount = requiredCash,
            currency = accountSummary.currency,
            ticker = null,
            description = "Buy $ticker x$quantity at $executionPrice"
        )

        // Step 13: Record CREDIT/STOCK_BUY ledger entry (add shares)
        ledgerApi.recordTransaction(
            accountId = accountId,
            userId = userId,
            type = TransactionType.CREDIT,
            assetType = TransactionAssetType.STOCK_BUY,
            amount = quantity,
            currency = accountSummary.currency,
            ticker = ticker,
            description = "Buy $ticker x$quantity at $executionPrice"
        )

        // Step 14: Update order to FILLED
        order.status = OrderStatus.FILLED
        order.executionPrice = executionPrice
        orderRepository.save(order)

        eventPublisher.publishEvent(
            OrderFilledEvent(
                orderId = order.orderId,
                accountId = accountId,
                userId = userId,
                ticker = ticker,
                quantity = quantity,
                executionPrice = executionPrice,
                idempotencyKey = order.idempotencyKey,
                side = OrderSideType.BUY,
                timestamp = Instant.now()
            )
        )

        return order
    }
}
