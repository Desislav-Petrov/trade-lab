package org.dpp.tradelab.user.exception

class InvalidFeedTypeException(value: String) :
    RuntimeException("Invalid feed type: $value")
