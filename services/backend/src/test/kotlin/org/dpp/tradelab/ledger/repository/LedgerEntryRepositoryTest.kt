package org.dpp.tradelab.ledger.repository

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.dpp.tradelab.ledger.model.AssetType
import org.dpp.tradelab.ledger.model.EntryType
import org.dpp.tradelab.ledger.model.LedgerEntry
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import java.math.BigDecimal
import java.util.UUID

@DataJpaTest
class LedgerEntryRepositoryTest(
    private val repository: LedgerEntryRepository,
    private val em: TestEntityManager,
) : DescribeSpec({

    extension(SpringExtension)

    describe("LedgerEntryRepository") {

        it("save_validLedgerEntry_persistsAndCanBeFoundById") {
            val entryId = UUID.randomUUID()
            val accountId = UUID.randomUUID()

            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("1000"),
                currency = "USD",
                description = "Top-up"
            )

            repository.save(entry)
            em.flush()
            em.clear()

            val found = repository.findById(entryId)

            found.isPresent shouldBe true
            val saved = found.get()
            saved.entryId shouldBe entryId
            saved.accountId shouldBe accountId
            saved.type shouldBe EntryType.CREDIT
            saved.assetType shouldBe AssetType.CASH
            saved.amount.compareTo(BigDecimal("1000")) shouldBe 0
            saved.currency shouldBe "USD"
            saved.description shouldBe "Top-up"
            saved.createdAt shouldNotBe null
        }
    }
})
