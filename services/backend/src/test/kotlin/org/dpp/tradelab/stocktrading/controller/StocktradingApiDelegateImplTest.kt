package org.dpp.tradelab.stocktrading.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.dpp.tradelab.stocktrading.exception.DuplicateIdempotencyKeyException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotActiveException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotFoundException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotOwnedException
import org.dpp.tradelab.stocktrading.exception.TickerNotFoundException
import org.dpp.tradelab.stocktrading.model.Order
import org.dpp.tradelab.stocktrading.model.OrderStatus
import org.dpp.tradelab.stocktrading.model.OrderType
import org.dpp.tradelab.stocktrading.service.StockTradingService
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class StocktradingApiDelegateImplTest(
    @Autowired val mockMvc: MockMvc,
    @MockitoBean val stockTradingService: StockTradingService
) : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    private val objectMapper = ObjectMapper()

    init {
        val accountId = UUID.randomUUID()
        val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val idempotencyKey = UUID.randomUUID()
        val orderId = UUID.randomUUID()

        fun buildValidRequestBody() = mapOf(
            "accountId" to accountId.toString(),
            "userId" to userId.toString(),
            "ticker" to "AAPL",
            "quantity" to 2.5,
            "orderType" to "MARKET",
            "priceSnapshot" to 182.5,
            "side" to "BUY"
        )

        fun buildFilledOrder(): Order = Order(
            orderId = orderId,
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = "AAPL",
            quantity = BigDecimal("2.5000"),
            orderType = OrderType.MARKET,
            side = org.dpp.tradelab.stocktrading.model.OrderSide.BUY,
            status = OrderStatus.FILLED,
            priceSnapshot = BigDecimal("182.500"),
            executionPrice = BigDecimal("183.000"),
            createdAt = Instant.parse("2026-07-08T12:00:00Z")
        )

        fun buildRejectedOrder(): Order = Order(
            orderId = orderId,
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = "AAPL",
            quantity = BigDecimal("2.5000"),
            orderType = OrderType.MARKET,
            side = org.dpp.tradelab.stocktrading.model.OrderSide.BUY,
            status = OrderStatus.REJECTED,
            priceSnapshot = BigDecimal("182.500"),
            rejectionReason = "Insufficient funds",
            createdAt = Instant.parse("2026-07-08T12:00:00Z")
        )

        test("placeOrder_filledOrder_returns200WithFilledResponse") {
            whenever(stockTradingService.placeOrder(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildFilledOrder())

            mockMvc.perform(
                post("/api/v1/stock-orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey.toString())
                    .content(objectMapper.writeValueAsString(buildValidRequestBody()))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.executionPrice").value(183.0))
                .andExpect(jsonPath("$.totalCost").exists())
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist())
        }

        test("placeOrder_sellFilledOrder_returns200WithTotalProceeds") {
            val sellFilledOrder = Order(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = "AAPL",
                quantity = BigDecimal("2.5000"),
                orderType = OrderType.MARKET,
                side = org.dpp.tradelab.stocktrading.model.OrderSide.SELL,
                status = OrderStatus.FILLED,
                priceSnapshot = BigDecimal("182.500"),
                executionPrice = BigDecimal("183.000"),
                createdAt = Instant.parse("2026-07-08T12:00:00Z")
            )
            whenever(stockTradingService.placeOrder(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(sellFilledOrder)

            val sellRequestBody = buildValidRequestBody() + mapOf("side" to "SELL")
            mockMvc.perform(
                post("/api/v1/stock-orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey.toString())
                    .content(objectMapper.writeValueAsString(sellRequestBody))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.side").value("SELL"))
                .andExpect(jsonPath("$.totalProceeds").exists())
        }

        test("placeOrder_sellRejectedOrder_returns200WithQuantityExceedsHolding") {
            val sellRejectedOrder = Order(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = "AAPL",
                quantity = BigDecimal("2.5000"),
                orderType = OrderType.MARKET,
                side = org.dpp.tradelab.stocktrading.model.OrderSide.SELL,
                status = OrderStatus.REJECTED,
                priceSnapshot = BigDecimal("182.500"),
                rejectionReason = "Quantity exceeds holding",
                createdAt = Instant.parse("2026-07-08T12:00:00Z")
            )
            whenever(stockTradingService.placeOrder(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(sellRejectedOrder)

            val sellRequestBody = buildValidRequestBody() + mapOf("side" to "SELL")
            mockMvc.perform(
                post("/api/v1/stock-orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey.toString())
                    .content(objectMapper.writeValueAsString(sellRequestBody))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Quantity exceeds holding"))
        }

        test("placeOrder_rejectedOrder_returns200WithRejectedResponse") {
            whenever(stockTradingService.placeOrder(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildRejectedOrder())

            mockMvc.perform(
                post("/api/v1/stock-orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey.toString())
                    .content(objectMapper.writeValueAsString(buildValidRequestBody()))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Insufficient funds"))
                .andExpect(jsonPath("$.executionPrice").doesNotExist())
                .andExpect(jsonPath("$.totalCost").doesNotExist())
        }

        test("placeOrder_tickerNotFound_returns400") {
            whenever(stockTradingService.placeOrder(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(TickerNotFoundException("UNKNOWN"))

            mockMvc.perform(
                post("/api/v1/stock-orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey.toString())
                    .content(objectMapper.writeValueAsString(buildValidRequestBody()))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.status").value(400))
        }

        test("placeOrder_accountNotFound_returns404") {
            whenever(stockTradingService.placeOrder(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(OrderAccountNotFoundException(accountId))

            mockMvc.perform(
                post("/api/v1/stock-orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey.toString())
                    .content(objectMapper.writeValueAsString(buildValidRequestBody()))
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.status").value(404))
        }

        test("placeOrder_accountNotOwned_returns403") {
            whenever(stockTradingService.placeOrder(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(OrderAccountNotOwnedException(accountId, userId))

            mockMvc.perform(
                post("/api/v1/stock-orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey.toString())
                    .content(objectMapper.writeValueAsString(buildValidRequestBody()))
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.status").value(403))
        }

        test("placeOrder_accountNotActive_returns403") {
            whenever(stockTradingService.placeOrder(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(OrderAccountNotActiveException(accountId))

            mockMvc.perform(
                post("/api/v1/stock-orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey.toString())
                    .content(objectMapper.writeValueAsString(buildValidRequestBody()))
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.status").value(403))
        }

        test("placeOrder_duplicateIdempotencyKey_returns409") {
            whenever(stockTradingService.placeOrder(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(DuplicateIdempotencyKeyException(idempotencyKey))

            mockMvc.perform(
                post("/api/v1/stock-orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey.toString())
                    .content(objectMapper.writeValueAsString(buildValidRequestBody()))
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.status").value(409))
        }

        test("placeOrder_missingIdempotencyKeyHeader_returns400") {
            mockMvc.perform(
                post("/api/v1/stock-orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildValidRequestBody()))
            )
                .andExpect(status().isBadRequest)
        }

        test("placeOrder_missingRequestBody_returns400") {
            mockMvc.perform(
                post("/api/v1/stock-orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey.toString())
            )
                .andExpect(status().isBadRequest)
        }
    }
}
