package org.dpp.tradelab.ledger.repository

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.ledger.model.Account
import org.dpp.tradelab.ledger.model.AccountStatus
import org.dpp.tradelab.ledger.model.Currency
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import java.util.UUID

@DataJpaTest
class AccountRepositoryTest(
    private val repository: AccountRepository,
    private val em: TestEntityManager,
) : DescribeSpec({

    extension(SpringExtension)

    describe("AccountRepository.findAllByUserIdAndStatus") {

        it("findAllByUserIdAndStatus_matchingUserAndStatus_returnsOnlyMatchingAccounts") {
            val userId = UUID.randomUUID()
            val otherUserId = UUID.randomUUID()

            val activeAccount1 = Account(
                accountId = UUID.randomUUID(),
                userId = userId,
                name = "Active 1",
                currency = Currency.USD,
                status = AccountStatus.ACTIVE
            )
            val activeAccount2 = Account(
                accountId = UUID.randomUUID(),
                userId = userId,
                name = "Active 2",
                currency = Currency.GBP,
                status = AccountStatus.ACTIVE
            )
            val suspendedAccount = Account(
                accountId = UUID.randomUUID(),
                userId = userId,
                name = "Suspended",
                currency = Currency.USD,
                status = AccountStatus.SUSPENDED
            )
            val otherUserAccount = Account(
                accountId = UUID.randomUUID(),
                userId = otherUserId,
                name = "Other User",
                currency = Currency.USD,
                status = AccountStatus.ACTIVE
            )

            repository.saveAll(listOf(activeAccount1, activeAccount2, suspendedAccount, otherUserAccount))
            em.flush()
            em.clear()

            val result = repository.findAllByUserIdAndStatus(userId, AccountStatus.ACTIVE)

            result shouldHaveSize 2
            result.all { it.userId == userId } shouldBe true
            result.all { it.status == AccountStatus.ACTIVE } shouldBe true
        }

        it("findAllByUserIdAndStatus_noMatchingAccounts_returnsEmptyList") {
            val userId = UUID.randomUUID()

            val closedAccount = Account(
                accountId = UUID.randomUUID(),
                userId = userId,
                name = "Closed",
                currency = Currency.EUR,
                status = AccountStatus.CLOSED
            )
            repository.save(closedAccount)
            em.flush()
            em.clear()

            val result = repository.findAllByUserIdAndStatus(userId, AccountStatus.ACTIVE)

            result.shouldBeEmpty()
        }
    }
})
