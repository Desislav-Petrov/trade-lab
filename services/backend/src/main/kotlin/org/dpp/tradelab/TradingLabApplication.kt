package org.dpp.tradelab

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TradingLabApplication

fun main(args: Array<String>) {
    runApplication<TradingLabApplication>(*args)
}
