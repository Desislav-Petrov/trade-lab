package org.dpp.tradelab.stocktrading.exception

import java.util.UUID

class OrderAccountNotActiveException(val accountId: UUID) : RuntimeException(
    "Account $accountId is not active and cannot be used to place an order"
)
