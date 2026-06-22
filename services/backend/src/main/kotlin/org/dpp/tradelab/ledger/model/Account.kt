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

@Entity
@Table(name = "accounts")
data class Account(
    @Id
    @Column(nullable = false, updatable = false)
    // @get:JvmName renames the auto-generated Kotlin getter at the JVM level so it
    // no longer clashes with the getId() method required by Persistable<UUID>.
    @get:JvmName("getAccountId")
    val id: UUID,

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

    // Tells Spring Data JPA to always call persist() instead of merge().
    // Required because we pre-assign the UUID — without this, a non-null id
    // causes SimpleJpaRepository to call merge(), which issues a stale UPDATE
    // before the INSERT and throws StaleObjectStateException.
    @Transient
    private val _isNew: Boolean = true
) : Persistable<UUID> {
    override fun getId(): UUID = id
    override fun isNew(): Boolean = _isNew
}
