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
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "ledger_entries")
data class LedgerEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    val id: UUID? = null,

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

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    val createdAt: Instant? = null
)
