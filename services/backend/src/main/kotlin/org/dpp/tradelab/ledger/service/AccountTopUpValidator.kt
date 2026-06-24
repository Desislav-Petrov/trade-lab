package org.dpp.tradelab.ledger.service

import org.dpp.tradelab.ledger.exception.AccountNotActiveException
import org.dpp.tradelab.ledger.model.Account
import org.dpp.tradelab.ledger.model.AccountStatus
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID

@Component
class AccountTopUpValidator {

    fun validateAmount(amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO)
            throw IllegalArgumentException("amount must be greater than zero")
        if (amount.stripTrailingZeros().scale() > 0)
            throw IllegalArgumentException("amount must be a whole number")
        if (amount > BigDecimal(10_000_000))
            throw IllegalArgumentException("amount must not exceed 10,000,000")
    }

    fun validateAccountEligibility(account: Account, requestingUserId: UUID) {
        if (account.userId != requestingUserId)
            throw AccountNotActiveException(account.accountId)
        if (account.status != AccountStatus.ACTIVE)
            throw AccountNotActiveException(account.accountId)
    }
}
