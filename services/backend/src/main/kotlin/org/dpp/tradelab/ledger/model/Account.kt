package org.dpp.tradelab.ledger.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "accounts")
data class Account(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    val id: UUID? = null,

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
    val updatedAt: Instant? = null
)
