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

/**
 * JPA entity representing an aggregated stock holding for a single ticker symbol
 * within one account.
 *
 * A Position row is created the first time a stock buy order is filled for a given
 * (userId, accountId, ticker) combination, and updated on every subsequent fill for
 * that combination. Cash is not stored in this table — cash balance is managed by
 * the Ledger domain.
 *
 * Implements [Persistable] with a transient [_isNew] flag so that Spring Data
 * always calls EntityManager.persist() — never merge() — even though the UUID
 * is pre-assigned by the service layer before construction.
 *
 * The primary-key property is named [positionId] (not [id]) to avoid a JVM
 * platform declaration clash: Kotlin auto-generates a getId() getter for any
 * property named `id`, which collides with the Persistable.getId() override.
 *
 * The unique constraint on (userId, accountId, ticker) ensures at most one active
 * Position row exists per combination.
 */
@Entity
@Table(
    name = "positions",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_position_user_account_ticker",
            columnNames = ["user_id", "account_id", "ticker"]
        )
    ]
)
class Position(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val positionId: UUID,

    @Column(nullable = false, updatable = false)
    val userId: UUID,

    @Column(nullable = false, updatable = false)
    val accountId: UUID,

    @Column(nullable = false, updatable = false, length = 10)
    val ticker: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    val assetType: AssetType,

    @Column(nullable = false, precision = 19, scale = 4)
    var quantity: BigDecimal,

    @Column(nullable = false, precision = 19, scale = 4)
    var totalCost: BigDecimal,

    @Column(nullable = false, precision = 19, scale = 4)
    var avgPrice: BigDecimal,

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
        "Position(id=$positionId, userId=$userId, accountId=$accountId, ticker='$ticker', " +
                "assetType=$assetType, quantity=$quantity, totalCost=$totalCost, " +
                "avgPrice=$avgPrice, minPrice=$minPrice, maxPrice=$maxPrice, lastUpdated=$lastUpdated)"
}
