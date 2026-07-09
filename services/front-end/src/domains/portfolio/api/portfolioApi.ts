import axiosInstance from '../../../shared/api/axiosInstance'
import type { PortfolioHoldingsResponse } from '../types/portfolio.types'

export const PORTFOLIO_HOLDINGS_KEY = 'portfolioHoldings'

export async function fetchPortfolioHoldings(
  accountId: string,
  userId: string
): Promise<PortfolioHoldingsResponse> {
  const response = await axiosInstance.get<PortfolioHoldingsResponse>('/v1/portfolio/holdings', {
    params: { accountId, userId },
  })
  return response.data
}
