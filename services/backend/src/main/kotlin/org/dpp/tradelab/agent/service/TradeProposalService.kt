package org.dpp.tradelab.agent.service

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class TradeProposalService {
    companion object {
        const val PROPOSAL_STATE_KEY = "tradeProposal"
        const val SUPERSEDED_PROPOSAL_STATE_KEY = "supersededTradeProposal"
        const val PENDING_TRADE_REFRESH_ACCOUNT_ID = "pendingTradeRefreshAccountId"
    }

    enum class ProposalStatus {
        AWAITING_CONFIRMATION,
        CONFIRMED,
        DECLINED,
        SUPERSEDED
    }

    enum class OutcomeStatus {
        FILLED,
        REJECTED,
        FAILED,
        ALREADY_PLACED
    }

    data class ProposedOrder(
        val ticker: String,
        val side: String,
        val quantity: BigDecimal,
        val idempotencyKey: UUID,
        val outcome: OrderOutcome? = null
    )

    data class OrderOutcome(
        val status: OutcomeStatus,
        val orderId: UUID? = null,
        val executionPrice: BigDecimal? = null,
        val totalAmount: BigDecimal? = null,
        val rejectionReason: String? = null,
        val errorMessage: String? = null
    )

    data class TradeProposal(
        val proposalId: UUID,
        val conversationId: UUID,
        val userId: UUID,
        val accountId: UUID,
        val orders: List<ProposedOrder>,
        val status: ProposalStatus,
        val createdAt: Instant,
        val updatedAt: Instant
    )

    fun propose(
        state: MutableMap<String, Any>,
        conversationId: UUID,
        userId: UUID,
        accountId: UUID,
        orders: List<TradeOrderInput>
    ): TradeProposal {
        require(orders.isNotEmpty()) { "A trade proposal must contain at least one order." }

        val existing = getProposal(state)
        if (existing?.status == ProposalStatus.AWAITING_CONFIRMATION) {
            state[SUPERSEDED_PROPOSAL_STATE_KEY] = toState(existing.copy(
                status = ProposalStatus.SUPERSEDED,
                updatedAt = Instant.now()
            ))
        }

        val now = Instant.now()
        val proposal = TradeProposal(
            proposalId = UUID.randomUUID(),
            conversationId = conversationId,
            userId = userId,
            accountId = accountId,
            orders = orders.map { order ->
                ProposedOrder(
                    ticker = order.ticker,
                    side = order.side,
                    quantity = order.quantity,
                    idempotencyKey = UUID.randomUUID()
                )
            },
            status = ProposalStatus.AWAITING_CONFIRMATION,
            createdAt = now,
            updatedAt = now
        )
        state[PROPOSAL_STATE_KEY] = toState(proposal)
        return proposal
    }

    fun getProposal(state: Map<String, Any>): TradeProposal? =
        (state[PROPOSAL_STATE_KEY] as? Map<*, *>)?.let(::fromState)

    fun getSupersededProposal(state: Map<String, Any>): TradeProposal? =
        (state[SUPERSEDED_PROPOSAL_STATE_KEY] as? Map<*, *>)?.let(::fromState)

    fun getPendingProposal(
        state: Map<String, Any>,
        conversationId: UUID,
        userId: UUID,
        accountId: UUID
    ): TradeProposal? =
        getProposal(state)
            ?.takeIf { it.status == ProposalStatus.AWAITING_CONFIRMATION }
            ?.also { requireScope(it, conversationId, userId, accountId) }

    fun recordOutcome(
        state: MutableMap<String, Any>,
        proposalId: UUID,
        orderIndex: Int,
        outcome: OrderOutcome
    ): TradeProposal {
        val proposal = requireProposal(state, proposalId)
        check(proposal.status == ProposalStatus.AWAITING_CONFIRMATION) {
            "Only an awaiting proposal can record order outcomes."
        }
        require(orderIndex in proposal.orders.indices) { "Order index is outside the proposal." }

        val updatedOrders = proposal.orders.toMutableList()
        updatedOrders[orderIndex] = updatedOrders[orderIndex].copy(outcome = outcome)
        return proposal.copy(orders = updatedOrders, updatedAt = Instant.now())
            .also { state[PROPOSAL_STATE_KEY] = toState(it) }
    }

    fun confirm(state: MutableMap<String, Any>, proposalId: UUID): TradeProposal {
        val proposal = requireProposal(state, proposalId)
        if (proposal.status == ProposalStatus.CONFIRMED) return proposal
        check(proposal.status == ProposalStatus.AWAITING_CONFIRMATION) {
            "Only an awaiting proposal can be confirmed."
        }
        return proposal.copy(status = ProposalStatus.CONFIRMED, updatedAt = Instant.now())
            .also { state[PROPOSAL_STATE_KEY] = toState(it) }
    }

    fun decline(state: MutableMap<String, Any>, proposalId: UUID): TradeProposal {
        val proposal = requireProposal(state, proposalId)
        if (proposal.status == ProposalStatus.DECLINED) return proposal
        check(proposal.status == ProposalStatus.AWAITING_CONFIRMATION) {
            "Only an awaiting proposal can be declined."
        }
        return proposal.copy(status = ProposalStatus.DECLINED, updatedAt = Instant.now())
            .also { state[PROPOSAL_STATE_KEY] = toState(it) }
    }

    private fun requireProposal(state: Map<String, Any>, proposalId: UUID): TradeProposal =
        requireNotNull(getProposal(state)) { "No trade proposal exists in this conversation." }
            .also { require(it.proposalId == proposalId) { "Trade proposal has changed." } }

    private fun requireScope(proposal: TradeProposal, conversationId: UUID, userId: UUID, accountId: UUID) {
        check(proposal.conversationId == conversationId) {
            "Trade proposal conversation does not match the agent session."
        }
        check(proposal.userId == userId) { "Trade proposal user does not match the authenticated user." }
        check(proposal.accountId == accountId) { "Trade proposal account does not match the conversation account." }
    }

    private fun toState(proposal: TradeProposal): Map<String, Any> = mapOf(
        "proposalId" to proposal.proposalId.toString(),
        "conversationId" to proposal.conversationId.toString(),
        "userId" to proposal.userId.toString(),
        "accountId" to proposal.accountId.toString(),
        "orders" to proposal.orders.map { order ->
            mapOf(
                "ticker" to order.ticker,
                "side" to order.side,
                "quantity" to order.quantity.toPlainString(),
                "idempotencyKey" to order.idempotencyKey.toString(),
                "outcome" to (order.outcome?.let(::outcomeToState) ?: emptyMap<String, Any>())
            )
        },
        "status" to proposal.status.name,
        "createdAt" to proposal.createdAt.toString(),
        "updatedAt" to proposal.updatedAt.toString()
    )

    private fun outcomeToState(outcome: OrderOutcome): Map<String, Any> = buildMap {
        put("status", outcome.status.name)
        outcome.orderId?.let { put("orderId", it.toString()) }
        outcome.executionPrice?.let { put("executionPrice", it.toPlainString()) }
        outcome.totalAmount?.let { put("totalAmount", it.toPlainString()) }
        outcome.rejectionReason?.let { put("rejectionReason", it) }
        outcome.errorMessage?.let { put("errorMessage", it) }
    }

    private fun fromState(value: Map<*, *>): TradeProposal {
        val orders = (value["orders"] as? List<*>)?.map { line ->
            val order = line as? Map<*, *> ?: error("Invalid trade proposal order.")
            val outcome = (order["outcome"] as? Map<*, *>)
                ?.takeIf { it.isNotEmpty() }
                ?.let(::outcomeFromState)
            ProposedOrder(
                ticker = order.requiredString("ticker"),
                side = order.requiredString("side"),
                quantity = BigDecimal(order.requiredString("quantity")),
                idempotencyKey = UUID.fromString(order.requiredString("idempotencyKey")),
                outcome = outcome
            )
        } ?: error("Invalid trade proposal orders.")
        return TradeProposal(
            proposalId = UUID.fromString(value.requiredString("proposalId")),
            conversationId = UUID.fromString(value.requiredString("conversationId")),
            userId = UUID.fromString(value.requiredString("userId")),
            accountId = UUID.fromString(value.requiredString("accountId")),
            orders = orders,
            status = ProposalStatus.valueOf(value.requiredString("status")),
            createdAt = Instant.parse(value.requiredString("createdAt")),
            updatedAt = Instant.parse(value.requiredString("updatedAt"))
        )
    }

    private fun outcomeFromState(value: Map<*, *>): OrderOutcome = OrderOutcome(
        status = OutcomeStatus.valueOf(value.requiredString("status")),
        orderId = value.optionalString("orderId")?.let(UUID::fromString),
        executionPrice = value.optionalString("executionPrice")?.let(::BigDecimal),
        totalAmount = value.optionalString("totalAmount")?.let(::BigDecimal),
        rejectionReason = value.optionalString("rejectionReason"),
        errorMessage = value.optionalString("errorMessage")
    )

    private fun Map<*, *>.requiredString(key: String): String =
        optionalString(key) ?: error("Invalid trade proposal field: $key")

    private fun Map<*, *>.optionalString(key: String): String? =
        (this[key] as? String)
}
