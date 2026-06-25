package org.dpp.tradelab.marketdata.repository

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.marketdata.model.AssetSubscription
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import java.util.UUID

@DataJpaTest
class AssetSubscriptionRepositoryTest(
    private val repository: AssetSubscriptionRepository,
    private val em: TestEntityManager,
) : DescribeSpec({

    extension(SpringExtension)

    describe("AssetSubscriptionRepository") {

        describe("findAllByUserIdOrderByTickerAsc") {

            it("findAllByUserIdOrderByTickerAsc_userWithSubscriptions_returnsOrderedList") {
                val userId = UUID.randomUUID()

                val subMsft = AssetSubscription(
                    subscriptionId = UUID.randomUUID(),
                    userId = userId,
                    ticker = "MSFT",
                    companyName = "Microsoft Corporation"
                )
                val subAapl = AssetSubscription(
                    subscriptionId = UUID.randomUUID(),
                    userId = userId,
                    ticker = "AAPL",
                    companyName = "Apple Inc."
                )
                val subNvda = AssetSubscription(
                    subscriptionId = UUID.randomUUID(),
                    userId = userId,
                    ticker = "NVDA",
                    companyName = "NVIDIA Corporation"
                )

                repository.save(subMsft)
                repository.save(subAapl)
                repository.save(subNvda)
                em.flush()
                em.clear()

                val result = repository.findAllByUserIdOrderByTickerAsc(userId)

                result.size shouldBe 3
                result[0].ticker shouldBe "AAPL"
                result[1].ticker shouldBe "MSFT"
                result[2].ticker shouldBe "NVDA"
            }

            it("findAllByUserIdOrderByTickerAsc_userWithNoSubscriptions_returnsEmptyList") {
                val userId = UUID.randomUUID()

                val result = repository.findAllByUserIdOrderByTickerAsc(userId)

                result.size shouldBe 0
            }

            it("findAllByUserIdOrderByTickerAsc_doesNotReturnOtherUsersSubscriptions") {
                val userId = UUID.randomUUID()
                val otherUserId = UUID.randomUUID()

                repository.save(
                    AssetSubscription(
                        subscriptionId = UUID.randomUUID(),
                        userId = otherUserId,
                        ticker = "AAPL",
                        companyName = "Apple Inc."
                    )
                )
                em.flush()
                em.clear()

                val result = repository.findAllByUserIdOrderByTickerAsc(userId)

                result.size shouldBe 0
            }
        }

        describe("findAllByUserIdAndTickerIn") {

            it("findAllByUserIdAndTickerIn_matchingTickers_returnsMatches") {
                val userId = UUID.randomUUID()

                repository.save(
                    AssetSubscription(
                        subscriptionId = UUID.randomUUID(),
                        userId = userId,
                        ticker = "AAPL",
                        companyName = "Apple Inc."
                    )
                )
                repository.save(
                    AssetSubscription(
                        subscriptionId = UUID.randomUUID(),
                        userId = userId,
                        ticker = "MSFT",
                        companyName = "Microsoft Corporation"
                    )
                )
                repository.save(
                    AssetSubscription(
                        subscriptionId = UUID.randomUUID(),
                        userId = userId,
                        ticker = "GOOGL",
                        companyName = "Alphabet Inc."
                    )
                )
                em.flush()
                em.clear()

                val result = repository.findAllByUserIdAndTickerIn(userId, listOf("AAPL", "MSFT"))

                result.size shouldBe 2
                result.map { it.ticker }.toSet() shouldBe setOf("AAPL", "MSFT")
            }

            it("findAllByUserIdAndTickerIn_noMatchingTickers_returnsEmptyList") {
                val userId = UUID.randomUUID()

                repository.save(
                    AssetSubscription(
                        subscriptionId = UUID.randomUUID(),
                        userId = userId,
                        ticker = "AAPL",
                        companyName = "Apple Inc."
                    )
                )
                em.flush()
                em.clear()

                val result = repository.findAllByUserIdAndTickerIn(userId, listOf("TSLA", "NVDA"))

                result.size shouldBe 0
            }
        }

        describe("countByUserId") {

            it("countByUserId_userWithSubscriptions_returnsCorrectCount") {
                val userId = UUID.randomUUID()

                repository.save(
                    AssetSubscription(
                        subscriptionId = UUID.randomUUID(),
                        userId = userId,
                        ticker = "AAPL",
                        companyName = "Apple Inc."
                    )
                )
                repository.save(
                    AssetSubscription(
                        subscriptionId = UUID.randomUUID(),
                        userId = userId,
                        ticker = "MSFT",
                        companyName = "Microsoft Corporation"
                    )
                )
                em.flush()
                em.clear()

                val count = repository.countByUserId(userId)

                count shouldBe 2L
            }

            it("countByUserId_userWithNoSubscriptions_returnsZero") {
                val userId = UUID.randomUUID()

                val count = repository.countByUserId(userId)

                count shouldBe 0L
            }
        }
    }
})
