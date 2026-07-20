import { describe, it, expect } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TransactionTable } from './TransactionTable'
import type { TransactionResponse } from '../types/transaction'

const cashTransaction: TransactionResponse = {
  id: 'tx-1',
  type: 'CREDIT',
  assetType: 'CASH',
  amount: 1000,
  currency: 'USD',
  ticker: null,
  shares: null,
  description: 'Initial deposit',
  createdAt: '2026-06-01T12:00:00Z',
}

const stockTransaction: TransactionResponse = {
  id: 'tx-2',
  type: 'DEBIT',
  assetType: 'STOCK_BUY',
  amount: 500.5,
  currency: 'USD',
  ticker: 'AAPL',
  shares: 2.5,
  description: 'Buy AAPL',
  createdAt: '2026-06-02T09:30:00Z',
}

describe('TransactionTable', () => {
  it('TransactionTable - cash transaction - renders direction and asset type', () => {
    render(<TransactionTable transactions={[cashTransaction]} isLoading={false} isError={false} />)
    expect(screen.getByText('CREDIT')).toBeInTheDocument()
    expect(screen.getByText('CASH')).toBeInTheDocument()
  })

  it('TransactionTable - cash transaction - renders value with currency', () => {
    render(<TransactionTable transactions={[cashTransaction]} isLoading={false} isError={false} />)
    expect(screen.getByText('1,000.00 USD')).toBeInTheDocument()
  })

  it('TransactionTable - cash transaction - ticker and shares cells are empty', () => {
    render(<TransactionTable transactions={[cashTransaction]} isLoading={false} isError={false} />)
    const rows = screen.getAllByRole('row')
    // rows[0] is the header; rows[1] is the data row
    const dataRow = rows[1]
    const cells = within(dataRow).getAllByRole('cell')
    // Ticker is column index 3, Shares is column index 4
    expect(cells[3].textContent).toBe('')
    expect(cells[4].textContent).toBe('')
  })

  it('TransactionTable - stock transaction - renders ticker and shares', () => {
    render(<TransactionTable transactions={[stockTransaction]} isLoading={false} isError={false} />)
    expect(screen.getByText('AAPL')).toBeInTheDocument()
    expect(screen.getByText('2.5')).toBeInTheDocument()
  })

  it('TransactionTable - stock transaction - renders STOCK_BUY asset type and DEBIT direction', () => {
    render(<TransactionTable transactions={[stockTransaction]} isLoading={false} isError={false} />)
    expect(screen.getByText('DEBIT')).toBeInTheDocument()
    expect(screen.getByText('STOCK_BUY')).toBeInTheDocument()
  })

  it('TransactionTable - empty transactions - shows empty state message', () => {
    render(<TransactionTable transactions={[]} isLoading={false} isError={false} />)
    expect(screen.getByText(/no transactions yet/i)).toBeInTheDocument()
  })

  it('TransactionTable - empty transactions - does not render table header', () => {
    render(<TransactionTable transactions={[]} isLoading={false} isError={false} />)
    expect(screen.queryByRole('columnheader')).not.toBeInTheDocument()
  })

  it('TransactionTable - loading state - renders loading indicator', () => {
    render(<TransactionTable transactions={[]} isLoading={true} isError={false} />)
    expect(screen.getByRole('status')).toBeInTheDocument()
  })

  it('TransactionTable - loading state - does not render table rows', () => {
    render(<TransactionTable transactions={[cashTransaction]} isLoading={true} isError={false} />)
    expect(screen.queryByRole('row')).not.toBeInTheDocument()
  })

  it('TransactionTable - error state - shows error banner', () => {
    render(<TransactionTable transactions={[]} isLoading={false} isError={true} />)
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText(/could not load transactions/i)).toBeInTheDocument()
  })

  it('TransactionTable - error state - does not render table rows', () => {
    render(<TransactionTable transactions={[cashTransaction]} isLoading={false} isError={true} />)
    expect(screen.queryByRole('row')).not.toBeInTheDocument()
  })

  it('TransactionTable - sort Date header - cycles ascending then descending then unsorted', async () => {
    const user = userEvent.setup()
    render(
      <TransactionTable
        transactions={[cashTransaction, stockTransaction]}
        isLoading={false}
        isError={false}
      />,
    )

    const dateHeader = screen.getByRole('columnheader', { name: /^date$/i })

    // Initial state: no sort indicator on Date
    expect(dateHeader.textContent).toBe('Date')

    // First click: ascending
    await user.click(dateHeader)
    expect(dateHeader.textContent).toBe('Date ↑')

    // Second click: descending
    await user.click(dateHeader)
    expect(dateHeader.textContent).toBe('Date ↓')

    // Third click: unsorted (back to none)
    await user.click(dateHeader)
    expect(dateHeader.textContent).toBe('Date')
  })

  it('TransactionTable - sort Value ascending - orders rows by amount', async () => {
    const user = userEvent.setup()
    render(
      <TransactionTable
        transactions={[stockTransaction, cashTransaction]}
        isLoading={false}
        isError={false}
      />,
    )

    const valueHeader = screen.getByRole('columnheader', { name: /^value$/i })
    await user.click(valueHeader)

    const rows = screen.getAllByRole('row')
    // rows[0] header; rows[1] first data row; rows[2] second data row
    expect(within(rows[1]).getByText('500.50 USD')).toBeInTheDocument()
    expect(within(rows[2]).getByText('1,000.00 USD')).toBeInTheDocument()
  })
})
