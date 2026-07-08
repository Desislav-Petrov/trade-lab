package org.dpp.tradelab.stocktrading.exception

import java.util.UUID

class DuplicateIdempotencyKeyException(val idempotencyKey: UUID) : RuntimeException(
    "An order with idempotency key $idempotencyKey has already been submitted"
)
