package org.dpp.tradelab.stocktrading.controller

import org.dpp.tradelab.stocktrading.generated.api.StockOrdersApiDelegate
import org.dpp.tradelab.stocktrading.generated.model.PlaceOrderRequest
import org.dpp.tradelab.stocktrading.generated.model.PlaceOrderResponse
import org.dpp.tradelab.stocktrading.model.OrderStatus
import org.dpp.tradelab.stocktrading.model.OrderType
import org.dpp.tradelab.stocktrading.service.StockTradingService
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.time.ZoneOffset
import java.util.UUID

@Service
class StocktradingApiDelegateImpl(
    private val stockTradingService: StockTradingService
) : StockOrdersApiDelegate {

    override fun placeOrder(
        idempotencyKey: UUID,
        placeOrderRequest: PlaceOrderRequest
    ): ResponseEntity<PlaceOrderResponse> {
        val order = stockTradingService.placeOrder(
            idempotencyKey = idempotencyKey,
            accountId = placeOrderRequest.accountId,
            userId = placeOrderRequest.userId,
            ticker = placeOrderRequest.ticker,
            quantity = placeOrderRequest.quantity,
            orderType = OrderType.valueOf(placeOrderRequest.orderType.value),
            priceSnapshot = placeOrderRequest.priceSnapshot
        )

        val createdAt = order.createdAt?.atOffset(ZoneOffset.UTC)
            ?: java.time.OffsetDateTime.now(ZoneOffset.UTC)

        val response = when (order.status) {
            OrderStatus.FILLED -> PlaceOrderResponse(
                orderId = order.orderId,
                status = PlaceOrderResponse.Status.FILLED,
                ticker = order.ticker,
                quantity = order.quantity,
                executionPrice = order.executionPrice,
                totalCost = order.executionPrice?.let { order.quantity.multiply(it) },
                rejectionReason = null,
                accountId = order.accountId,
                createdAt = createdAt
            )
            OrderStatus.REJECTED -> PlaceOrderResponse(
                orderId = order.orderId,
                status = PlaceOrderResponse.Status.REJECTED,
                ticker = order.ticker,
                quantity = order.quantity,
                executionPrice = null,
                totalCost = null,
                rejectionReason = order.rejectionReason,
                accountId = order.accountId,
                createdAt = createdAt
            )
            OrderStatus.PENDING -> throw IllegalStateException(
                "Order ${order.orderId} is still PENDING after placeOrder completed — this should never happen"
            )
        }

        return ResponseEntity.ok(response)
    }
}
