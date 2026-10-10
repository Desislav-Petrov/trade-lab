package org.dpp.tradelab.agent.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "agent_user_state",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_agent_user_state_scope", columnNames = ["app_name", "user_id"])
    ]
)
class AgentUserState(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @get:JvmName("getIdValue")
    val id: UUID,

    @Column(name = "app_name", nullable = false, updatable = false)
    val appName: String,

    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: String,

    @Column(name = "state_json", nullable = false, columnDefinition = "text")
    var stateJson: String,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,

    @Transient
    private val _isNew: Boolean = true
) : Persistable<UUID> {
    override fun getId(): UUID = id

    override fun isNew(): Boolean = _isNew

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AgentUserState) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "AgentUserState(id=$id)"
}
