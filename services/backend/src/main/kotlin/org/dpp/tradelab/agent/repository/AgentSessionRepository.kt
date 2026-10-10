package org.dpp.tradelab.agent.repository

import jakarta.persistence.LockModeType
import org.dpp.tradelab.agent.model.AgentSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface AgentSessionRepository : JpaRepository<AgentSession, UUID> {
    fun findByAppNameAndUserIdAndSessionId(appName: String, userId: String, sessionId: String): AgentSession?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select session from AgentSession session
        where session.appName = :appName
          and session.userId = :userId
          and session.sessionId = :sessionId
        """
    )
    fun findByAppNameAndUserIdAndSessionIdForUpdate(
        @Param("appName") appName: String,
        @Param("userId") userId: String,
        @Param("sessionId") sessionId: String
    ): AgentSession?

    fun findAllByAppNameAndUserId(appName: String, userId: String): List<AgentSession>

    fun deleteByAppNameAndUserIdAndSessionId(appName: String, userId: String, sessionId: String): Int
}
