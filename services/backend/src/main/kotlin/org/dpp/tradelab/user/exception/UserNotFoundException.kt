package org.dpp.tradelab.user.exception

import java.util.UUID

class UserNotFoundException : RuntimeException {
    constructor(userId: UUID) : super("No user found with id: $userId")
    constructor(email: String) : super("No user found with email: $email")
}
