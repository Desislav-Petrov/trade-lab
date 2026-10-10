package org.dpp.tradelab.agent.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Transient
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "agent_sessions",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_agent_sessions_scope", columnNames = ["app_name", "user_id", "session_id"])
    ],
    indexes = [
        Index(name = "idx_agent_sessions_scope", columnList = "app_name, user_id, session_id")
    ]
)
class AgentSession(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @get:JvmName("getIdValue")
    val id: UUID,

    @Column(name = "app_name", nullable = false, updatable = false)
    val appName: String,

    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: String,

    @Column(name = "session_id", nullable = false, updatable = false)
    val sessionId: String,

    @Column(name = "state_json", nullable = false, columnDefinition = "text")
    var stateJson: String,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

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
        if (other !is AgentSession) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "AgentSession(id=$id)"
}
