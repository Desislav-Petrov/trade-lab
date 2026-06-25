package org.dpp.tradelab.marketdata.service

import jakarta.annotation.PostConstruct
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * Loads the supported tickers from the classpath CSV file at application startup.
 *
 * The CSV file format is one entry per line: `TICKER,Company Name` — no header row.
 * Ticker keys are normalised to uppercase on load. The loaded map is immutable after
 * initialisation; no live reloading is supported (static configuration).
 */
@Component
class SupportedTickerConfig {

    private val tickerMap: MutableMap<String, String> = mutableMapOf()

    @PostConstruct
    fun init() {
        val resource = ClassPathResource("supported-tickers.csv")
        resource.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    val commaIndex = trimmed.indexOf(',')
                    if (commaIndex > 0) {
                        val ticker = trimmed.substring(0, commaIndex).trim().uppercase()
                        val companyName = trimmed.substring(commaIndex + 1).trim()
                        tickerMap[ticker] = companyName
                    }
                }
            }
        }
    }

    /**
     * Returns an immutable copy of the full ticker-to-companyName map.
     */
    fun getAll(): Map<String, String> = tickerMap.toMap()

    /**
     * Case-insensitive lookup by ticker symbol.
     *
     * @return the company name, or null if the ticker is not in the supported list.
     */
    fun resolve(ticker: String): String? = tickerMap[ticker.uppercase()]
}
