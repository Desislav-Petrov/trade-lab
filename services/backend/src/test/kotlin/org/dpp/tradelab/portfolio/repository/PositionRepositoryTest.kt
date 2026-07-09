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

    describe("PositionRepository.findByUserIdAndAccountIdAndTicker") {

        it("findByUserIdAndAccountIdAndTicker_matchingPosition_returnsPosition") {
            val userId = UUID.randomUUID()
            val accountId = UUID.randomUUID()
            val ticker = "AAPL"

            val position = Position(
                positionId = UUID.randomUUID(),
                userId = userId,
                accountId = accountId,
                ticker = ticker,
                assetType = AssetType.STOCK,
                quantity = BigDecimal("100.0000"),
                totalCost = BigDecimal("15000.0000"),
                avgPrice = BigDecimal("150.0000"),
                minPrice = BigDecimal("145.0000"),
                maxPrice = BigDecimal("155.0000"),
                lastUpdated = Instant.now()
            )
            repository.save(position)
            em.flush()
            em.clear()

            val result = repository.findByUserIdAndAccountIdAndTicker(userId, accountId, ticker)

            result.isPresent shouldBe true
            result.get().positionId shouldBe position.positionId
            result.get().ticker shouldBe ticker
            result.get().quantity shouldBe BigDecimal("100.0000")
        }

        it("findByUserIdAndAccountIdAndTicker_noMatchingPosition_returnsEmpty") {
            val userId = UUID.randomUUID()
            val accountId = UUID.randomUUID()
            val ticker = "AAPL"

            val position = Position(
                positionId = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                ticker = "GOOGL",
                assetType = AssetType.STOCK,
                quantity = BigDecimal("50.0000"),
                totalCost = BigDecimal("7500.0000"),
                avgPrice = BigDecimal("150.0000"),
                minPrice = BigDecimal("145.0000"),
                maxPrice = BigDecimal("155.0000"),
                lastUpdated = Instant.now()
            )
            repository.save(position)
            em.flush()
            em.clear()

            val result = repository.findByUserIdAndAccountIdAndTicker(userId, accountId, ticker)

            result.isPresent shouldBe false
        }
    }

    describe("PositionRepository.findAllByAccountIdAndQuantityGreaterThan") {

        it("findAllByAccountIdAndQuantityGreaterThan_multiplePositions_returnsOnlyAboveThreshold") {
            val accountId = UUID.randomUUID()
            val userId = UUID.randomUUID()

            val position1 = Position(
                positionId = UUID.randomUUID(),
                userId = userId,
                accountId = accountId,
                ticker = "AAPL",
                assetType = AssetType.STOCK,
                quantity = BigDecimal("100.0000"),
                totalCost = BigDecimal("15000.0000"),
                avgPrice = BigDecimal("150.0000"),
                minPrice = BigDecimal("145.0000"),
                maxPrice = BigDecimal("155.0000"),
                lastUpdated = Instant.now()
            )
            val position2 = Position(
                positionId = UUID.randomUUID(),
                userId = userId,
                accountId = accountId,
                ticker = "GOOGL",
                assetType = AssetType.STOCK,
                quantity = BigDecimal("0.0000"),
                totalCost = BigDecimal("0.0000"),
                avgPrice = BigDecimal("100.0000"),
                minPrice = BigDecimal("95.0000"),
                maxPrice = BigDecimal("105.0000"),
                lastUpdated = Instant.now()
            )
            val position3 = Position(
                positionId = UUID.randomUUID(),
                userId = userId,
                accountId = accountId,
                ticker = "TSLA",
                assetType = AssetType.STOCK,
                quantity = BigDecimal("50.0000"),
                totalCost = BigDecimal("10000.0000"),
                avgPrice = BigDecimal("200.0000"),
                minPrice = BigDecimal("195.0000"),
                maxPrice = BigDecimal("205.0000"),
                lastUpdated = Instant.now()
            )
            val otherAccountPosition = Position(
                positionId = UUID.randomUUID(),
                userId = userId,
                accountId = UUID.randomUUID(),
                ticker = "MSFT",
                assetType = AssetType.STOCK,
                quantity = BigDecimal("75.0000"),
                totalCost = BigDecimal("22500.0000"),
                avgPrice = BigDecimal("300.0000"),
                minPrice = BigDecimal("295.0000"),
                maxPrice = BigDecimal("305.0000"),
                lastUpdated = Instant.now()
            )

            repository.saveAll(listOf(position1, position2, position3, otherAccountPosition))
            em.flush()
            em.clear()

            val result = repository.findAllByAccountIdAndQuantityGreaterThan(accountId, BigDecimal.ZERO)

            result shouldHaveSize 2
            result.all { it.accountId == accountId } shouldBe true
            result.all { it.quantity > BigDecimal.ZERO } shouldBe true
            result.map { it.ticker }.toSet() shouldBe setOf("AAPL", "TSLA")
        }

        it("findAllByAccountIdAndQuantityGreaterThan_noPositionsAboveThreshold_returnsEmptyList") {
            val accountId = UUID.randomUUID()
            val userId = UUID.randomUUID()

            val position = Position(
                positionId = UUID.randomUUID(),
                userId = userId,
                accountId = accountId,
                ticker = "AAPL",
                assetType = AssetType.STOCK,
                quantity = BigDecimal("0.0000"),
                totalCost = BigDecimal("0.0000"),
                avgPrice = BigDecimal("150.0000"),
                minPrice = BigDecimal("145.0000"),
                maxPrice = BigDecimal("155.0000"),
                lastUpdated = Instant.now()
            )
            repository.save(position)
            em.flush()
            em.clear()

            val result = repository.findAllByAccountIdAndQuantityGreaterThan(accountId, BigDecimal.ZERO)

            result.shouldBeEmpty()
        }

        it("findAllByAccountIdAndQuantityGreaterThan_noPositionsForAccount_returnsEmptyList") {
            val accountId = UUID.randomUUID()
            val otherAccountId = UUID.randomUUID()
            val userId = UUID.randomUUID()

            val position = Position(
                positionId = UUID.randomUUID(),
                userId = userId,
                accountId = otherAccountId,
                ticker = "AAPL",
                assetType = AssetType.STOCK,
                quantity = BigDecimal("100.0000"),
                totalCost = BigDecimal("15000.0000"),
                avgPrice = BigDecimal("150.0000"),
                minPrice = BigDecimal("145.0000"),
                maxPrice = BigDecimal("155.0000"),
                lastUpdated = Instant.now()
            )
            repository.save(position)
            em.flush()
            em.clear()

            val result = repository.findAllByAccountIdAndQuantityGreaterThan(accountId, BigDecimal.ZERO)

            result.shouldBeEmpty()
        }
    }
})
