package org.dpp.tradelab.agent.tool

import com.google.adk.tools.FunctionTool
import com.google.adk.tools.ToolContext
import org.dpp.tradelab.agent.service.TradeOrderInput
import org.dpp.tradelab.agent.service.TradeProposalService
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID

@Component
class ProposeTradesTool(
    private val tradeProposalService: TradeProposalService
) {
    fun asFunctionTool(): FunctionTool = FunctionTool.create(this, "proposeTrades")

    fun proposeTrades(toolContext: ToolContext, orders: List<TradeOrderInput>): Result {
        val scope = TradeToolScope.from(toolContext)
        val validOrders = mutableListOf<TradeOrderInput>()
        val invalidOrders = mutableListOf<InvalidOrder>()

        orders.forEachIndexed { index, order ->
            val quantity = order.quantity.trim().toBigDecimalOrNull()
            val reason = when {
                order.ticker.isBlank() -> "Ticker is required."
                quantity == null -> "Quantity must be a number."
                quantity <= BigDecimal.ZERO -> "Quantity must be greater than zero."
                order.side !in setOf("BUY", "SELL") -> "Side must be BUY or SELL."
                else -> null
            }
            if (reason == null) {
                validOrders += order
            } else {
                invalidOrders += InvalidOrder(
                    index = index,
                    ticker = order.ticker,
                    side = order.side,
                    quantity = order.quantity,
                    reason = reason
                )
            }
        }

        if (validOrders.isEmpty()) {
            return Result(
                status = "no_valid_orders",
                targetAccountId = scope.accountId,
                orders = emptyList(),
                invalidOrders = invalidOrders
            )
        }

        val proposal = tradeProposalService.propose(
            state = toolContext.state(),
            conversationId = scope.conversationId,
            userId = scope.userId,
            accountId = scope.accountId,
            orders = validOrders
        )
        return Result(
            status = "proposed",
            proposalId = proposal.proposalId,
            targetAccountId = proposal.accountId,
            orders = proposal.orders.map { ProposedOrder(it.ticker, it.side, it.quantity) },
            invalidOrders = invalidOrders
        )
    }

    data class Result(
        val status: String,
        val targetAccountId: UUID,
        val orders: List<ProposedOrder>,
        val invalidOrders: List<InvalidOrder>,
        val proposalId: UUID? = null
    )

    data class ProposedOrder(
        val ticker: String,
        val side: String,
        val quantity: BigDecimal
    )

    data class InvalidOrder(
        val index: Int,
        val ticker: String,
        val side: String,
        val quantity: String,
        val reason: String
    )
}
