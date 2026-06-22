package org.dpp.tradelab.ledger.exception

class InvalidCurrencyException(currency: String) :
    RuntimeException("Invalid currency: $currency")
