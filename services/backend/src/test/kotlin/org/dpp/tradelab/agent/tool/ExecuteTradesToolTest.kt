package org.dpp.tradelab.agent.tool

import com.google.adk.agents.InvocationContext
import com.google.adk.agents.RunConfig
import com.google.adk.events.EventActions
import com.google.adk.sessions.InMemorySessionService
import com.google.adk.sessions.Session
import com.google.adk.tools.ToolContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.agent.service.TradeOrderInput
import org.dpp.tradelab.agent.service.TradeProposalService
import org.dpp.tradelab.agent.service.TradeProposalService.OutcomeStatus
import org.dpp.tradelab.stocktrading.api.OrderPlacementResult
import org.dpp.tradelab.stocktrading.api.OrderPlacementSide
import org.dpp.tradelab.stocktrading.api.OrderPlacementStatus
import org.dpp.tradelab.stocktrading.api.StockTradingApi
import org.dpp.tradelab.stocktrading.exception.DuplicateIdempotencyKeyException
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class ExecuteTradesToolTest : FunSpec({
    val proposalService = TradeProposalService()
    val stockTradingApi = mock<StockTradingApi>()
    val tool = ExecuteTradesTool(proposalService, stockTradingApi)
    val conversationId = UUID.randomUUID()
    val userId = UUID.randomUUID()
    val accountId = UUID.randomUUID()
    val sessionService = InMemorySessionService()
    val orders = listOf(
        TradeOrderInput("AAPL", "BUY", "2"),
        TradeOrderInput("TSLA", "SELL", "1")
    )

    fun toolContext(): ToolContext {
        val session = Session.Builder(conversationId.toString())
            .appName("trade-lab-agent")
            .userId(userId.toString())
            .state(mapOf("userId" to userId.toString(), "accountId" to accountId.toString()))
            .build()
        val invocationContext = InvocationContext.builder()
            .invocationId("invocation-id")
            .agent(mock())
            .session(session)
            .sessionService(sessionService)
            .runConfig(
                RunConfig.builder()
                    .streamingMode(RunConfig.StreamingMode.SSE)
                    .autoCreateSession(false)
                    .build()
            )
            .callbackContextData(emptyMap())
            .build()
        return ToolContext.builder(invocationContext)
            .actions(EventActions.builder().build())
            .eventId("event-id")
            .build()
    }

    fun filled(orderId: UUID = UUID.randomUUID(), ticker: String, side: OrderPlacementSide, quantity: BigDecimal) =
        OrderPlacementResult(
            orderId = orderId,
            status = OrderPlacementStatus.FILLED,
            ticker = ticker,
            quantity = quantity,
            side = side,
            executionPrice = BigDecimal("10.00"),
            totalAmount = quantity.multiply(BigDecimal("10.00"))
        )

    fun rejected(ticker: String, side: OrderPlacementSide, quantity: BigDecimal) =
        OrderPlacementResult(
            orderId = UUID.randomUUID(),
            status = OrderPlacementStatus.REJECTED,
            ticker = ticker,
            quantity = quantity,
            side = side,
            rejectionReason = "Insufficient funds"
        )

    fun proposedContext(orderInputs: List<TradeOrderInput> = orders): Pair<ToolContext, TradeProposalService.TradeProposal> {
        val context = toolContext()
        val proposal = proposalService.propose(context.state(), conversationId, userId, accountId, orderInputs)
        return context to proposal
    }

    beforeEach { reset(stockTradingApi) }

    test("executeTrades_allFilled_recordsOutcomesAndSetsRefreshMarker") {
        val (context, proposal) = proposedContext()
        whenever(stockTradingApi.placeMarketOrder(
            eq(proposal.orders[0].idempotencyKey), eq(accountId), eq(userId),
            eq("AAPL"), eq(BigDecimal("2")), eq(OrderPlacementSide.BUY)
        )).thenReturn(filled(ticker = "AAPL", side = OrderPlacementSide.BUY, quantity = BigDecimal("2")))
        whenever(stockTradingApi.placeMarketOrder(
            eq(proposal.orders[1].idempotencyKey), eq(accountId), eq(userId),
            eq("TSLA"), eq(BigDecimal("1")), eq(OrderPlacementSide.SELL)
        )).thenReturn(filled(ticker = "TSLA", side = OrderPlacementSide.SELL, quantity = BigDecimal("1")))

        val result = tool.executeTrades(context, confirmed = true)

        result.status shouldBe "executed"
        result.orders shouldHaveSize 2
        result.filledCount shouldBe 2
        context.state()[TradeProposalService.PENDING_TRADE_REFRESH_ACCOUNT_ID] shouldBe accountId.toString()
        proposalService.getProposal(context.state())?.status shouldBe TradeProposalService.ProposalStatus.CONFIRMED
    }

    test("executeTrades_rejectionAndException_continuesInProposalOrder") {
        val (context, proposal) = proposedContext(
            orders + TradeOrderInput("MSFT", "BUY", "3")
        )
        whenever(stockTradingApi.placeMarketOrder(
            eq(proposal.orders[0].idempotencyKey), eq(accountId), eq(userId),
            eq("AAPL"), eq(BigDecimal("2")), eq(OrderPlacementSide.BUY)
        )).thenReturn(rejected("AAPL", OrderPlacementSide.BUY, BigDecimal("2")))
        whenever(stockTradingApi.placeMarketOrder(
            eq(proposal.orders[1].idempotencyKey), eq(accountId), eq(userId),
            eq("TSLA"), eq(BigDecimal("1")), eq(OrderPlacementSide.SELL)
        )).thenThrow(IllegalStateException("internal details"))
        whenever(stockTradingApi.placeMarketOrder(
            eq(proposal.orders[2].idempotencyKey), eq(accountId), eq(userId),
            eq("MSFT"), eq(BigDecimal("3")), eq(OrderPlacementSide.BUY)
        )).thenReturn(filled(ticker = "MSFT", side = OrderPlacementSide.BUY, quantity = BigDecimal("3")))

        val result = tool.executeTrades(context, confirmed = true)

        result.orders.map { it.status } shouldContainExactly listOf("REJECTED", "FAILED", "FILLED")
        result.orders[1].errorMessage shouldBe "Order could not be placed. Please try again."
        result.filledCount shouldBe 1
        context.state()[TradeProposalService.PENDING_TRADE_REFRESH_ACCOUNT_ID] shouldBe accountId.toString()
        verify(stockTradingApi, times(3)).placeMarketOrder(any(), eq(accountId), eq(userId), any(), any(), any())
    }

    test("executeTrades_duplicateIdempotencyKey_recordsAlreadyPlacedOutcome") {
        val (context, proposal) = proposedContext(listOf(orders.first()))
        whenever(stockTradingApi.placeMarketOrder(
            eq(proposal.orders[0].idempotencyKey), eq(accountId), eq(userId),
            eq("AAPL"), eq(BigDecimal("2")), eq(OrderPlacementSide.BUY)
        )).thenThrow(DuplicateIdempotencyKeyException(proposal.orders[0].idempotencyKey))

        val result = tool.executeTrades(context, confirmed = true)

        result.orders.single().status shouldBe OutcomeStatus.ALREADY_PLACED.name
        result.orders.single().errorMessage shouldBe "This order was already placed."
        context.state().containsKey(TradeProposalService.PENDING_TRADE_REFRESH_ACCOUNT_ID) shouldBe false
    }

    test("executeTrades_reconfirm_returnsStoredOutcomesWithoutPlacingAgain") {
        val (context, proposal) = proposedContext(listOf(orders.first()))
        whenever(stockTradingApi.placeMarketOrder(
            eq(proposal.orders[0].idempotencyKey), eq(accountId), eq(userId),
            eq("AAPL"), eq(BigDecimal("2")), eq(OrderPlacementSide.BUY)
        )).thenReturn(filled(ticker = "AAPL", side = OrderPlacementSide.BUY, quantity = BigDecimal("2")))

        val firstResult = tool.executeTrades(context, confirmed = true)
        context.state().remove(TradeProposalService.PENDING_TRADE_REFRESH_ACCOUNT_ID)
        val retryResult = tool.executeTrades(context, confirmed = true)

        firstResult.orders shouldBe retryResult.orders
        retryResult.status shouldBe "already_confirmed"
        context.state().containsKey(TradeProposalService.PENDING_TRADE_REFRESH_ACCOUNT_ID) shouldBe false
        verify(stockTradingApi, times(1)).placeMarketOrder(any(), eq(accountId), eq(userId), any(), any(), any())
    }

    test("executeTrades_rejectedBatch_doesNotSetRefreshMarker") {
        val (context, proposal) = proposedContext(listOf(orders.first()))
        whenever(stockTradingApi.placeMarketOrder(
            eq(proposal.orders[0].idempotencyKey), eq(accountId), eq(userId),
            eq("AAPL"), eq(BigDecimal("2")), eq(OrderPlacementSide.BUY)
        )).thenReturn(rejected("AAPL", OrderPlacementSide.BUY, BigDecimal("2")))

        val result = tool.executeTrades(context, confirmed = true)

        result.filledCount shouldBe 0
        context.state().containsKey(TradeProposalService.PENDING_TRADE_REFRESH_ACCOUNT_ID) shouldBe false
    }

    test("executeTrades_decline_marksProposalWithoutPlacingOrders") {
        val (context, proposal) = proposedContext()

        val result = tool.executeTrades(context, confirmed = false)

        result.status shouldBe "declined"
        proposalService.getProposal(context.state())?.status shouldBe TradeProposalService.ProposalStatus.DECLINED
        verify(stockTradingApi, times(0)).placeMarketOrder(any(), any(), any(), any(), any(), any())
    }

    test("asFunctionTool_createsNamedFunctionTool") {
        tool.asFunctionTool().declaration().get().name().orElseThrow() shouldBe "executeTrades"
    }
})
