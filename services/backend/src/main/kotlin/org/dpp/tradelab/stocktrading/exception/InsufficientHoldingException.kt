package org.dpp.tradelab.stocktrading.exception

import java.math.BigDecimal

class InsufficientHoldingException(
    val ticker: String,
    val requested: BigDecimal,
    val available: BigDecimal
) : RuntimeException(
    "Insufficient holding for $ticker: requested $requested, available $available"
)
