package org.dpp.tradelab.agent.tool

import com.google.adk.tools.FunctionTool
import com.google.adk.tools.ToolContext
import org.dpp.tradelab.agent.service.TradeProposalService
import org.dpp.tradelab.agent.service.TradeProposalService.OrderOutcome
import org.dpp.tradelab.agent.service.TradeProposalService.OutcomeStatus
import org.dpp.tradelab.agent.service.TradeProposalService.ProposalStatus
import org.dpp.tradelab.stocktrading.api.OrderPlacementSide
import org.dpp.tradelab.stocktrading.api.OrderPlacementStatus
import org.dpp.tradelab.stocktrading.api.StockTradingApi
import org.dpp.tradelab.stocktrading.exception.DuplicateIdempotencyKeyException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotActiveException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotFoundException
import org.dpp.tradelab.stocktrading.exception.OrderAccountNotOwnedException
import org.dpp.tradelab.stocktrading.exception.TickerNotFoundException
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ExecuteTradesTool(
    private val tradeProposalService: TradeProposalService,
    private val stockTradingApi: StockTradingApi
) {
    fun asFunctionTool(): FunctionTool = FunctionTool.create(this, "executeTrades")

    fun executeTrades(toolContext: ToolContext, confirmed: Boolean): Result {
        val scope = TradeToolScope.from(toolContext)
        val proposal = tradeProposalService.getProposal(toolContext.state())
            ?.also {
                check(it.conversationId == scope.conversationId) {
                    "Trade proposal conversation does not match the agent session."
                }
                check(it.userId == scope.userId) {
                    "Trade proposal user does not match the authenticated user."
                }
                check(it.accountId == scope.accountId) {
                    "Trade proposal account does not match the conversation account."
                }
            }
            ?: return Result("no_pending_proposal", null, emptyList(), 0, 0)

        if (proposal.status == ProposalStatus.CONFIRMED) {
            return executionResult(toolContext, proposal, "already_confirmed", setRefreshMarker = false)
        }
        if (proposal.status != ProposalStatus.AWAITING_CONFIRMATION) {
            return Result("no_pending_proposal", proposal.proposalId, emptyList(), 0, proposal.orders.size)
        }
        if (!confirmed) {
            val declined = tradeProposalService.decline(toolContext.state(), proposal.proposalId)
            return Result("declined", declined.proposalId, emptyList(), 0, declined.orders.size)
        }

        proposal.orders.forEachIndexed { index, order ->
            if (order.outcome != null) return@forEachIndexed
            val outcome = try {
                val result = stockTradingApi.placeMarketOrder(
                    idempotencyKey = order.idempotencyKey,
                    accountId = scope.accountId,
                    userId = scope.userId,
                    ticker = order.ticker,
                    quantity = order.quantity,
                    side = OrderPlacementSide.valueOf(order.side)
                )
                when (result.status) {
                    OrderPlacementStatus.FILLED -> OrderOutcome(
                        status = OutcomeStatus.FILLED,
                        orderId = result.orderId,
                        executionPrice = requireNotNull(result.executionPrice),
                        totalAmount = requireNotNull(result.totalAmount)
                    )
                    OrderPlacementStatus.REJECTED -> OrderOutcome(
                        status = OutcomeStatus.REJECTED,
                        orderId = result.orderId,
                        rejectionReason = result.rejectionReason ?: "Order rejected."
                    )
                }
            } catch (ex: DuplicateIdempotencyKeyException) {
                OrderOutcome(
                    status = OutcomeStatus.ALREADY_PLACED,
                    errorMessage = "This order was already placed."
                )
            } catch (ex: Exception) {
                OrderOutcome(
                    status = OutcomeStatus.FAILED,
                    errorMessage = safeFailureMessage(ex)
                )
            }
            tradeProposalService.recordOutcome(toolContext.state(), proposal.proposalId, index, outcome)
        }

        val confirmedProposal = tradeProposalService.confirm(toolContext.state(), proposal.proposalId)
        return executionResult(toolContext, confirmedProposal, "executed")
    }

    data class Result(
        val status: String,
        val proposalId: UUID?,
        val orders: List<OrderReport>,
        val filledCount: Int,
        val totalCount: Int
    )

    data class OrderReport(
        val ticker: String,
        val side: String,
        val quantity: String,
        val status: String,
        val executionPrice: String? = null,
        val totalAmount: String? = null,
        val rejectionReason: String? = null,
        val errorMessage: String? = null
    )

    private fun executionResult(
        toolContext: ToolContext,
        proposal: TradeProposalService.TradeProposal,
        status: String,
        setRefreshMarker: Boolean = true
    ): Result {
        val reports = proposal.orders.map { order ->
            val outcome = order.outcome
            OrderReport(
                ticker = order.ticker,
                side = order.side,
                quantity = order.quantity.toPlainString(),
                status = outcome?.status?.name ?: "FAILED",
                executionPrice = outcome?.executionPrice?.toPlainString(),
                totalAmount = outcome?.totalAmount?.toPlainString(),
                rejectionReason = outcome?.rejectionReason,
                errorMessage = outcome?.errorMessage
            )
        }
        val filledCount = proposal.orders.count { it.outcome?.status == OutcomeStatus.FILLED }
        if (setRefreshMarker && filledCount > 0) {
            toolContext.state()[TradeProposalService.PENDING_TRADE_REFRESH_ACCOUNT_ID] =
                proposal.accountId.toString()
        }
        return Result(
            status = status,
            proposalId = proposal.proposalId,
            orders = reports,
            filledCount = filledCount,
            totalCount = proposal.orders.size
        )
    }

    private fun safeFailureMessage(ex: Exception): String = when (ex) {
        is TickerNotFoundException,
        is OrderAccountNotFoundException,
        is OrderAccountNotOwnedException,
        is OrderAccountNotActiveException -> ex.message ?: "Order could not be placed."
        else -> "Order could not be placed. Please try again."
    }
}
