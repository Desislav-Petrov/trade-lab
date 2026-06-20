package org.dpp.tradelab.user.exception

import java.util.UUID

class UserNotFoundException(userId: UUID) :
    RuntimeException("No user found with id: $userId")
