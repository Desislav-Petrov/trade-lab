package org.dpp.tradelab.ledger.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.util.UUID

class AccountTest : StringSpec({

    "balance_mutated_newValueIsReflected" {
        val account = Account(
            accountId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            name = "Test Account",
            balance = BigDecimal("100.00"),
            currency = Currency.USD,
            status = AccountStatus.ACTIVE
        )

        account.balance = BigDecimal("250.50")

        account.balance shouldBe BigDecimal("250.50")
    }
})
