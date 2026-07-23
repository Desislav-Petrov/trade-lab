package org.dpp.tradelab.stocktrading.service

import org.dpp.tradelab.ledger.api.LedgerAccountApi
import org.dpp.tradelab.ledger.api.LedgerApi
import org.dpp.tradelab.ledger.api.TransactionAssetType
import org.dpp.tradelab.ledger.api.TransactionType
import org.dpp.tradelab.marketdata.api.MarketDataApi
import org.dpp.tradelab.stocktrading.exception.DuplicateIdempotencyKeyException
import org.dpp.tradelab.stocktrading.exception.InsufficientHoldingException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotActiveException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotFoundException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotOwnedException
import org.dpp.tradelab.portfolio.api.PortfolioApi
import org.dpp.tradelab.stocktrading.exception.TickerNotFoundException
import org.dpp.tradelab.stocktrading.messaging.OrderFilledEvent
import org.dpp.tradelab.stocktrading.messaging.OrderRejectedEvent
import org.dpp.tradelab.stocktrading.model.Order
import org.dpp.tradelab.stocktrading.model.OrderSide
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
    private val portfolioApi: PortfolioApi,
    private val marketDataApi: MarketDataApi,
    private val eventPublisher: ApplicationEventPublisher
) {

    @Transactional(readOnly = true)
    fun getIndicativePrice(ticker: String): BigDecimal {
        if (!marketDataApi.isTickerSupported(ticker)) {
            throw TickerNotFoundException(ticker)
        }
        return marketDataApi.getCurrentPrice(ticker)
    }

    @Transactional
    fun placeOrder(
        idempotencyKey: UUID,
        accountId: UUID,
        userId: UUID,
        ticker: String,
        quantity: BigDecimal,
        side: OrderSide,
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
            side = side,
            orderType = orderType,
            status = OrderStatus.PENDING,
            priceSnapshot = priceSnapshot
        )
        orderRepository.save(order)

        // Step 9: Get execution price from market data cache
        val executionPrice = marketDataApi.getCurrentPrice(ticker)

        val settledOrder = when (side) {
            OrderSide.BUY -> processBuyOrder(order, accountSummary.balance, accountSummary.currency, executionPrice)
            OrderSide.SELL -> processSellOrder(order, accountSummary.currency, executionPrice)
        }

        if (settledOrder.status == OrderStatus.REJECTED) {
            return settledOrder
        }

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
                side = order.side,
                idempotencyKey = order.idempotencyKey,
                timestamp = Instant.now()
            )
        )

        return order
    }

    private fun processBuyOrder(
        order: Order,
        availableBalance: BigDecimal,
        currency: String,
        executionPrice: BigDecimal
    ): Order {
        val requiredCash = order.quantity.multiply(executionPrice)

        if (availableBalance < requiredCash) {
            rejectOrder(order, "Insufficient funds")
            return order
        }

        ledgerApi.recordTransaction(
            accountId = order.accountId,
            userId = order.userId,
            type = TransactionType.DEBIT,
            assetType = TransactionAssetType.CASH,
            amount = requiredCash,
            currency = currency,
            ticker = null,
            description = "Buy ${order.ticker} x${order.quantity} at $executionPrice"
        )

        ledgerApi.recordTransaction(
            accountId = order.accountId,
            userId = order.userId,
            type = TransactionType.CREDIT,
            assetType = TransactionAssetType.STOCK_BUY,
            amount = order.quantity,
            currency = currency,
            ticker = order.ticker,
            description = "Buy ${order.ticker} x${order.quantity} at $executionPrice"
        )

        return order
    }

    private fun processSellOrder(
        order: Order,
        currency: String,
        executionPrice: BigDecimal
    ): Order {
        try {
            ensureSufficientHolding(order.accountId, order.ticker, order.quantity)
        } catch (ex: InsufficientHoldingException) {
            rejectOrder(order, "Quantity exceeds holding")
            return order
        }

        val totalProceeds = order.quantity.multiply(executionPrice)

        ledgerApi.recordTransaction(
            accountId = order.accountId,
            userId = order.userId,
            type = TransactionType.DEBIT,
            assetType = TransactionAssetType.STOCK_SELL,
            amount = order.quantity,
            currency = currency,
            ticker = order.ticker,
            description = "Sell ${order.ticker} x${order.quantity}"
        )

        ledgerApi.recordTransaction(
            accountId = order.accountId,
            userId = order.userId,
            type = TransactionType.CREDIT,
            assetType = TransactionAssetType.CASH,
            amount = totalProceeds,
            currency = currency,
            ticker = null,
            description = "Sell ${order.ticker} x${order.quantity}"
        )

        return order
    }

    private fun ensureSufficientHolding(accountId: UUID, ticker: String, quantity: BigDecimal) {
        val available = portfolioApi.getPositionQuantity(accountId, ticker)
        if (available < quantity) {
            throw InsufficientHoldingException(ticker, quantity, available)
        }
    }

    private fun rejectOrder(order: Order, rejectionReason: String) {
        order.status = OrderStatus.REJECTED
        order.rejectionReason = rejectionReason
        orderRepository.save(order)
        eventPublisher.publishEvent(
            OrderRejectedEvent(
                orderId = order.orderId,
                accountId = order.accountId,
                userId = order.userId,
                ticker = order.ticker,
                quantity = order.quantity,
                side = order.side,
                rejectionReason = rejectionReason,
                timestamp = Instant.now()
            )
        )
    }
}
