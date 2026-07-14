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
        return when (order.status) {
            OrderStatus.FILLED -> PlaceOrderResponse(
                orderId = order.orderId,
                status = PlaceOrderResponse.Status.FILLED,
                ticker = order.ticker,
                quantity = order.quantity,
                side = PlaceOrderResponse.Side.valueOf(order.side.name),
                executionPrice = order.executionPrice,
                totalCost = order.executionPrice?.takeIf { order.side == OrderSide.BUY }?.let { order.quantity.multiply(it) },
                totalProceeds = order.executionPrice?.takeIf { order.side == OrderSide.SELL }?.let { order.quantity.multiply(it) },
                rejectionReason = null,
                accountId = order.accountId,
                createdAt = createdAt
            )
            OrderStatus.REJECTED -> PlaceOrderResponse(
                orderId = order.orderId,
                status = PlaceOrderResponse.Status.REJECTED,
                ticker = order.ticker,
                quantity = order.quantity,
                side = PlaceOrderResponse.Side.valueOf(order.side.name),
                executionPrice = null,
                totalCost = null,
                totalProceeds = null,
                rejectionReason = order.rejectionReason,
                accountId = order.accountId,
                createdAt = createdAt
            )
            OrderStatus.PENDING -> throw IllegalStateException(
                "Order ${order.orderId} is still PENDING after placeOrder completed — this should never happen"
            )
        }
    }
}
