package org.dpp.tradelab.user.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for per-user platform settings.
 *
 * Implements [Persistable] with a transient [_isNew] flag so that Spring Data
 * always calls EntityManager.persist() — never merge() — even though the UUID
 * is pre-assigned by the service layer before construction.
 *
 * [userId] stores the UUID of the owning [User] as a plain FK column.
 * A unique constraint on [userId] enforces the one-settings-per-user rule.
 */
@Entity
@Table(name = "user_settings")
class UserSettings(
    @Id
    @Column(nullable = false, updatable = false)
    @get:JvmName("getIdValue")
    val id: UUID,

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var feedType: FeedType = FeedType.SYNTHETIC,

    @UpdateTimestamp
    @Column(nullable = false)
    var updatedAt: Instant? = null,

    @Transient
    private val _isNew: Boolean = true
) : Persistable<UUID> {

    override fun getId(): UUID = id

    override fun isNew(): Boolean = _isNew

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserSettings) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "UserSettings(id=$id, userId=$userId, feedType=$feedType, updatedAt=$updatedAt)"
}
