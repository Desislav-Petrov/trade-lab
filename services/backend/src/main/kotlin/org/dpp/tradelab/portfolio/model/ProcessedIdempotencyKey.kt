package org.dpp.tradelab.portfolio.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

/**
 * JPA entity representing a processed idempotency key for portfolio domain events.
 *
 * This is a lean idempotency-guard table that stores only the keys of events
 * the Portfolio domain has already processed — no event payload data. Before any
 * position update the service checks this table; if the key is present the event
 * is discarded; if absent the key is inserted and the position update proceeds,
 * both within the same transaction.
 *
 * Implements [Persistable] with a transient [_isNew] flag so that Spring Data
 * always calls EntityManager.persist() — never merge() — even though the UUID
 * is pre-assigned by the service layer before construction.
 *
 * The primary-key property is named [keyId] (not [id]) to avoid a JVM
 * platform declaration clash: Kotlin auto-generates a getId() getter for any
 * property named `id`, which collides with the Persistable.getId() override.
 */
@Entity
@Table(name = "processed_idempotency_keys")
class ProcessedIdempotencyKey(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val keyId: UUID,

    @Column(nullable = false, unique = true, updatable = false)
    val idempotencyKey: UUID,

    @Column(nullable = false, updatable = false)
    val processedAt: Instant,

    @Transient
    private val _isNew: Boolean = true
) : Persistable<UUID> {

    override fun getId(): UUID = keyId

    override fun isNew(): Boolean = _isNew

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProcessedIdempotencyKey) return false
        return keyId == other.keyId
    }

    override fun hashCode(): Int = keyId.hashCode()

    override fun toString(): String =
        "ProcessedIdempotencyKey(keyId=$keyId, idempotencyKey=$idempotencyKey, processedAt=$processedAt)"
}
