package org.dpp.tradelab.user.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

/**
 * Maps an external identity provider account to a platform User.
 * Created on first OIDC authentication with a provider.
 *
 * See decisions/2026-07-26-external-identity-provider-constraints.md for constraint rationale.
 */
@Entity
@Table(
    name = "external_identity_providers",
    uniqueConstraints = [
        jakarta.persistence.UniqueConstraint(columnNames = ["provider_type", "sub_id"]),
        jakarta.persistence.UniqueConstraint(columnNames = ["user_id", "provider_type"])
    ]
)
class ExternalIdentityProvider(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID,

    @Column(nullable = false, updatable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, name = "provider_type")
    val providerType: ProviderType,

    @Column(nullable = false, updatable = false, name = "sub_id")
    val subId: String,

    @Column(nullable = false)
    val email: String,

    @Column(nullable = false, name = "last_accessed_at")
    var lastAccessedAt: Instant,

    @Transient
    private val _isNew: Boolean = true
) : Persistable<UUID> {

    override fun getId(): UUID = id

    override fun isNew(): Boolean = _isNew

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExternalIdentityProvider) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "ExternalIdentityProvider(id=$id, userId=$userId, providerType=$providerType, subId=$subId)"
}
