package org.dpp.tradelab.portfolio.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import jakarta.persistence.UniqueConstraint
import org.springframework.data.domain.Persistable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "positions",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_position_user_account_ticker",
            columnNames = ["user_id", "account_id", "ticker"]
        )
    ]
)
class Position(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val positionId: UUID,

    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,

    @Column(name = "account_id", nullable = false, updatable = false)
    val accountId: UUID,

    @Column(nullable = false, updatable = false)
    val ticker: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val assetType: AssetType,

    @Column(nullable = false, precision = 19, scale = 4)
    var quantity: BigDecimal,

    @Column(nullable = false, precision = 19, scale = 4)
    var totalCost: BigDecimal,

    @Column(precision = 19, scale = 4)
    var avgPrice: BigDecimal?,

    @Column(nullable = false, precision = 19, scale = 4)
    var minPrice: BigDecimal,

    @Column(nullable = false, precision = 19, scale = 4)
    var maxPrice: BigDecimal,

    @Column(nullable = false)
    var lastUpdated: Instant,

    @Transient
    private val _isNew: Boolean = true
) : Persistable<UUID> {

    override fun getId(): UUID = positionId

    override fun isNew(): Boolean = _isNew

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Position) return false
        return positionId == other.positionId
    }

    override fun hashCode(): Int = positionId.hashCode()

    override fun toString(): String =
        "Position(positionId=$positionId, userId=$userId, accountId=$accountId, ticker=$ticker, " +
            "assetType=$assetType, quantity=$quantity, totalCost=$totalCost, " +
            "avgPrice=$avgPrice, minPrice=$minPrice, maxPrice=$maxPrice, lastUpdated=$lastUpdated)"
}
