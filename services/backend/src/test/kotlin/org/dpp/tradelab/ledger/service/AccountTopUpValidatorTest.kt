package org.dpp.tradelab.ledger.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import org.dpp.tradelab.ledger.exception.AccountNotActiveException
import org.dpp.tradelab.ledger.model.Account
import org.dpp.tradelab.ledger.model.AccountStatus
import org.dpp.tradelab.ledger.model.Currency
import java.math.BigDecimal
import java.util.UUID

class AccountTopUpValidatorTest : FunSpec({

    val validator = AccountTopUpValidator()

    // ─── validateAmount ───────────────────────────────────────────────────────

    test("validateAmount_positiveWholeNumber_doesNotThrow") {
        validator.validateAmount(BigDecimal("1000"))
    }

    test("validateAmount_zero_throwsIllegalArgumentException") {
        val ex = shouldThrow<IllegalArgumentException> {
            validator.validateAmount(BigDecimal.ZERO)
        }
        ex.message shouldContain "greater than zero"
    }

    test("validateAmount_negative_throwsIllegalArgumentException") {
        val ex = shouldThrow<IllegalArgumentException> {
            validator.validateAmount(BigDecimal("-1"))
        }
        ex.message shouldContain "greater than zero"
    }

    test("validateAmount_decimal_throwsIllegalArgumentException") {
        val ex = shouldThrow<IllegalArgumentException> {
            validator.validateAmount(BigDecimal("1.5"))
        }
        ex.message shouldContain "whole number"
    }

    test("validateAmount_exactMaximum_doesNotThrow") {
        validator.validateAmount(BigDecimal("10000000"))
    }

    test("validateAmount_exceedsMaximum_throwsIllegalArgumentException") {
        val ex = shouldThrow<IllegalArgumentException> {
            validator.validateAmount(BigDecimal("10000001"))
        }
        ex.message shouldContain "10,000,000"
    }

    // ─── validateAccountEligibility ───────────────────────────────────────────

    test("validateAccountEligibility_activeAccountMatchingOwner_doesNotThrow") {
        val userId = UUID.randomUUID()
        val account = buildAccount(userId = userId, status = AccountStatus.ACTIVE)
        validator.validateAccountEligibility(account, userId)
    }

    test("validateAccountEligibility_ownerMismatch_throwsAccountNotActiveException") {
        val account = buildAccount(userId = UUID.randomUUID(), status = AccountStatus.ACTIVE)
        shouldThrow<AccountNotActiveException> {
            validator.validateAccountEligibility(account, UUID.randomUUID())
        }
    }

    test("validateAccountEligibility_suspendedAccount_throwsAccountNotActiveException") {
        val userId = UUID.randomUUID()
        val account = buildAccount(userId = userId, status = AccountStatus.SUSPENDED)
        shouldThrow<AccountNotActiveException> {
            validator.validateAccountEligibility(account, userId)
        }
    }

    test("validateAccountEligibility_closedAccount_throwsAccountNotActiveException") {
        val userId = UUID.randomUUID()
        val account = buildAccount(userId = userId, status = AccountStatus.CLOSED)
        shouldThrow<AccountNotActiveException> {
            validator.validateAccountEligibility(account, userId)
        }
    }
})

private fun buildAccount(userId: UUID, status: AccountStatus) = Account(
    accountId = UUID.randomUUID(),
    userId = userId,
    name = "Test Account",
    balance = BigDecimal.ZERO,
    currency = Currency.USD,
    status = status
)
