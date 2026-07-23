package org.dpp.tradelab.stocktrading.exception

import java.util.UUID

class OrderAccountNotOwnedException(val accountId: UUID, val userId: UUID) : RuntimeException(
    "Account $accountId does not belong to user $userId"
)
