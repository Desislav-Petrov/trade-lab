package org.dpp.tradelab.stocktrading.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.marketdata.api.MarketDataApi
import org.dpp.tradelab.stocktrading.api.OrderPlacementResult
import org.dpp.tradelab.stocktrading.api.OrderPlacementSide
import org.dpp.tradelab.stocktrading.api.OrderPlacementStatus
import org.dpp.tradelab.stocktrading.exception.DuplicateIdempotencyKeyException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotActiveException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotFoundException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotOwnedException
import org.dpp.tradelab.stocktrading.exception.TickerNotFoundException
import org.dpp.tradelab.stocktrading.model.Order
import org.dpp.tradelab.stocktrading.model.OrderSide
import org.dpp.tradelab.stocktrading.model.OrderStatus
import org.dpp.tradelab.stocktrading.model.OrderType
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class StockTradingApiAdapterTest : FunSpec({
    val stockTradingService = mock<StockTradingService>()
    val marketDataApi = mock<MarketDataApi>()
    val adapter = StockTradingApiAdapter(stockTradingService, marketDataApi)

    val idempotencyKey = UUID.randomUUID()
    val accountId = UUID.randomUUID()
    val userId = UUID.randomUUID()
    val orderId = UUID.randomUUID()
    val ticker = "AAPL"
    val quantity = BigDecimal("2.0000")
    val priceSnapshot = BigDecimal("180.0000")
    val executionPrice = BigDecimal("182.5000")

    fun order(
        status: OrderStatus,
        executionPrice: BigDecimal? = null,
        rejectionReason: String? = null
    ) = Order(
        orderId = orderId,
        idempotencyKey = idempotencyKey,
        accountId = accountId,
        userId = userId,
        ticker = ticker,
        quantity = quantity,
        orderType = OrderType.MARKET,
        side = OrderSide.BUY,
        status = status,
        priceSnapshot = priceSnapshot,
        executionPrice = executionPrice,
        rejectionReason = rejectionReason
    )

    beforeEach {
        reset(stockTradingService, marketDataApi)
        whenever(marketDataApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(marketDataApi.getCurrentPrice(ticker)).thenReturn(priceSnapshot)
    }

    test("placeMarketOrder_filled_mapsOrderAndUsesCurrentPriceSnapshot") {
        whenever(
            stockTradingService.placeOrder(
                idempotencyKey,
                accountId,
                userId,
                ticker,
                quantity,
                OrderType.MARKET,
                priceSnapshot,
                OrderSide.BUY
            )
        ).thenReturn(order(OrderStatus.FILLED, executionPrice))

        adapter.placeMarketOrder(idempotencyKey, accountId, userId, ticker, quantity, OrderPlacementSide.BUY) shouldBe
            OrderPlacementResult(
                orderId = orderId,
                status = OrderPlacementStatus.FILLED,
                ticker = ticker,
                quantity = quantity,
                side = OrderPlacementSide.BUY,
                executionPrice = executionPrice,
                totalAmount = quantity.multiply(executionPrice)
            )

        verify(marketDataApi).getCurrentPrice(ticker)
        verify(stockTradingService).placeOrder(
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            orderType = OrderType.MARKET,
            priceSnapshot = priceSnapshot,
            side = OrderSide.BUY
        )
    }

    test("placeMarketOrder_rejected_mapsReasonWithoutFillAmounts") {
        whenever(
            stockTradingService.placeOrder(
                idempotencyKey,
                accountId,
                userId,
                ticker,
                quantity,
                OrderType.MARKET,
                priceSnapshot,
                OrderSide.SELL
            )
        ).thenReturn(order(OrderStatus.REJECTED, rejectionReason = "Quantity exceeds holding"))

        adapter.placeMarketOrder(idempotencyKey, accountId, userId, ticker, quantity, OrderPlacementSide.SELL) shouldBe
            OrderPlacementResult(
                orderId = orderId,
                status = OrderPlacementStatus.REJECTED,
                ticker = ticker,
                quantity = quantity,
                side = OrderPlacementSide.SELL,
                rejectionReason = "Quantity exceeds holding"
            )
    }

    test("placeMarketOrder_serviceExceptions_propagateUnchanged") {
        val exceptions = listOf(
            TickerNotFoundException(ticker),
            OrderAccountNotFoundException(accountId),
            OrderAccountNotOwnedException(accountId, userId),
            OrderAccountNotActiveException(accountId),
            DuplicateIdempotencyKeyException(idempotencyKey)
        )

        exceptions.forEach { exception ->
            reset(stockTradingService, marketDataApi)
            whenever(marketDataApi.isTickerSupported(ticker)).thenReturn(true)
            whenever(marketDataApi.getCurrentPrice(ticker)).thenReturn(priceSnapshot)
            whenever(
                stockTradingService.placeOrder(
                    idempotencyKey,
                    accountId,
                    userId,
                    ticker,
                    quantity,
                    OrderType.MARKET,
                    priceSnapshot,
                    OrderSide.BUY
                )
            ).thenThrow(exception)

            shouldThrow<RuntimeException> {
                adapter.placeMarketOrder(idempotencyKey, accountId, userId, ticker, quantity, OrderPlacementSide.BUY)
            } shouldBe exception
        }
    }

    test("placeMarketOrder_unsupportedTicker_reachesSharedServiceValidation") {
        reset(stockTradingService, marketDataApi)
        whenever(marketDataApi.isTickerSupported(ticker)).thenReturn(false)
        val exception = TickerNotFoundException(ticker)
        whenever(
            stockTradingService.placeOrder(
                idempotencyKey,
                accountId,
                userId,
                ticker,
                quantity,
                OrderType.MARKET,
                BigDecimal.ZERO,
                OrderSide.BUY
            )
        ).thenThrow(exception)

        shouldThrow<TickerNotFoundException> {
            adapter.placeMarketOrder(idempotencyKey, accountId, userId, ticker, quantity, OrderPlacementSide.BUY)
        } shouldBe exception
        verify(marketDataApi, org.mockito.kotlin.never()).getCurrentPrice(ticker)
    }
})
