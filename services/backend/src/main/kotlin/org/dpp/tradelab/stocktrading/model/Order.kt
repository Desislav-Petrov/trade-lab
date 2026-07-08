package org.dpp.tradelab.stocktrading.model

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
 * JPA entity representing a stock order placed by a user.
 *
 * Implements [Persistable] with a transient [_isNew] flag so that Spring Data
 * always calls EntityManager.persist() — never merge() — even though the UUID
 * is pre-assigned by the service layer before construction.
 *
 * The primary-key property is named [orderId] (not [id]) to avoid a JVM
 * platform declaration clash: Kotlin auto-generates a getId() getter for any
 * property named `id`, which collides with the Persistable.getId() override.
 */
@Entity
@Table(name = "orders")
class Order(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val orderId: UUID,

    @Column(nullable = false, unique = true, updatable = false)
    val idempotencyKey: UUID,

    @Column(nullable = false, updatable = false)
    val accountId: UUID,

    @Column(nullable = false, updatable = false)
    val userId: UUID,

    @Column(nullable = false, updatable = false)
    val ticker: String,

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    val quantity: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    val orderType: OrderType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus,

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    val priceSnapshot: BigDecimal,

    @Column(precision = 19, scale = 4)
    var executionPrice: BigDecimal? = null,

    @Column
    var rejectionReason: String? = null,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(nullable = false)
    val updatedAt: Instant? = null,

    @Transient
    private val _isNew: Boolean = true
) : Persistable<UUID> {

    override fun getId(): UUID = orderId

    override fun isNew(): Boolean = _isNew

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Order) return false
        return orderId == other.orderId
    }

    override fun hashCode(): Int = orderId.hashCode()

    override fun toString(): String =
        "Order(orderId=$orderId, accountId=$accountId, userId=$userId, ticker=$ticker, " +
            "quantity=$quantity, orderType=$orderType, status=$status, " +
            "priceSnapshot=$priceSnapshot, executionPrice=$executionPrice, " +
            "rejectionReason=$rejectionReason, createdAt=$createdAt)"
}
