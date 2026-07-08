package org.dpp.tradelab.stocktrading.exception

import java.util.UUID

class OrderAccountNotFoundException(val accountId: UUID) : RuntimeException(
    "Account not found: $accountId"
)
