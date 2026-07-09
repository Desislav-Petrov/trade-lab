import { useQuery } from '@tanstack/react-query'
import { fetchPortfolioHoldings, PORTFOLIO_HOLDINGS_KEY } from '../api/portfolioApi'
import type { PortfolioHoldingsResponse } from '../types/portfolio.types'

export function usePortfolioHoldings(
  accountId: string | null,
  userId: string
): {
  data: PortfolioHoldingsResponse | undefined
  isLoading: boolean
  isError: boolean
  error: unknown
} {
  return useQuery({
    queryKey: [PORTFOLIO_HOLDINGS_KEY, accountId, userId],
    queryFn: () => fetchPortfolioHoldings(accountId!, userId),
    enabled: accountId !== null,
  })
}
