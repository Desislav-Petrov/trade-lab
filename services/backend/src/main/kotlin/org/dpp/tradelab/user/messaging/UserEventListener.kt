package org.dpp.tradelab.user.messaging

import org.springframework.stereotype.Component

/**
 * Listener for intra-domain User events.
 *
 * This class is a shell — the User domain does not currently consume its own
 * [UserSettingsChangedEvent] internally. It exists to satisfy the mandatory
 * listener class pattern (see standards/backend.md) and is ready for any
 * future intra-domain event consumption.
 */
@Component("userDomainEventListener")
class UserEventListener
