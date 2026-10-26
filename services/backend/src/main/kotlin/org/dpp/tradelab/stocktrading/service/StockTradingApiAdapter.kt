package org.dpp.tradelab.stocktrading.service

import org.dpp.tradelab.marketdata.api.MarketDataApi
import org.dpp.tradelab.stocktrading.api.OrderPlacementResult
import org.dpp.tradelab.stocktrading.api.OrderPlacementSide
import org.dpp.tradelab.stocktrading.api.OrderPlacementStatus
import org.dpp.tradelab.stocktrading.api.StockTradingApi
import org.dpp.tradelab.stocktrading.model.OrderSide
import org.dpp.tradelab.stocktrading.model.OrderStatus
import org.dpp.tradelab.stocktrading.model.OrderType
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class StockTradingApiAdapter(
    private val stockTradingService: StockTradingService,
    private val marketDataApi: MarketDataApi
) : StockTradingApi {
    override fun placeMarketOrder(
        idempotencyKey: UUID,
        accountId: UUID,
        userId: UUID,
        ticker: String,
        quantity: BigDecimal,
        side: OrderPlacementSide
    ): OrderPlacementResult {
        val priceSnapshot = if (marketDataApi.isTickerSupported(ticker)) {
            marketDataApi.getCurrentPrice(ticker)
        } else {
            BigDecimal.ZERO
        }
        val order = stockTradingService.placeOrder(
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            orderType = OrderType.MARKET,
            priceSnapshot = priceSnapshot,
            side = when (side) {
                OrderPlacementSide.BUY -> OrderSide.BUY
                OrderPlacementSide.SELL -> OrderSide.SELL
            }
        )

        return when (order.status) {
            OrderStatus.FILLED -> {
                val executionPrice = requireNotNull(order.executionPrice) {
                    "Filled order ${order.orderId} has no execution price"
                }
                OrderPlacementResult(
                    orderId = order.orderId,
                    status = OrderPlacementStatus.FILLED,
                    ticker = order.ticker,
                    quantity = order.quantity,
                    side = side,
                    executionPrice = executionPrice,
                    totalAmount = order.quantity.multiply(executionPrice)
                )
            }
            OrderStatus.REJECTED -> OrderPlacementResult(
                orderId = order.orderId,
                status = OrderPlacementStatus.REJECTED,
                ticker = order.ticker,
                quantity = order.quantity,
                side = side,
                rejectionReason = requireNotNull(order.rejectionReason) {
                    "Rejected order ${order.orderId} has no rejection reason"
                }
            )
            OrderStatus.PENDING -> error("Order ${order.orderId} has not completed placement")
        }
    }
}
