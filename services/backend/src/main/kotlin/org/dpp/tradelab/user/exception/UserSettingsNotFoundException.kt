package org.dpp.tradelab.user.exception

import java.util.UUID

class UserSettingsNotFoundException(userId: UUID) :
    RuntimeException("No settings found for user: $userId")
