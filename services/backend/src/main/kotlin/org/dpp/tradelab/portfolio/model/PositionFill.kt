package org.dpp.tradelab.portfolio.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.springframework.data.domain.Persistable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.jvm.JvmName

@Entity
@Table(name = "position_fills")
class PositionFill(
    @field:Id
    @field:Column(nullable = false, updatable = false)
    @get:JvmName("getPositionFillId")
    val id: UUID,

    @Column(nullable = false, updatable = false)
    val userId: UUID,

    @Column(nullable = false, updatable = false)
    val accountId: UUID,

    @Column(nullable = false, updatable = false)
    val ticker: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    val assetType: AssetType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    val side: FillSide,

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    val executionPrice: BigDecimal,

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    val quantity: BigDecimal,

    @Column(nullable = false, updatable = false)
    val filledAt: Instant,

    @Column(nullable = false, updatable = false, unique = true)
    val idempotencyKey: UUID,

    @Transient
    private val _isNew: Boolean = true
) : Persistable<UUID> {

    override fun getId(): UUID = id

    override fun isNew(): Boolean = _isNew

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PositionFill) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "PositionFill(id=$id, userId=$userId, accountId=$accountId, ticker=$ticker, " +
            "assetType=$assetType, side=$side, executionPrice=$executionPrice, quantity=$quantity, " +
            "filledAt=$filledAt, idempotencyKey=$idempotencyKey)"
}
