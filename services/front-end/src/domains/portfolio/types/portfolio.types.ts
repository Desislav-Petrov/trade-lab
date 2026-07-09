export interface PortfolioHoldingsResponse {
  holdings: StockHolding[]
  cash: CashHolding
}

export interface StockHolding {
  ticker: string
  quantity: number
  currentPrice: number
  currentValue: number
  minPrice: number
  maxPrice: number
  avgPrice: number
  portfolioPercent: number | null
  unrealisedPnL: number
}

export interface CashHolding {
  balance: number
  currency: 'USD' | 'GBP' | 'EUR'
  portfolioPercent: number | null
}
