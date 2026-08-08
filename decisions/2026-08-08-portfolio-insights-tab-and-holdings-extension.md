# Decision: Portfolio Insights Tab and Holdings Endpoint Extension

**Date:** 2026-08-08  
**Status:** accepted

## Context

Issue #109 introduces an Insights tab to the Portfolio page with three charts:
1. Asset class breakdown (Stock vs. Cash as % of total portfolio value) — pie chart.
2. Stock holdings breakdown (each stock as % of total stock-only value) — pie chart.
3. Unrealised P&L contribution per stock (positive and negative) — diverging bar chart.

Two structural decisions were required:

**Decision 1 — Page navigation structure.**  
The Portfolio page previously had no sub-navigation. Adding Insights requires a tab structure: Holdings (existing content), Insights (new default landing tab), and Advanced Insights (placeholder, empty). A page-level account selector is introduced above the tabs; it is shared by all tabs. This changes the existing `view-portfolio` flow materially.

**Decision 2 — New endpoint vs. extending the existing holdings endpoint.**  
The existing `GET /api/v1/portfolio/holdings?accountId={accountId}` already computes `currentValue`, `unrealisedPnL`, and `portfolioPercent` per stock. All chart data is derivable from these values with minor additional server-side aggregations (total stock value for Chart 2 denominator; raw unrealised P&L per stock for Chart 3). Introducing a separate Insights endpoint would duplicate the holdings fetch on every page load and add unnecessary API surface.

## Decision

1. The Portfolio page (`/portfolio`) gains a tab bar: **Holdings** | **Insights** | **Advanced Insights**. The page-level account selector sits above the tab bar and its selection applies to all tabs. Insights is the default tab. Advanced Insights is empty and out of scope for this iteration.

2. The existing `GET /api/v1/portfolio/holdings` response is extended — not replaced — with an `insights` aggregate object. The Portfolio backend computes and returns this alongside the existing `holdings` array and `cash` object. The frontend does not perform any chart calculations — it renders the values returned by the backend verbatim.

3. Chart 2 (stock breakdown) denominates against **total stock value only** (excluding cash), not total portfolio value. This is a deliberate product decision confirmed during issue intake.

4. Chart 3 is a **diverging bar chart** (not a pie chart) to accommodate negative unrealised P&L values. This was confirmed during issue intake.

## Consequences

- `domain/flows/view-portfolio.md` must be updated to describe the tab structure, shared account selector, and the extended holdings response shape.
- `domain/flows/view-portfolio-insights.md` is created to describe the Insights tab flows.
- `domain/usecases/view-portfolio.md` must be updated to include the Insights tab in the happy path and reference the new flow.
- The OpenAPI contract for `GET /api/v1/portfolio/holdings` must be extended with an `insights` field when the backend task is decomposed.
- Advanced Insights tab content is explicitly out of scope for this iteration.
