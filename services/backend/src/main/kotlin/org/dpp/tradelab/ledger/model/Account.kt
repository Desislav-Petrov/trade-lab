package org.dpp.tradelab.ledger.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.domain.Persistable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for a trading account.
 *
 * Implements [Persistable] with a transient [_isNew] flag so that Spring Data
 * always calls EntityManager.persist() — never merge() — even though the UUID
 * is pre-assigned by the service layer before construction. Without this,
 * a non-null id causes SimpleJpaRepository to call merge(), which issues a
 * stale UPDATE before the INSERT and throws StaleObjectStateException.
 *
 * The primary-key property is named [accountId] (not [id]) to avoid a JVM
 * platform declaration clash: Kotlin auto-generates a getId() getter for any
 * property named `id`, which collides with the Persistable.getId() override.
 */
@Entity
@Table(name = "accounts")
class Account(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val accountId: UUID,

    @Column(nullable = false, updatable = false)
    val userId: UUID,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, precision = 19, scale = 4)
    val balance: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    val currency: Currency,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: AccountStatus = AccountStatus.ACTIVE,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(nullable = false)
    val updatedAt: Instant? = null,

    @Transient
    private val _isNew: Boolean = true
) : Persistable<UUID> {

    override fun getId(): UUID = accountId

    override fun isNew(): Boolean = _isNew

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Account) return false
        return accountId == other.accountId
    }

    override fun hashCode(): Int = accountId.hashCode()

    override fun toString(): String =
        "Account(accountId=$accountId, userId=$userId, name=$name, currency=$currency, status=$status, balance=$balance)"
}
