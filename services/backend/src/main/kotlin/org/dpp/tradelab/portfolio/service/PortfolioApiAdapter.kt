package org.dpp.tradelab.portfolio.service

import org.dpp.tradelab.portfolio.api.PortfolioApi
import org.dpp.tradelab.portfolio.api.PortfolioHoldingsView
import org.dpp.tradelab.portfolio.mapper.PortfolioApiMapper
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class PortfolioApiAdapter(
    private val portfolioQueryService: PortfolioQueryService,
    private val portfolioApiMapper: PortfolioApiMapper
) : PortfolioApi {
    override fun getPositionQuantity(accountId: UUID, ticker: String): BigDecimal =
        portfolioQueryService.getPositionQuantity(accountId, ticker)

    override fun getHoldings(accountId: UUID, userId: UUID): PortfolioHoldingsView =
        portfolioApiMapper.toHoldingsView(portfolioQueryService.getHoldings(accountId, userId))
}
