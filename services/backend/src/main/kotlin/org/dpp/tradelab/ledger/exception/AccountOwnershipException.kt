package org.dpp.tradelab.ledger.exception

import java.util.UUID

class AccountOwnershipException(accountId: UUID) :
    RuntimeException("Account $accountId does not belong to the requesting user")
