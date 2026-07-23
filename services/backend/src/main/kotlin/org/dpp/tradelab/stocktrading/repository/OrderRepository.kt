package org.dpp.tradelab.stocktrading.repository

import org.dpp.tradelab.stocktrading.model.Order
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface OrderRepository : JpaRepository<Order, UUID> {

    fun existsByIdempotencyKey(idempotencyKey: UUID): Boolean
}
