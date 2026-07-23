package org.dpp.tradelab.stocktrading.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.dpp.tradelab.ledger.api.AccountSummary
import org.dpp.tradelab.ledger.api.LedgerAccountApi
import org.dpp.tradelab.ledger.api.LedgerApi
import org.dpp.tradelab.ledger.api.TransactionAssetType
import org.dpp.tradelab.ledger.api.TransactionType
import org.dpp.tradelab.ledger.exception.AccountNotFoundException
import org.dpp.tradelab.marketdata.api.MarketDataApi
import org.dpp.tradelab.portfolio.api.PortfolioApi
import org.dpp.tradelab.stocktrading.exception.DuplicateIdempotencyKeyException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotActiveException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotFoundException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotOwnedException
import org.dpp.tradelab.stocktrading.exception.TickerNotFoundException
import org.dpp.tradelab.stocktrading.messaging.OrderFilledEvent
import org.dpp.tradelab.stocktrading.messaging.OrderRejectedEvent
import org.dpp.tradelab.stocktrading.model.OrderSide
import org.dpp.tradelab.stocktrading.model.OrderStatus
import org.dpp.tradelab.stocktrading.model.OrderType
import org.dpp.tradelab.stocktrading.repository.OrderRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
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
    val portfolioApi = mock<PortfolioApi>()
    val marketDataApi = mock<MarketDataApi>()
    val eventPublisher = mock<ApplicationEventPublisher>()

    val service = StockTradingService(
        orderRepository,
        ledgerApi,
        ledgerAccountApi,
        portfolioApi,
        marketDataApi,
        eventPublisher
    )

    val userId = UUID.randomUUID()
    val accountId = UUID.randomUUID()
    val idempotencyKey = UUID.randomUUID()
    val ticker = "AAPL"
    val quantity = BigDecimal("2.0000")
    val priceSnapshot = BigDecimal("180.0000")
    val executionPrice = BigDecimal("182.5000")

    fun activeAccountSummary(balance: BigDecimal = BigDecimal("1000.0000")) = AccountSummary(
        id = accountId,
        userId = userId,
        currency = "USD",
        balance = balance,
        status = "active"
    )

    beforeEach {
        reset(orderRepository, ledgerApi, ledgerAccountApi, portfolioApi, marketDataApi, eventPublisher)
        whenever(orderRepository.save(any())).thenAnswer { it.arguments[0] }
    }

    test("getIndicativePrice_happyPath_returnsCurrentPrice") {
        whenever(marketDataApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(marketDataApi.getCurrentPrice(ticker)).thenReturn(executionPrice)

        service.getIndicativePrice(ticker) shouldBe executionPrice
    }

    test("getIndicativePrice_unsupportedTicker_throwsTickerNotFoundException") {
        whenever(marketDataApi.isTickerSupported(ticker)).thenReturn(false)

        shouldThrow<TickerNotFoundException> {
            service.getIndicativePrice(ticker)
        }
    }

    test("placeOrder_buyFilledHappyPath_returnsFilledOrder") {
        whenever(marketDataApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(activeAccountSummary())
        whenever(orderRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(marketDataApi.getCurrentPrice(ticker)).thenReturn(executionPrice)

        val order = service.placeOrder(
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            side = OrderSide.BUY,
            orderType = OrderType.MARKET,
            priceSnapshot = priceSnapshot
        )

        order.status shouldBe OrderStatus.FILLED
        order.side shouldBe OrderSide.BUY
        order.executionPrice shouldBe executionPrice
        order.rejectionReason shouldBe null
    }

    test("placeOrder_buyFilledHappyPath_callsDebitCashThenCreditStockBuy") {
        whenever(marketDataApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(activeAccountSummary())
        whenever(orderRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(marketDataApi.getCurrentPrice(ticker)).thenReturn(executionPrice)

        service.placeOrder(
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            side = OrderSide.BUY,
            orderType = OrderType.MARKET,
            priceSnapshot = priceSnapshot
        )

        val typeCaptor = argumentCaptor<TransactionType>()
        val assetTypeCaptor = argumentCaptor<TransactionAssetType>()
        val amountCaptor = argumentCaptor<BigDecimal>()
        verify(ledgerApi, times(2)).recordTransaction(
            any(), any(), typeCaptor.capture(), assetTypeCaptor.capture(), amountCaptor.capture(),
            any(), anyOrNull(), anyOrNull()
        )

        typeCaptor.firstValue shouldBe TransactionType.DEBIT
        assetTypeCaptor.firstValue shouldBe TransactionAssetType.CASH
        amountCaptor.firstValue shouldBe quantity.multiply(executionPrice)
        typeCaptor.secondValue shouldBe TransactionType.CREDIT
        assetTypeCaptor.secondValue shouldBe TransactionAssetType.STOCK_BUY
        amountCaptor.secondValue shouldBe quantity
    }

    test("placeOrder_buyFilledHappyPath_emitsOrderFilledEventWithBuySide") {
        whenever(marketDataApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(activeAccountSummary())
        whenever(orderRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(marketDataApi.getCurrentPrice(ticker)).thenReturn(executionPrice)

        service.placeOrder(
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            side = OrderSide.BUY,
            orderType = OrderType.MARKET,
            priceSnapshot = priceSnapshot
        )

        val captor = argumentCaptor<OrderFilledEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        captor.firstValue.orderId shouldNotBe null
        captor.firstValue.side shouldBe OrderSide.BUY
        captor.firstValue.idempotencyKey shouldBe idempotencyKey
    }

    test("placeOrder_buyInsufficientFunds_returnsRejectedOrderWithoutLedgerWrites") {
        whenever(marketDataApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(activeAccountSummary(BigDecimal("10.0000")))
        whenever(orderRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(marketDataApi.getCurrentPrice(ticker)).thenReturn(executionPrice)

        val order = service.placeOrder(
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            side = OrderSide.BUY,
            orderType = OrderType.MARKET,
            priceSnapshot = priceSnapshot
        )

        order.status shouldBe OrderStatus.REJECTED
        order.rejectionReason shouldBe "Insufficient funds"
        verify(ledgerApi, never()).recordTransaction(any(), any(), any(), any(), any(), any(), anyOrNull(), anyOrNull())

        val captor = argumentCaptor<OrderRejectedEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        captor.firstValue.side shouldBe OrderSide.BUY
        captor.firstValue.rejectionReason shouldBe "Insufficient funds"
    }

    test("placeOrder_sellFilledHappyPath_returnsFilledOrder") {
        whenever(marketDataApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(activeAccountSummary())
        whenever(orderRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(marketDataApi.getCurrentPrice(ticker)).thenReturn(executionPrice)
        whenever(portfolioApi.getPositionQuantity(accountId, ticker)).thenReturn(BigDecimal("5.0000"))

        val order = service.placeOrder(
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            side = OrderSide.SELL,
            orderType = OrderType.MARKET,
            priceSnapshot = priceSnapshot
        )

        order.status shouldBe OrderStatus.FILLED
        order.side shouldBe OrderSide.SELL
        order.executionPrice shouldBe executionPrice
    }

    test("placeOrder_sellFilledHappyPath_callsDebitStockSellThenCreditCash") {
        whenever(marketDataApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(activeAccountSummary())
        whenever(orderRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(marketDataApi.getCurrentPrice(ticker)).thenReturn(executionPrice)
        whenever(portfolioApi.getPositionQuantity(accountId, ticker)).thenReturn(BigDecimal("5.0000"))

        service.placeOrder(
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            side = OrderSide.SELL,
            orderType = OrderType.MARKET,
            priceSnapshot = priceSnapshot
        )

        val typeCaptor = argumentCaptor<TransactionType>()
        val assetTypeCaptor = argumentCaptor<TransactionAssetType>()
        val amountCaptor = argumentCaptor<BigDecimal>()
        verify(ledgerApi, times(2)).recordTransaction(
            any(), any(), typeCaptor.capture(), assetTypeCaptor.capture(), amountCaptor.capture(),
            any(), anyOrNull(), anyOrNull()
        )

        typeCaptor.firstValue shouldBe TransactionType.DEBIT
        assetTypeCaptor.firstValue shouldBe TransactionAssetType.STOCK_SELL
        amountCaptor.firstValue shouldBe quantity
        typeCaptor.secondValue shouldBe TransactionType.CREDIT
        assetTypeCaptor.secondValue shouldBe TransactionAssetType.CASH
        amountCaptor.secondValue shouldBe quantity.multiply(executionPrice)
    }

    test("placeOrder_sellFilledHappyPath_emitsOrderFilledEventWithSellSide") {
        whenever(marketDataApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(activeAccountSummary())
        whenever(orderRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(marketDataApi.getCurrentPrice(ticker)).thenReturn(executionPrice)
        whenever(portfolioApi.getPositionQuantity(accountId, ticker)).thenReturn(BigDecimal("5.0000"))

        service.placeOrder(
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            side = OrderSide.SELL,
            orderType = OrderType.MARKET,
            priceSnapshot = priceSnapshot
        )

        val captor = argumentCaptor<OrderFilledEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        captor.firstValue.side shouldBe OrderSide.SELL
        captor.firstValue.idempotencyKey shouldBe idempotencyKey
    }

    test("placeOrder_sellInsufficientHolding_returnsRejectedOrderWithoutLedgerWrites") {
        whenever(marketDataApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(activeAccountSummary())
        whenever(orderRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false)
        whenever(marketDataApi.getCurrentPrice(ticker)).thenReturn(executionPrice)
        whenever(portfolioApi.getPositionQuantity(accountId, ticker)).thenReturn(BigDecimal("1.0000"))

        val order = service.placeOrder(
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            userId = userId,
            ticker = ticker,
            quantity = quantity,
            side = OrderSide.SELL,
            orderType = OrderType.MARKET,
            priceSnapshot = priceSnapshot
        )

        order.status shouldBe OrderStatus.REJECTED
        order.rejectionReason shouldBe "Quantity exceeds holding"
        verify(ledgerApi, never()).recordTransaction(any(), any(), any(), any(), any(), any(), anyOrNull(), anyOrNull())

        val captor = argumentCaptor<OrderRejectedEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        captor.firstValue.side shouldBe OrderSide.SELL
        captor.firstValue.rejectionReason shouldBe "Quantity exceeds holding"
    }

    test("placeOrder_zeroQuantity_throwsIllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            service.placeOrder(
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = ticker,
                quantity = BigDecimal.ZERO,
                side = OrderSide.BUY,
                orderType = OrderType.MARKET,
                priceSnapshot = priceSnapshot
            )
        }
        verify(orderRepository, never()).save(any())
    }

    test("placeOrder_tickerNotSupported_throwsTickerNotFoundException") {
        whenever(marketDataApi.isTickerSupported(ticker)).thenReturn(false)

        shouldThrow<TickerNotFoundException> {
            service.placeOrder(
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = ticker,
                quantity = quantity,
                side = OrderSide.BUY,
                orderType = OrderType.MARKET,
                priceSnapshot = priceSnapshot
            )
        }
        verify(orderRepository, never()).save(any())
    }

    test("placeOrder_accountNotFound_throwsOrderAccountNotFoundException") {
        whenever(marketDataApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenThrow(AccountNotFoundException(accountId))

        shouldThrow<OrderAccountNotFoundException> {
            service.placeOrder(
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = ticker,
                quantity = quantity,
                side = OrderSide.BUY,
                orderType = OrderType.MARKET,
                priceSnapshot = priceSnapshot
            )
        }
        verify(orderRepository, never()).save(any())
    }

    test("placeOrder_accountNotOwned_throwsOrderAccountNotOwnedException") {
        whenever(marketDataApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(
            AccountSummary(
                id = accountId,
                userId = UUID.randomUUID(),
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
                side = OrderSide.BUY,
                orderType = OrderType.MARKET,
                priceSnapshot = priceSnapshot
            )
        }
        verify(orderRepository, never()).save(any())
    }

    test("placeOrder_accountNotActive_throwsOrderAccountNotActiveException") {
        whenever(marketDataApi.isTickerSupported(ticker)).thenReturn(true)
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
                side = OrderSide.BUY,
                orderType = OrderType.MARKET,
                priceSnapshot = priceSnapshot
            )
        }
        verify(orderRepository, never()).save(any())
    }

    test("placeOrder_duplicateIdempotencyKey_throwsDuplicateIdempotencyKeyException") {
        whenever(marketDataApi.isTickerSupported(ticker)).thenReturn(true)
        whenever(ledgerAccountApi.getAccount(accountId)).thenReturn(activeAccountSummary())
        whenever(orderRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(true)

        shouldThrow<DuplicateIdempotencyKeyException> {
            service.placeOrder(
                idempotencyKey = idempotencyKey,
                accountId = accountId,
                userId = userId,
                ticker = ticker,
                quantity = quantity,
                side = OrderSide.BUY,
                orderType = OrderType.MARKET,
                priceSnapshot = priceSnapshot
            )
        }
        verify(orderRepository, never()).save(any())
    }
})
