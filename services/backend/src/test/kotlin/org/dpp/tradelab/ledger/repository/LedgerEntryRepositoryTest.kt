package org.dpp.tradelab.ledger.repository

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.dpp.tradelab.ledger.model.AssetType
import org.dpp.tradelab.ledger.model.EntryType
import org.dpp.tradelab.ledger.model.LedgerEntry
import org.springframework.boot.jpa.test.autoconfigure.AutoConfigureTestEntityManager
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest
@AutoConfigureTestEntityManager
@Transactional
class LedgerEntryRepositoryTest(
    private val repository: LedgerEntryRepository,
    private val em: TestEntityManager,
) : DescribeSpec({

    extension(SpringExtension)

    describe("LedgerEntryRepository.findByAccountId") {

        it("findByAccountId_multipleEntriesForAccount_returnsEntriesInCreatedAtDescOrder") {
            val accountId = UUID.randomUUID()

            val entry1 = LedgerEntry(
                entryId = UUID.randomUUID(),
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("100.0000"),
                currency = "USD",
                description = "First"
            )
            em.persistAndFlush(entry1)
            Thread.sleep(2)

            val entry2 = LedgerEntry(
                entryId = UUID.randomUUID(),
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("200.0000"),
                currency = "USD",
                description = "Second"
            )
            em.persistAndFlush(entry2)
            Thread.sleep(2)

            val entry3 = LedgerEntry(
                entryId = UUID.randomUUID(),
                accountId = accountId,
                type = EntryType.DEBIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("50.0000"),
                currency = "USD",
                description = "Third"
            )
            em.persistAndFlush(entry3)
            em.clear()

            val pageable = PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "createdAt"))
            val result = repository.findByAccountId(accountId, pageable)

            result.totalElements shouldBe 3
            result.content shouldHaveSize 3

            val timestamps = result.content.map { it.createdAt }
            timestamps[0] shouldNotBe null
            timestamps[1] shouldNotBe null
            timestamps[2] shouldNotBe null
            timestamps[0]!!.isAfter(timestamps[1]!!) shouldBe true
            timestamps[1]!!.isAfter(timestamps[2]!!) shouldBe true
        }

        it("findByAccountId_noEntriesForAccount_returnsEmptyPage") {
            val unknownAccountId = UUID.randomUUID()

            val pageable = PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "createdAt"))
            val result = repository.findByAccountId(unknownAccountId, pageable)

            result.totalElements shouldBe 0
            result.content shouldHaveSize 0
        }

        it("findByAccountId_thirtyEntries_pageSizeRespectedAcrossPages") {
            val accountId = UUID.randomUUID()

            for (i in 1..30) {
                val entry = LedgerEntry(
                    entryId = UUID.randomUUID(),
                    accountId = accountId,
                    type = EntryType.CREDIT,
                    assetType = AssetType.CASH,
                    amount = BigDecimal("${i}.0000"),
                    currency = "USD"
                )
                em.persistAndFlush(entry)
                if (i < 30) Thread.sleep(2)
            }
            em.clear()

            val pageZero = repository.findByAccountId(
                accountId,
                PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "createdAt"))
            )
            val pageOne = repository.findByAccountId(
                accountId,
                PageRequest.of(1, 25, Sort.by(Sort.Direction.DESC, "createdAt"))
            )

            pageZero.content shouldHaveSize 25
            pageOne.content shouldHaveSize 5
            pageZero.totalElements shouldBe 30
            pageOne.totalElements shouldBe 30
        }
    }
})
