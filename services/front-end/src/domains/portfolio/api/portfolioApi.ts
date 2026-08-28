import axiosInstance from '../../../shared/api/axiosInstance'
import type { FillHistoryResponse, PortfolioHoldingsResponse } from '../types/portfolio.types'

export const PORTFOLIO_HOLDINGS_KEY = 'portfolioHoldings'
export const FILL_HISTORY_QUERY_KEY = 'fillHistory'

export async function fetchPortfolioHoldings(
  accountId: string,
  userId: string,
): Promise<PortfolioHoldingsResponse> {
  const response = await axiosInstance.get<PortfolioHoldingsResponse>('/v1/portfolio/holdings', {
    params: { accountId, userId },
  })
  return response.data
}

export async function fetchFillHistory(
  accountId: string,
  page = 0,
  size = 100,
): Promise<FillHistoryResponse> {
  const response = await axiosInstance.get<FillHistoryResponse>('/v1/portfolio/fills', {
    params: { accountId, page, size },
  })
  return response.data
}
