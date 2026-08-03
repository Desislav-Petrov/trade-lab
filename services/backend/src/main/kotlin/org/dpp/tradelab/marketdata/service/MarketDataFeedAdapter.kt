package org.dpp.tradelab.marketdata.service

/**
 * Abstraction over a market data feed source.
 *
 * Implementations are self-contained Spring components that activate themselves
 * (e.g. via [@PostConstruct] and [@Scheduled]) and write ticks to the shared
 * [MarketDataFeedService] cache and dispatch pipeline.
 *
 * The interface acts as a marker / grouping contract so that the service layer
 * and tests can reference implementations by their concrete type without
 * depending on scheduling details.
 */
interface MarketDataFeedAdapter
