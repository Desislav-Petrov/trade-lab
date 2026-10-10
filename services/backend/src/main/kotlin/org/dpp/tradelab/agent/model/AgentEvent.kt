package org.dpp.tradelab.agent.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "agent_events",
    indexes = [
        Index(name = "idx_agent_events_session_sequence", columnList = "app_name, user_id, session_id, sequence")
    ]
)
class AgentEvent(
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

    @Column(name = "event_json", nullable = false, columnDefinition = "text")
    val eventJson: String,

    @Column(name = "event_timestamp", nullable = false, updatable = false)
    val eventTimestamp: Instant,

    @Column(name = "sequence", nullable = false, updatable = false)
    val sequence: Long,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @Transient
    private val _isNew: Boolean = true
) : Persistable<UUID> {
    override fun getId(): UUID = id

    override fun isNew(): Boolean = _isNew

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AgentEvent) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "AgentEvent(id=$id)"
}
