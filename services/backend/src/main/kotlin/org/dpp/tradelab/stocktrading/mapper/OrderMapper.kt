package org.dpp.tradelab.stocktrading.mapper

import org.dpp.tradelab.stocktrading.generated.model.PlaceOrderResponse
import org.dpp.tradelab.stocktrading.model.Order
import org.dpp.tradelab.stocktrading.model.OrderSide
import org.dpp.tradelab.stocktrading.model.OrderStatus
import org.springframework.stereotype.Component
import java.time.ZoneOffset

@Component
class OrderMapper {
    fun toPlaceOrderResponse(order: Order): PlaceOrderResponse {
        val createdAt = order.createdAt?.atOffset(ZoneOffset.UTC)
            ?: java.time.OffsetDateTime.now(ZoneOffset.UTC)
        val orderSide = if (order.side == OrderSide.BUY) {
            PlaceOrderResponse.Side.BUY
        } else {
            PlaceOrderResponse.Side.SELL
        }
        return when (order.status) {
            OrderStatus.FILLED -> PlaceOrderResponse(
                orderId = order.orderId,
                status = PlaceOrderResponse.Status.FILLED,
                ticker = order.ticker,
                quantity = order.quantity,
                executionPrice = order.executionPrice,
                totalCost = if (order.side == OrderSide.BUY) order.executionPrice?.let { order.quantity.multiply(it) } else null,
                totalProceeds = if (order.side == OrderSide.SELL) order.executionPrice?.let { order.quantity.multiply(it) } else null,
                rejectionReason = null,
                accountId = order.accountId,
                createdAt = createdAt,
                side = orderSide
            )
            OrderStatus.REJECTED -> PlaceOrderResponse(
                orderId = order.orderId,
                status = PlaceOrderResponse.Status.REJECTED,
                ticker = order.ticker,
                quantity = order.quantity,
                executionPrice = null,
                totalCost = null,
                totalProceeds = null,
                rejectionReason = order.rejectionReason,
                accountId = order.accountId,
                createdAt = createdAt,
                side = orderSide
            )
            OrderStatus.PENDING -> throw IllegalStateException(
                "Order ${order.orderId} is still PENDING after placeOrder completed — this should never happen"
            )
        }
    }
}
