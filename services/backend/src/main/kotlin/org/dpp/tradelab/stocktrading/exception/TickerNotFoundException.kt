package org.dpp.tradelab.stocktrading.exception

class TickerNotFoundException(val ticker: String) : RuntimeException(
    "Ticker '$ticker' is not in the supported list"
)
