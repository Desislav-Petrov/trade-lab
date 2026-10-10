package org.dpp.tradelab.agent.repository

import jakarta.persistence.LockModeType
import org.dpp.tradelab.agent.model.AgentUserState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface AgentUserStateRepository : JpaRepository<AgentUserState, UUID> {
    fun findByAppNameAndUserId(appName: String, userId: String): AgentUserState?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select state from AgentUserState state
        where state.appName = :appName
          and state.userId = :userId
        """
    )
    fun findByAppNameAndUserIdForUpdate(
        @Param("appName") appName: String,
        @Param("userId") userId: String
    ): AgentUserState?
}
