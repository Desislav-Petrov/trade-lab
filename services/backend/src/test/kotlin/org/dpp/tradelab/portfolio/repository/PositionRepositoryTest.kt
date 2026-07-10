package org.dpp.tradelab.portfolio.repository

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.portfolio.model.AssetType
import org.dpp.tradelab.portfolio.model.Position
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@DataJpaTest
class PositionRepositoryTest(
    private val repository: PositionRepository,
    private val em: TestEntityManager,
) : DescribeSpec({

    extension(SpringExtension)

    fun buildPosition(
        userId: UUID,
        accountId: UUID,
        ticker: String,
        quantity: BigDecimal = BigDecimal("10.0000")
    ) = Position(
        positionId = UUID.randomUUID(),
        userId = userId,
        accountId = accountId,
        ticker = ticker,
        assetType = AssetType.STOCK,
        quantity = quantity,
        totalCost = quantity.multiply(BigDecimal("100.0000")),
        avgPrice = BigDecimal("100.0000"),
        minPrice = BigDecimal("95.0000"),
        maxPrice = BigDecimal("105.0000"),
        lastUpdated = Instant.now()
    )

    describe("PositionRepository.findByUserIdAndAccountIdAndTicker") {

        it("findByUserIdAndAccountIdAndTicker_matchingRow_returnsPosition") {
            val userId = UUID.randomUUID()
            val accountId = UUID.randomUUID()
            val ticker = "AAPL"

            val position = buildPosition(userId, accountId, ticker)
            repository.save(position)
            em.flush()
            em.clear()

            val result = repository.findByUserIdAndAccountIdAndTicker(userId, accountId, ticker)

            result.isPresent shouldBe true
            result.get().ticker shouldBe ticker
            result.get().userId shouldBe userId
            result.get().accountId shouldBe accountId
        }

        it("findByUserIdAndAccountIdAndTicker_noMatchingRow_returnsEmpty") {
            val userId = UUID.randomUUID()
            val accountId = UUID.randomUUID()

            val result = repository.findByUserIdAndAccountIdAndTicker(userId, accountId, "UNKNOWN")

            result.isPresent shouldBe false
        }

        it("findByUserIdAndAccountIdAndTicker_differentUser_returnsEmpty") {
            val userId = UUID.randomUUID()
            val differentUserId = UUID.randomUUID()
            val accountId = UUID.randomUUID()
            val ticker = "MSFT"

            val position = buildPosition(userId, accountId, ticker)
            repository.save(position)
            em.flush()
            em.clear()

            val result = repository.findByUserIdAndAccountIdAndTicker(differentUserId, accountId, ticker)

            result.isPresent shouldBe false
        }
    }

    describe("PositionRepository.findAllByAccountIdAndQuantityGreaterThan") {

        it("findAllByAccountIdAndQuantityGreaterThan_withPositiveQuantity_returnsMatchingPositions") {
            val userId = UUID.randomUUID()
            val accountId = UUID.randomUUID()

            val activePosition1 = buildPosition(userId, accountId, "AAPL", BigDecimal("5.0000"))
            val activePosition2 = buildPosition(userId, accountId, "MSFT", BigDecimal("3.0000"))
            val zeroPosition = buildPosition(userId, accountId, "GOOG", BigDecimal("0.0000"))
            val otherAccountPosition = buildPosition(userId, UUID.randomUUID(), "AAPL", BigDecimal("10.0000"))

            repository.saveAll(listOf(activePosition1, activePosition2, zeroPosition, otherAccountPosition))
            em.flush()
            em.clear()

            val result = repository.findAllByAccountIdAndQuantityGreaterThan(accountId, BigDecimal.ZERO)

            result shouldHaveSize 2
            result.all { it.accountId == accountId } shouldBe true
            result.all { it.quantity > BigDecimal.ZERO } shouldBe true
        }

        it("findAllByAccountIdAndQuantityGreaterThan_allZeroQuantity_returnsEmpty") {
            val userId = UUID.randomUUID()
            val accountId = UUID.randomUUID()

            val zeroPosition = buildPosition(userId, accountId, "AAPL", BigDecimal("0.0000"))
            repository.save(zeroPosition)
            em.flush()
            em.clear()

            val result = repository.findAllByAccountIdAndQuantityGreaterThan(accountId, BigDecimal.ZERO)

            result.shouldBeEmpty()
        }

        it("findAllByAccountIdAndQuantityGreaterThan_noPositionsForAccount_returnsEmpty") {
            val result = repository.findAllByAccountIdAndQuantityGreaterThan(UUID.randomUUID(), BigDecimal.ZERO)

            result.shouldBeEmpty()
        }
    }
})
