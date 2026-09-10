package org.dpp.tradelab.stocktrading.service

import org.dpp.tradelab.ledger.api.LedgerAccountApi
import org.dpp.tradelab.ledger.api.LedgerApi
import org.dpp.tradelab.ledger.api.TransactionAssetType
import org.dpp.tradelab.ledger.api.TransactionType
import org.dpp.tradelab.marketdata.api.MarketDataApi
import org.dpp.tradelab.portfolio.api.PortfolioApi
import org.dpp.tradelab.stocktrading.exception.DuplicateIdempotencyKeyException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotActiveException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotFoundException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotOwnedException
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
    private val marketDataApi: MarketDataApi,
    private val portfolioApi: PortfolioApi,
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
        priceSnapshot: BigDecimal,
        side: OrderSide
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

        return if (side == OrderSide.BUY) {
            placeBuyOrder(idempotencyKey, accountId, userId, ticker, quantity, orderType, priceSnapshot, accountSummary)
        } else {
            placeSellOrder(idempotencyKey, accountId, userId, ticker, quantity, orderType, priceSnapshot, accountSummary)
        }
    }

    private fun placeBuyOrder(
        idempotencyKey: UUID,
        accountId: UUID,
        userId: UUID,
        ticker: String,
        quantity: BigDecimal,
        orderType: OrderType,
        priceSnapshot: BigDecimal,
        accountSummary: org.dpp.tradelab.ledger.api.AccountSummary
    ): Order {
        val order = createPendingOrder(
            idempotencyKey, accountId, userId, ticker, quantity, orderType, priceSnapshot, OrderSide.BUY
        )

        val executionPrice = marketDataApi.getCurrentPrice(ticker)
        val requiredCash = quantity.multiply(executionPrice)

        // Fund check
        if (accountSummary.balance < requiredCash) {
            return reject(order, "Insufficient funds")
        }

        // Record DEBIT/CASH ledger entry (deduct cash)
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

        // Record CREDIT/STOCK_BUY ledger entry (add shares)
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

        return fill(order, executionPrice)
    }

    private fun placeSellOrder(
        idempotencyKey: UUID,
        accountId: UUID,
        userId: UUID,
        ticker: String,
        quantity: BigDecimal,
        orderType: OrderType,
        priceSnapshot: BigDecimal,
        accountSummary: org.dpp.tradelab.ledger.api.AccountSummary
    ): Order {
        val order = createPendingOrder(
            idempotencyKey, accountId, userId, ticker, quantity, orderType, priceSnapshot, OrderSide.SELL
        )

        val executionPrice = marketDataApi.getCurrentPrice(ticker)

        // Check holding quantity
        val positionQuantity = portfolioApi.getPositionQuantity(accountId, ticker)
        if (positionQuantity < quantity) {
            return reject(order, "Quantity exceeds holding")
        }

        // Record DEBIT/STOCK_SELL ledger entry (remove shares)
        ledgerApi.recordTransaction(
            accountId = accountId,
            userId = userId,
            type = TransactionType.DEBIT,
            assetType = TransactionAssetType.STOCK_SELL,
            amount = quantity,
            currency = accountSummary.currency,
            ticker = ticker,
            description = "Sell $ticker x$quantity"
        )

        // Record CREDIT/CASH ledger entry (add cash proceeds)
        val totalProceeds = quantity.multiply(executionPrice)
        ledgerApi.recordTransaction(
            accountId = accountId,
            userId = userId,
            type = TransactionType.CREDIT,
            assetType = TransactionAssetType.CASH,
            amount = totalProceeds,
            currency = accountSummary.currency,
            ticker = null,
            description = "Sell $ticker x$quantity"
        )

        return fill(order, executionPrice)
    }

    /** Builds and persists a new PENDING order for the given side. */
    private fun createPendingOrder(
        idempotencyKey: UUID,
        accountId: UUID,
        userId: UUID,
        ticker: String,
        quantity: BigDecimal,
        orderType: OrderType,
        priceSnapshot: BigDecimal,
        side: OrderSide
    ): Order {
        val order = Order(
            orderId = UUID.randomUUID(),
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            orderType = orderType,
            side = side,
            status = OrderStatus.PENDING,
            priceSnapshot = priceSnapshot
        )
        orderRepository.save(order)
        return order
    }

    /** Marks an order REJECTED, persists it, and publishes an OrderRejectedEvent. */
    private fun reject(order: Order, rejectionReason: String): Order {
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
                rejectionReason = rejectionReason,
                side = order.side,
                timestamp = Instant.now()
            )
        )
        return order
    }

    /** Marks an order FILLED at the execution price, persists it, and publishes an OrderFilledEvent. */
    private fun fill(order: Order, executionPrice: BigDecimal): Order {
        order.status = OrderStatus.FILLED
        order.executionPrice = executionPrice
        orderRepository.save(order)
        eventPublisher.publishEvent(
            OrderFilledEvent(
                orderId = order.orderId,
                accountId = order.accountId,
                userId = order.userId,
                ticker = order.ticker,
                quantity = order.quantity,
                executionPrice = executionPrice,
                idempotencyKey = order.idempotencyKey,
                side = order.side,
                timestamp = Instant.now()
            )
        )
        return order
    }
}
