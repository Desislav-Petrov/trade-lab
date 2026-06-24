package org.dpp.tradelab.ledger.exception

import java.util.UUID

class AccountNotActiveException(accountId: UUID) :
    RuntimeException("Account $accountId is not available for this operation")
