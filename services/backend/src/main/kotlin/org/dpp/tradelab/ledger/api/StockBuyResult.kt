package org.dpp.tradelab.ledger.api

/**
 * Outcome of a stock-buy settlement performed by the Ledger domain.
 *
 * Returned by [LedgerApi.recordStockBuy] so the caller (Stock Trading) can map the result
 * to an order state without the Ledger domain leaking balances or locking concerns.
 */
enum class StockBuyResult {
    /** Funds were sufficient; the cash debit and stock credit were written and the balance debited. */
    COMPLETED,

    /** Funds were insufficient under the lock; nothing was written. */
    INSUFFICIENT_FUNDS
}
