export interface TransactionResponse {
  id: string
  type: 'CREDIT' | 'DEBIT'
  assetType: 'CASH' | 'STOCK_BUY' | 'STOCK_SELL'
  amount: number
  currency: string
  ticker: string | null
  shares: number | null
  description: string | null
  createdAt: string
}

export interface TransactionListResponse {
  transactions: TransactionResponse[]
  page: number
  totalPages: number
  totalCount: number
}
