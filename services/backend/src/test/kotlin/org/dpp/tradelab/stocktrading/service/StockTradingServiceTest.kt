package org.dpp.tradelab.stocktrading.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.dpp.tradelab.ledger.api.AccountSummary
import org.dpp.tradelab.ledger.api.LedgerAccountApi
import org.dpp.tradelab.ledger.api.LedgerApi
import org.dpp.tradelab.ledger.exception.AccountNotFoundException
import org.dpp.tradelab.marketdata.api.MarketDataApi
import org.dpp.tradelab.marketdata.api.MarketDataSupportedTickersApi
import org.dpp.tradelab.stocktrading.exception.DuplicateIdempotencyKeyException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotActiveException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotFoundException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotOwnedException
import org.dpp.tradelab.stocktrading.exception.TickerNotFoundException
import org.dpp.tradelab.stocktrading.messaging.OrderFilledEvent
import org.dpp.tradelab.stocktrading.messaging.OrderRejectedEvent
import org.dpp.tradelab.stocktrading.model.OrderStatus
import org.dpp.tradelab.stocktrading.model.OrderType
import org.dpp.tradelab.stocktrading.repository.OrderRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.util.UUID

class StockTradingServiceTest : FunSpec({

    val orderRepository = mock<OrderRepository>()
    val ledgerApi = mock<LedgerApi>()
    val ledgerAccountApi = mock<LedgerAccountApi>()
    val marketDataApi = mock<MarketDataApi>()
    val marketDataSupportedTickersApi = mock<MarketDataSupportedTickersApi>()
    val eventPublisher = mock<ApplicationEventPublisher>()

    val service = StockTradingService(
        orderRepository,
        ledgerApi,
        ledgerAccountApi,
        marketDataApi,
        marketDataSupportedTickersApi,
        eventPublisher
    )

    val userId = UUID.randomUUID()
    val accountId = UUID.randomUUID()
    val idempotencyKey = UUID.randomUUID()
    val ticker = "AAPL"
    val quantity = BigDecimal("2.0000")
    val priceSnapshot = BigDecimal("180.000")
    val executionPrice = BigDecimal("182.500")

    fun activeAccountSummary(balance: BigDecimal = BigDecimal("1000.0000")) = AccountSummary(
        id = accountId,
        userId = userId,
        currency = "USD",
        balance = balance,
        status = "active"
    )

    beforeEach {
        org.mockito.kotlin.reset(
            orderRepository, ledgerApi, ledgerAccountApi,
            marketDataApi, marketDataSupportedTickersApi, eventPublisher
        )
        whenever(orderRepository.save(any())).thenAnswer { it.arguments[0] }
    }

    // ── FILLED happy path ────────────────────────────────────────────────────

    test("placeOrder_filledHappyPath_returnsFilledOrder") {
        whenever(marketDataSupportedTickersApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(activeAccountSummary())
        whenever(orderRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(marketDataApi.getCurrentPrice(ticker)).thenReturn(executionPrice)

        val order = service.placeOrder(
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            orderType = OrderType.MARKET,
            priceSnapshot = priceSnapshot
        )

        order.status shouldBe OrderStatus.FILLED
        order.executionPrice shouldBe executionPrice
        order.rejectionReason shouldBe null
    }

    test("placeOrder_filledHappyPath_callsLedgerApiTwice") {
        whenever(marketDataSupportedTickersApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(activeAccountSummary())
        whenever(orderRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(marketDataApi.getCurrentPrice(ticker)).thenReturn(executionPrice)

        service.placeOrder(
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            orderType = OrderType.MARKET,
            priceSnapshot = priceSnapshot
        )

        verify(ledgerApi, times(2)).recordTransaction(any(), any(), any(), any(), any(), any(), anyOrNull(), anyOrNull())
    }

    test("placeOrder_filledHappyPath_firstLedgerCallIsDebitCash") {
        whenever(marketDataSupportedTickersApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(activeAccountSummary())
        whenever(orderRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(marketDataApi.getCurrentPrice(ticker)).thenReturn(executionPrice)

        service.placeOrder(
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            orderType = OrderType.MARKET,
            priceSnapshot = priceSnapshot
        )

        val typeCaptor = argumentCaptor<String>()
        val assetTypeCaptor = argumentCaptor<String>()
        val amountCaptor = argumentCaptor<BigDecimal>()
        verify(ledgerApi, times(2)).recordTransaction(
            any(), any(),
            typeCaptor.capture(),
            assetTypeCaptor.capture(),
            amountCaptor.capture(),
            any(), anyOrNull(), anyOrNull()
        )

        typeCaptor.firstValue shouldBe "DEBIT"
        assetTypeCaptor.firstValue shouldBe "CASH"
        amountCaptor.firstValue shouldBe quantity.multiply(executionPrice)

        typeCaptor.secondValue shouldBe "CREDIT"
        assetTypeCaptor.secondValue shouldBe "STOCK_BUY"
        amountCaptor.secondValue shouldBe quantity
    }

    test("placeOrder_filledHappyPath_emitsOrderFilledEvent") {
        whenever(marketDataSupportedTickersApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(activeAccountSummary())
        whenever(orderRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(marketDataApi.getCurrentPrice(ticker)).thenReturn(executionPrice)

        service.placeOrder(
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            orderType = OrderType.MARKET,
            priceSnapshot = priceSnapshot
        )

        val captor = argumentCaptor<OrderFilledEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        captor.firstValue.orderId shouldNotBe null
        captor.firstValue.accountId shouldBe accountId
        captor.firstValue.userId shouldBe userId
        captor.firstValue.ticker shouldBe ticker
        captor.firstValue.quantity shouldBe quantity
        captor.firstValue.executionPrice shouldBe executionPrice
    }

    // ── REJECTED — insufficient funds ────────────────────────────────────────

    test("placeOrder_insufficientFunds_returnsRejectedOrder") {
        whenever(marketDataSupportedTickersApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(activeAccountSummary(BigDecimal("10.0000")))
        whenever(orderRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(marketDataApi.getCurrentPrice(ticker)).thenReturn(executionPrice)

        val order = service.placeOrder(
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            orderType = OrderType.MARKET,
            priceSnapshot = priceSnapshot
        )

        order.status shouldBe OrderStatus.REJECTED
        order.rejectionReason shouldBe "Insufficient funds"
    }

    test("placeOrder_insufficientFunds_doesNotCallLedgerApi") {
        whenever(marketDataSupportedTickersApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(activeAccountSummary(BigDecimal("10.0000")))
        whenever(orderRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(marketDataApi.getCurrentPrice(ticker)).thenReturn(executionPrice)

        service.placeOrder(
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            orderType = OrderType.MARKET,
            priceSnapshot = priceSnapshot
        )

        verify(ledgerApi, never()).recordTransaction(any(), any(), any(), any(), any(), any(), anyOrNull(), anyOrNull())
    }

    test("placeOrder_insufficientFunds_emitsOrderRejectedEvent") {
        whenever(marketDataSupportedTickersApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(activeAccountSummary(BigDecimal("10.0000")))
        whenever(orderRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(marketDataApi.getCurrentPrice(ticker)).thenReturn(executionPrice)

        service.placeOrder(
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            orderType = OrderType.MARKET,
            priceSnapshot = priceSnapshot
        )

        val captor = argumentCaptor<OrderRejectedEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        captor.firstValue.accountId shouldBe accountId
        captor.firstValue.rejectionReason shouldBe "Insufficient funds"
    }

    // ── Validation errors (no Order saved) ──────────────────────────────────

    test("placeOrder_zeroQuantity_throwsIllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            service.placeOrder(
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = ticker,
                quantity = BigDecimal.ZERO,
                orderType = OrderType.MARKET,
                priceSnapshot = priceSnapshot
            )
        }
        verify(orderRepository, never()).save(any())
    }

    test("placeOrder_tickerNotSupported_throwsTickerNotFoundException") {
        whenever(marketDataSupportedTickersApi.isTickerSupported(ticker)).thenReturn(false)

        shouldThrow<TickerNotFoundException> {
            service.placeOrder(
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = ticker,
                quantity = quantity,
                orderType = OrderType.MARKET,
                priceSnapshot = priceSnapshot
            )
        }
        verify(orderRepository, never()).save(any())
    }

    test("placeOrder_accountNotFound_throwsOrderAccountNotFoundException") {
        whenever(marketDataSupportedTickersApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenThrow(AccountNotFoundException(accountId))

        shouldThrow<OrderAccountNotFoundException> {
            service.placeOrder(
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = ticker,
                quantity = quantity,
                orderType = OrderType.MARKET,
                priceSnapshot = priceSnapshot
            )
        }
        verify(orderRepository, never()).save(any())
    }

    test("placeOrder_accountNotOwned_throwsOrderAccountNotOwnedException") {
        val differentUserId = UUID.randomUUID()
        whenever(marketDataSupportedTickersApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(
            AccountSummary(
                id = accountId,
                userId = differentUserId,
                currency = "USD",
                balance = BigDecimal("1000.0000"),
                status = "active"
            )
        )

        shouldThrow<OrderAccountNotOwnedException> {
            service.placeOrder(
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = ticker,
                quantity = quantity,
                orderType = OrderType.MARKET,
                priceSnapshot = priceSnapshot
            )
        }
        verify(orderRepository, never()).save(any())
    }

    test("placeOrder_accountNotActive_throwsOrderAccountNotActiveException") {
        whenever(marketDataSupportedTickersApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(
            AccountSummary(
                id = accountId,
                userId = userId,
                currency = "USD",
                balance = BigDecimal("1000.0000"),
                status = "suspended"
            )
        )

        shouldThrow<OrderAccountNotActiveException> {
            service.placeOrder(
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = ticker,
                quantity = quantity,
                orderType = OrderType.MARKET,
                priceSnapshot = priceSnapshot
            )
        }
        verify(orderRepository, never()).save(any())
    }

    test("placeOrder_duplicateIdempotencyKey_throwsDuplicateIdempotencyKeyException") {
        whenever(marketDataSupportedTickersApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(activeAccountSummary())
        whenever(orderRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(true)

        shouldThrow<DuplicateIdempotencyKeyException> {
            service.placeOrder(
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = ticker,
                quantity = quantity,
                orderType = OrderType.MARKET,
                priceSnapshot = priceSnapshot
            )
        }
        verify(orderRepository, never()).save(any())
    }
})
