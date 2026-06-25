package org.dpp.tradelab.marketdata.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

/**
 * JPA entity representing a single user's active subscription to a stock ticker.
 *
 * Implements [Persistable] with a transient [_isNew] flag so that Spring Data
 * always calls EntityManager.persist() — never merge() — even though the UUID
 * is pre-assigned by the service layer before construction.
 *
 * The primary-key property is named [subscriptionId] (not [id]) to avoid a JVM
 * platform declaration clash: Kotlin auto-generates a getId() getter for any
 * property named `id`, which collides with the Persistable.getId() override.
 *
 * Subscriptions are immutable after creation — there is no updatedAt field.
 */
@Entity
@Table(
    name = "asset_subscriptions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "ticker"])]
)
class AssetSubscription(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val subscriptionId: UUID,

    @Column(nullable = false, updatable = false)
    val userId: UUID,

    @Column(nullable = false, updatable = false)
    val ticker: String,

    @Column(nullable = false, updatable = false)
    val companyName: String,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @Transient
    private val _isNew: Boolean = true
) : Persistable<UUID> {

    override fun getId(): UUID = subscriptionId

    override fun isNew(): Boolean = _isNew

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AssetSubscription) return false
        return subscriptionId == other.subscriptionId
    }

    override fun hashCode(): Int = subscriptionId.hashCode()

    override fun toString(): String =
        "AssetSubscription(subscriptionId=$subscriptionId, userId=$userId, ticker=$ticker, companyName=$companyName, createdAt=$createdAt)"
}
