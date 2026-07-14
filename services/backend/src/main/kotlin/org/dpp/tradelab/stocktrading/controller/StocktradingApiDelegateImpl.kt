package org.dpp.tradelab.stocktrading.controller

import org.dpp.tradelab.stocktrading.generated.api.StockOrdersApiDelegate
import org.dpp.tradelab.stocktrading.generated.model.PlaceOrderRequest
import org.dpp.tradelab.stocktrading.generated.model.PlaceOrderResponse
import org.dpp.tradelab.stocktrading.mapper.OrderMapper
import org.dpp.tradelab.stocktrading.model.OrderSide
import org.dpp.tradelab.stocktrading.model.OrderType
import org.dpp.tradelab.stocktrading.service.StockTradingService
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class StocktradingApiDelegateImpl(
    private val stockTradingService: StockTradingService,
    private val orderMapper: OrderMapper
) : StockOrdersApiDelegate {

    override fun placeOrder(
        idempotencyKey: UUID,
        placeOrderRequest: PlaceOrderRequest
    ): ResponseEntity<PlaceOrderResponse> {
        val side = OrderSide.valueOf(placeOrderRequest.side.value)
        val order = stockTradingService.placeOrder(
            idempotencyKey = idempotencyKey,
            accountId = placeOrderRequest.accountId,
            userId = placeOrderRequest.userId,
            ticker = placeOrderRequest.ticker,
            quantity = placeOrderRequest.quantity,
            orderType = OrderType.valueOf(placeOrderRequest.orderType.value),
            priceSnapshot = placeOrderRequest.priceSnapshot,
            side = side
        )
        return ResponseEntity.ok(orderMapper.toPlaceOrderResponse(order))
    }
}
