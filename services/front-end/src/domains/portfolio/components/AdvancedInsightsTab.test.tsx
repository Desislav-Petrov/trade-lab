import { describe, it, expect, vi, beforeEach } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import { createElement } from 'react'
import { AdvancedInsightsTab } from './AdvancedInsightsTab'
import { usePortfolioStore } from '../hooks/usePortfolioStore'
import type { FillHistoryEntry } from '../types/portfolio.types'

vi.mock('../hooks/useFillHistory', () => ({
  useFillHistory: vi.fn(),
}))

vi.mock('./FillHistoryChart', () => ({
  FillHistoryChart: ({
    fills,
    hiddenSymbols,
    onToggleSymbol,
  }: {
    fills: FillHistoryEntry[]
    hiddenSymbols: Set<string>
    onToggleSymbol: (ticker: string) => void
  }) =>
    createElement(
      'button',
      {
        'data-testid': 'fill-history-chart',
        onClick: () => onToggleSymbol('AAPL'),
      },
      `fills=${fills.length}, hidden=${hiddenSymbols.size}`,
    ),
}))

import { useFillHistory } from '../hooks/useFillHistory'
const mockUseFillHistory = vi.mocked(useFillHistory)

function buildFillHistoryReturn(overrides: Partial<ReturnType<typeof useFillHistory>> = {}) {
  return {
    fills: [],
    isLoading: false,
    isError: false,
    ...overrides,
  }
}

describe('AdvancedInsightsTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    usePortfolioStore.setState({ hiddenSymbols: new Set() })
  })

  it('AdvancedInsightsTab - loading - renders skeleton', () => {
    mockUseFillHistory.mockReturnValue(buildFillHistoryReturn({ isLoading: true }))

    render(<AdvancedInsightsTab accountId="acc-1" />)

    expect(screen.getByTestId('advanced-insights-loading')).toBeInTheDocument()
  })

  it('AdvancedInsightsTab - error - renders alert message', () => {
    mockUseFillHistory.mockReturnValue(buildFillHistoryReturn({ isError: true }))

    render(<AdvancedInsightsTab accountId="acc-1" />)

    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText('Could not load price history. Please try again.')).toBeInTheDocument()
  })

  it('AdvancedInsightsTab - success - renders FillHistoryChart', () => {
    mockUseFillHistory.mockReturnValue(
      buildFillHistoryReturn({
        fills: [{ ticker: 'AAPL', dataPoints: [] }],
      }),
    )

    render(<AdvancedInsightsTab accountId="acc-1" />)

    expect(screen.getByTestId('fill-history-chart')).toHaveTextContent('fills=1, hidden=0')
  })

  it('AdvancedInsightsTab - accountId change - resets symbol visibility', () => {
    mockUseFillHistory.mockReturnValue(buildFillHistoryReturn())
    const resetSymbolVisibility = vi.spyOn(usePortfolioStore.getState(), 'resetSymbolVisibility')

    const { rerender } = render(<AdvancedInsightsTab accountId="acc-1" />)
    act(() => {
      usePortfolioStore.setState({ hiddenSymbols: new Set(['AAPL']) })
    })
    act(() => {
      rerender(<AdvancedInsightsTab accountId="acc-2" />)
    })

    expect(resetSymbolVisibility).toHaveBeenCalled()
    expect(usePortfolioStore.getState().hiddenSymbols).toEqual(new Set())
  })
})
