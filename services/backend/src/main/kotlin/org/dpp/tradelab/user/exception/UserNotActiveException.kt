package org.dpp.tradelab.user.exception

class UserNotActiveException(email: String) :
    RuntimeException("This account ($email) is suspended or closed and cannot be used to log in.")
