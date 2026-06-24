package org.dpp.tradelab.ledger.exception

import java.util.UUID

class AccountNotFoundException(accountId: UUID) :
    RuntimeException("No account found with id: $accountId")
