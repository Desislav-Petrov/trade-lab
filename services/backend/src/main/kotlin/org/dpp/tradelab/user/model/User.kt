package org.dpp.tradelab.user.model

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
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for a registered user.
 *
 * Implements [Persistable] with a transient [_isNew] flag so that Spring Data
 * always calls EntityManager.persist() — never merge() — even though the UUID
 * is pre-assigned by the service layer before construction. Without this,
 * a non-null id causes SimpleJpaRepository.save() to call EntityManager.merge(),
 * which issues a stale UPDATE before the INSERT and throws StaleObjectStateException.
 *
 * [_isNew] is annotated [@Transient][Transient] so JPA ignores it, and the
 * property getter is renamed via [@get:JvmName] so that the explicit
 * [getId] override does not clash with the Kotlin-generated property accessor.
 *
 * [settings] is a non-persisted convenience field populated by the service layer
 * after loading the [User] — it carries the associated [UserSettings] for
 * in-process use without a JPA relationship.
 */
@Entity
@Table(name = "users")
class User(
    @Id
    @Column(nullable = false, updatable = false)
    @get:JvmName("getIdValue")
    val id: UUID,

    @Column(nullable = false)
    val firstName: String,

    @Column(nullable = false)
    val lastName: String,

    @Column(nullable = false)
    val address: String,

    @Column(nullable = false, unique = true)
    val email: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: UserStatus = UserStatus.ACTIVE,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @UpdateTimestamp
    @Column(nullable = false)
    val updatedAt: Instant = Instant.now(),

    @Transient
    private val _isNew: Boolean = true
) : Persistable<UUID> {

    @Transient
    var settings: UserSettings? = null

    override fun getId(): UUID = id

    override fun isNew(): Boolean = _isNew

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "User(id=$id, email=$email, status=$status)"
}
