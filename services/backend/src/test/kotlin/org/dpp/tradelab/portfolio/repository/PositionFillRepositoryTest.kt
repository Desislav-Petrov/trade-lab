package org.dpp.tradelab.portfolio.repository

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.portfolio.model.AssetType
import org.dpp.tradelab.portfolio.model.FillSide
import org.dpp.tradelab.portfolio.model.PositionFill
import org.springframework.boot.jpa.test.autoconfigure.AutoConfigureTestEntityManager
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureTestEntityManager
@Transactional
class PositionFillRepositoryTest(
    private val repository: PositionFillRepository,
    private val em: TestEntityManager
) : DescribeSpec({

    extension(SpringExtension)

    fun buildFill(
        userId: UUID,
        accountId: UUID,
        ticker: String,
        filledAt: Instant
    ) = PositionFill(
        id = UUID.randomUUID(),
        userId = userId,
        accountId = accountId,
        ticker = ticker,
        assetType = AssetType.STOCK,
        side = FillSide.BUY,
        executionPrice = BigDecimal("150.0000"),
        quantity = BigDecimal("2.0000"),
        filledAt = filledAt,
        idempotencyKey = UUID.randomUUID()
    )

    describe("PositionFillRepository.findByUserIdAndAccountIdOrderByFilledAtAsc") {

        it("findByUserIdAndAccountIdOrderByFilledAtAsc_returnsResultsOrderedByFilledAtAscending") {
            val userId = UUID.randomUUID()
            val accountId = UUID.randomUUID()
            val later = buildFill(userId, accountId, "AAPL", Instant.parse("2026-08-28T12:00:00Z"))
            val earlier = buildFill(userId, accountId, "AAPL", Instant.parse("2026-08-28T10:00:00Z"))
            em.persist(later)
            em.persist(earlier)
            em.flush()
            em.clear()

            val result = repository.findByUserIdAndAccountIdOrderByFilledAtAsc(
                userId,
                accountId,
                PageRequest.of(0, 100)
            )

            result.content.map { it.filledAt } shouldBe listOf(earlier.filledAt, later.filledAt)
        }

        it("findByUserIdAndAccountIdOrderByFilledAtAsc_scopesResultsToUserIdAndAccountId") {
            val userId = UUID.randomUUID()
            val accountId = UUID.randomUUID()
            val matching = buildFill(userId, accountId, "AAPL", Instant.parse("2026-08-28T10:00:00Z"))
            em.persist(matching)
            em.persist(buildFill(UUID.randomUUID(), accountId, "AAPL", Instant.parse("2026-08-28T11:00:00Z")))
            em.persist(buildFill(userId, UUID.randomUUID(), "AAPL", Instant.parse("2026-08-28T12:00:00Z")))
            em.flush()
            em.clear()

            val result = repository.findByUserIdAndAccountIdOrderByFilledAtAsc(
                userId,
                accountId,
                PageRequest.of(0, 100)
            )

            result.content shouldHaveSize 1
            result.content.first().id shouldBe matching.id
        }

        it("findByUserIdAndAccountIdOrderByFilledAtAsc_respectsPageSize") {
            val userId = UUID.randomUUID()
            val accountId = UUID.randomUUID()
            val baseTime = Instant.parse("2026-08-28T10:00:00Z")
            (0 until 150).forEach { index ->
                em.persist(buildFill(userId, accountId, "AAPL", baseTime.plusSeconds(index.toLong())))
            }
            em.flush()
            em.clear()

            val firstPage = repository.findByUserIdAndAccountIdOrderByFilledAtAsc(
                userId,
                accountId,
                PageRequest.of(0, 100)
            )
            val secondPage = repository.findByUserIdAndAccountIdOrderByFilledAtAsc(
                userId,
                accountId,
                PageRequest.of(1, 100)
            )

            firstPage.content shouldHaveSize 100
            secondPage.content shouldHaveSize 50
        }
    }
})
