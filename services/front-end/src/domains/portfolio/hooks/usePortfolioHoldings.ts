import { useQuery } from '@tanstack/react-query'
import { fetchPortfolioHoldings, PORTFOLIO_HOLDINGS_KEY } from '../api/portfolioApi'
import type { PortfolioHoldingsResponse, PortfolioInsights } from '../types/portfolio.types'

export function usePortfolioHoldings(accountId: string | null, userId: string) {
  const query = useQuery<PortfolioHoldingsResponse, Error>({
    queryKey: [PORTFOLIO_HOLDINGS_KEY, accountId, userId],
    queryFn: () => fetchPortfolioHoldings(accountId!, userId),
    enabled: accountId !== null,
    staleTime: 0,
  })

  const insights: PortfolioInsights | undefined = query.data?.insights
  const holdings = query.data?.holdings ?? []
  const cash = query.data?.cash

  return { ...query, holdings, cash, insights }
}
