package org.dpp.tradelab.ledger.exception

import java.util.UUID

class UserNotFoundException(userId: UUID) :
    RuntimeException("No user found with id: $userId")
