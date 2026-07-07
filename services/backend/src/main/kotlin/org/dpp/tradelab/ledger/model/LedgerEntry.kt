package org.dpp.tradelab.ledger.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.domain.Persistable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * JPA entity representing an immutable ledger entry.
 *
 * Implements [Persistable] with a transient [_isNew] flag so that Spring Data
 * always calls EntityManager.persist() — never merge() — even though the UUID
 * is pre-assigned by the service layer before construction. Without this,
 * a non-null id causes SimpleJpaRepository to call merge(), which issues a
 * stale UPDATE before the INSERT and throws StaleObjectStateException.
 *
 * The primary-key property is named [entryId] (not [id]) to avoid a JVM
 * platform declaration clash: Kotlin auto-generates a getId() getter for any
 * property named `id`, which collides with the Persistable.getId() override.
 */
@Entity
@Table(name = "ledger_entries")
class LedgerEntry(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val entryId: UUID,

    @Column(nullable = false, updatable = false)
    val accountId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    val type: EntryType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    val assetType: AssetType,

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    val amount: BigDecimal,

    @Column(nullable = false, updatable = false)
    val currency: String,

    @Column(nullable = true, updatable = false)
    val ticker: String? = null,

    @Column(nullable = true, precision = 19, scale = 4, updatable = false)
    val shares: BigDecimal? = null,

    @Column(nullable = true, updatable = false)
    val description: String? = null,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @Transient
    private val _isNew: Boolean = true
) : Persistable<UUID> {

    override fun getId(): UUID = entryId

    override fun isNew(): Boolean = _isNew

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LedgerEntry) return false
        return entryId == other.entryId
    }

    override fun hashCode(): Int = entryId.hashCode()

    override fun toString(): String =
        "LedgerEntry(entryId=$entryId, accountId=$accountId, type=$type, assetType=$assetType, " +
            "amount=$amount, currency=$currency, ticker=$ticker, shares=$shares, " +
            "description=$description, createdAt=$createdAt)"
}
