package org.dpp.tradelab.agent.repository

import org.dpp.tradelab.agent.model.AgentEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface AgentEventRepository : JpaRepository<AgentEvent, UUID> {
    fun findAllByAppNameAndUserIdAndSessionIdOrderBySequenceAsc(
        appName: String,
        userId: String,
        sessionId: String
    ): List<AgentEvent>

    @Query(
        """
        select max(event.sequence) from AgentEvent event
        where event.appName = :appName
          and event.userId = :userId
          and event.sessionId = :sessionId
        """
    )
    fun findMaxSequence(
        @Param("appName") appName: String,
        @Param("userId") userId: String,
        @Param("sessionId") sessionId: String
    ): Long?

    fun deleteAllByAppNameAndUserIdAndSessionId(appName: String, userId: String, sessionId: String): Int
}
