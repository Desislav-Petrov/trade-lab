package org.dpp.tradelab.agent.exception

class SessionPersistenceException(
    message: String = "Agent session persistence failed.",
    cause: Throwable? = null
) : RuntimeException(message, cause)
