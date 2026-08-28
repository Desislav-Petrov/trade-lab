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
  currency: string
  portfolioPercent: number | null
}

export interface AssetClassBreakdown {
  stockPercent: number | null
  cashPercent: number | null
  totalPortfolioValue: number
}

export interface StockBreakdownEntry {
  ticker: string
  currentValue: number
  percentOfStockPortfolio: number | null
}

export interface UnrealisedPnLEntry {
  ticker: string
  unrealisedPnL: number
}

export interface PortfolioInsights {
  assetClassBreakdown: AssetClassBreakdown
  stockBreakdown: StockBreakdownEntry[]
  unrealisedPnLContribution: UnrealisedPnLEntry[]
}

export interface PortfolioHoldingsResponse {
  holdings: StockHolding[]
  cash: CashHolding
  insights?: PortfolioInsights
}

export type FillSide = 'BUY' | 'SELL'

export interface FillDataPoint {
  filledAt: string
  executionPrice: number
  quantity: number
  side: FillSide
}

export interface FillHistoryEntry {
  ticker: string
  dataPoints: FillDataPoint[]
}

export interface FillHistoryResponse {
  page: number
  size: number
  totalPages: number
  totalElements: number
  fills: FillHistoryEntry[]
}
