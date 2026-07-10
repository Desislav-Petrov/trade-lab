package org.dpp.tradelab.portfolio.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "portfolio_processed_events")
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
