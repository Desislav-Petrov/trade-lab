package org.dpp.tradelab.stocktrading.exception

import java.math.BigDecimal
import java.util.UUID

class InsufficientFundsException(
    val accountId: UUID,
    val required: BigDecimal,
    val available: BigDecimal
) : RuntimeException(
    "Insufficient funds on account $accountId: required $required, available $available"
)
