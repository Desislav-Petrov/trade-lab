package org.dpp.tradelab.marketdata.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

/**
 * Provides the bounded worker pool used to fan out WebSocket price ticks.
 *
 * The market-data tick loop is driven by a single scheduler thread. Performing
 * the blocking `WebSocketSession.sendMessage` I/O inline on that thread means a
 * single slow/backpressured client stalls delivery to every other subscriber
 * and delays the next tick. Offloading the sends to this dedicated pool keeps
 * the tick thread free.
 *
 * See `decisions/2026-09-10-websocket-tick-dispatch-async.md`.
 */
@Configuration
class MarketDataDispatchConfig {

    @Bean("marketDataTickDispatchExecutor")
    fun marketDataTickDispatchExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 4
        executor.queueCapacity = 1000
        executor.setThreadNamePrefix("md-tick-dispatch-")
        // Under sustained backpressure, drop the oldest queued tick rather than
        // blocking the tick thread — a stale price tick has no value.
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.DiscardOldestPolicy())
        executor.initialize()
        return executor
    }
}
