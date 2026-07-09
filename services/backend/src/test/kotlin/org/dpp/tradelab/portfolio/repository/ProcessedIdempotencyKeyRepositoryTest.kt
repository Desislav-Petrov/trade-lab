package org.dpp.tradelab.portfolio.repository

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.dpp.tradelab.portfolio.model.ProcessedIdempotencyKey
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import java.time.Instant
import java.util.UUID

@DataJpaTest
class ProcessedIdempotencyKeyRepositoryTest(
    private val repository: ProcessedIdempotencyKeyRepository,
    private val em: TestEntityManager,
) : DescribeSpec({

    extension(SpringExtension)

    describe("ProcessedIdempotencyKeyRepository.existsByIdempotencyKey") {

        it("existsByIdempotencyKey_keyIsPresent_returnsTrue") {
            val idempotencyKey = UUID.randomUUID()
            val record = ProcessedIdempotencyKey(
                id = UUID.randomUUID(),
                idempotencyKey = idempotencyKey,
                processedAt = Instant.now()
            )
            repository.save(record)
            em.flush()
            em.clear()

            val result = repository.existsByIdempotencyKey(idempotencyKey)

            result shouldBe true
        }

        it("existsByIdempotencyKey_keyIsAbsent_returnsFalse") {
            val result = repository.existsByIdempotencyKey(UUID.randomUUID())

            result shouldBe false
        }

        it("existsByIdempotencyKey_differentKeyInDb_returnsFalse") {
            val storedKey = UUID.randomUUID()
            val queriedKey = UUID.randomUUID()

            val record = ProcessedIdempotencyKey(
                id = UUID.randomUUID(),
                idempotencyKey = storedKey,
                processedAt = Instant.now()
            )
            repository.save(record)
            em.flush()
            em.clear()

            val result = repository.existsByIdempotencyKey(queriedKey)

            result shouldBe false
        }
    }
})
