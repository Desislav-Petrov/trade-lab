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

        it("existsByIdempotencyKey_keyPresent_returnsTrue") {
            val idempotencyKey = UUID.randomUUID()

            val processedKey = ProcessedIdempotencyKey(
                keyId = UUID.randomUUID(),
                idempotencyKey = idempotencyKey,
                processedAt = Instant.now()
            )
            repository.save(processedKey)
            em.flush()
            em.clear()

            val exists = repository.existsByIdempotencyKey(idempotencyKey)

            exists shouldBe true
        }

        it("existsByIdempotencyKey_keyAbsent_returnsFalse") {
            val idempotencyKey = UUID.randomUUID()

            val exists = repository.existsByIdempotencyKey(idempotencyKey)

            exists shouldBe false
        }

        it("existsByIdempotencyKey_differentKeyPresent_returnsFalse") {
            val storedKey = UUID.randomUUID()
            val queryKey = UUID.randomUUID()

            val processedKey = ProcessedIdempotencyKey(
                keyId = UUID.randomUUID(),
                idempotencyKey = storedKey,
                processedAt = Instant.now()
            )
            repository.save(processedKey)
            em.flush()
            em.clear()

            val exists = repository.existsByIdempotencyKey(queryKey)

            exists shouldBe false
        }
    }
})
