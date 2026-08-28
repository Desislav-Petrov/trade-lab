import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { FillHistoryChart } from './FillHistoryChart'
import type { FillHistoryEntry } from '../types/portfolio.types'
import { LOSS_COLOUR, PROFIT_COLOUR } from './chartColours'

vi.mock('recharts', () => {
  type LineProps = {
    'data-testid'?: string
    data?: unknown[]
    dot?: (props: { cx: number; cy: number; payload: unknown }) => React.ReactNode
  }
  return {
    ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
      <div data-testid="responsive-container">{children}</div>
    ),
    LineChart: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
    CartesianGrid: () => <div />,
    XAxis: () => <div />,
    YAxis: () => <div />,
    Line: (props: LineProps) => (
      <div data-testid={props['data-testid']}>
        <svg>
          {props.data?.map((payload, index) => (
            <g key={index}>{props.dot?.({ cx: 10, cy: 10 + index, payload })}</g>
          ))}
        </svg>
      </div>
    ),
  }
})

const fills: FillHistoryEntry[] = [
  {
    ticker: 'AAPL',
    dataPoints: [
      {
        filledAt: '2026-08-28T10:00:00Z',
        executionPrice: 150,
        quantity: 2,
        side: 'BUY',
      },
      {
        filledAt: '2026-08-28T11:00:00Z',
        executionPrice: 155,
        quantity: 1,
        side: 'SELL',
      },
    ],
  },
  {
    ticker: 'MSFT',
    dataPoints: [
      {
        filledAt: '2026-08-28T12:00:00Z',
        executionPrice: 250,
        quantity: 3,
        side: 'BUY',
      },
    ],
  },
]

describe('FillHistoryChart', () => {
  it('FillHistoryChart - fills empty - renders empty state', () => {
    render(<FillHistoryChart fills={[]} hiddenSymbols={new Set()} onToggleSymbol={vi.fn()} />)

    expect(screen.getByText('No trade history to display.')).toBeInTheDocument()
    expect(screen.queryByTestId('fill-history-chart')).not.toBeInTheDocument()
  })

  it('FillHistoryChart - visible fills - renders one line per ticker', () => {
    render(<FillHistoryChart fills={fills} hiddenSymbols={new Set()} onToggleSymbol={vi.fn()} />)

    expect(screen.getByTestId('fill-history-line-AAPL')).toBeInTheDocument()
    expect(screen.getByTestId('fill-history-line-MSFT')).toBeInTheDocument()
  })

  it('FillHistoryChart - buy and sell fills - renders distinct dot colours', () => {
    render(<FillHistoryChart fills={fills} hiddenSymbols={new Set()} onToggleSymbol={vi.fn()} />)

    expect(screen.getByTestId('fill-dot-AAPL-BUY')).toHaveAttribute('fill', PROFIT_COLOUR)
    expect(screen.getByTestId('fill-dot-AAPL-SELL')).toHaveAttribute('fill', LOSS_COLOUR)
  })

  it('FillHistoryChart - hidden symbol - does not render hidden line and dims legend', () => {
    render(
      <FillHistoryChart fills={fills} hiddenSymbols={new Set(['AAPL'])} onToggleSymbol={vi.fn()} />,
    )

    expect(screen.queryByTestId('fill-history-line-AAPL')).not.toBeInTheDocument()
    expect(screen.getByTestId('fill-history-line-MSFT')).toBeInTheDocument()
    expect(screen.getByTestId('fill-history-legend-AAPL')).toHaveClass('opacity-40')
  })

  it('FillHistoryChart - legend click - calls onToggleSymbol', async () => {
    const onToggleSymbol = vi.fn()
    const user = userEvent.setup()

    render(
      <FillHistoryChart fills={fills} hiddenSymbols={new Set()} onToggleSymbol={onToggleSymbol} />,
    )

    await user.click(screen.getByTestId('fill-history-legend-AAPL'))

    expect(onToggleSymbol).toHaveBeenCalledWith('AAPL')
  })
})
