package org.dpp.tradelab

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class TradingLabApplication

fun main(args: Array<String>) {
    runApplication<TradingLabApplication>(*args)
}
