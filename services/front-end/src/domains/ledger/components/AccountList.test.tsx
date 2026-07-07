import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AccountList } from './AccountList'
import type { AccountResponse } from '../types/account'

const mockAccounts: AccountResponse[] = [
  {
    id: 'acc-1',
    name: 'Trading Account',
    currency: 'USD',
    balance: 1500.5,
    status: 'ACTIVE',
    createdAt: '2026-01-15T10:00:00Z',
  },
  {
    id: 'acc-2',
    name: 'Savings',
    currency: 'GBP',
    balance: 0,
    status: 'ACTIVE',
    createdAt: '2026-02-01T08:30:00Z',
  },
]

describe('AccountList', () => {
  it('AccountList - empty accounts - shows empty state message', () => {
    render(<AccountList accounts={[]} onTopUp={() => {}} onTransactions={() => {}} />)
    expect(screen.getByText(/no accounts yet/i)).toBeInTheDocument()
  })

  it('AccountList - with accounts - renders account names', () => {
    render(<AccountList accounts={mockAccounts} onTopUp={() => {}} onTransactions={() => {}} />)
    expect(screen.getByText('Trading Account')).toBeInTheDocument()
    expect(screen.getByText('Savings')).toBeInTheDocument()
  })

  it('AccountList - with accounts - renders currencies', () => {
    render(<AccountList accounts={mockAccounts} onTopUp={() => {}} onTransactions={() => {}} />)
    expect(screen.getByText('USD')).toBeInTheDocument()
    expect(screen.getByText('GBP')).toBeInTheDocument()
  })

  it('AccountList - with accounts - renders balance to 2 decimal places', () => {
    render(<AccountList accounts={mockAccounts} onTopUp={() => {}} onTransactions={() => {}} />)
    expect(screen.getByText('1500.50')).toBeInTheDocument()
    expect(screen.getByText('0.00')).toBeInTheDocument()
  })

  it('AccountList - with accounts - renders status', () => {
    render(<AccountList accounts={mockAccounts} onTopUp={() => {}} onTransactions={() => {}} />)
    const statusCells = screen.getAllByText('ACTIVE')
    expect(statusCells).toHaveLength(2)
  })

  it('AccountList - with accounts - renders createdAt as local date', () => {
    render(<AccountList accounts={mockAccounts} onTopUp={() => {}} onTransactions={() => {}} />)
    const date = new Date('2026-01-15T10:00:00Z').toLocaleDateString()
    expect(screen.getByText(date)).toBeInTheDocument()
  })

  it('AccountList - empty accounts - does not render list', () => {
    render(<AccountList accounts={[]} onTopUp={() => {}} onTransactions={() => {}} />)
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('AccountList - with accounts - renders a Top Up button for each account', () => {
    render(<AccountList accounts={mockAccounts} onTopUp={() => {}} onTransactions={() => {}} />)
    const topUpButtons = screen.getAllByRole('button', { name: /top up/i })
    expect(topUpButtons).toHaveLength(mockAccounts.length)
  })

  it('AccountList - with accounts - clicking Top Up calls onTopUp with the correct account', async () => {
    const user = userEvent.setup()
    const handleTopUp = vi.fn()
    render(<AccountList accounts={mockAccounts} onTopUp={handleTopUp} onTransactions={() => {}} />)

    const topUpButtons = screen.getAllByRole('button', { name: /top up/i })
    await user.click(topUpButtons[0])

    expect(handleTopUp).toHaveBeenCalledOnce()
    expect(handleTopUp).toHaveBeenCalledWith(mockAccounts[0])
    expect(handleTopUp).not.toHaveBeenCalledWith(mockAccounts[1])
  })

  it('AccountList - with accounts - renders a Transactions button for each account', () => {
    render(<AccountList accounts={mockAccounts} onTopUp={() => {}} onTransactions={() => {}} />)
    const txButtons = screen.getAllByRole('button', { name: /transactions/i })
    expect(txButtons).toHaveLength(mockAccounts.length)
  })

  it('AccountList - with accounts - clicking Transactions calls onTransactions with the correct account', async () => {
    const user = userEvent.setup()
    const handleTransactions = vi.fn()
    render(
      <AccountList accounts={mockAccounts} onTopUp={() => {}} onTransactions={handleTransactions} />,
    )

    const txButtons = screen.getAllByRole('button', { name: /transactions/i })
    await user.click(txButtons[0])

    expect(handleTransactions).toHaveBeenCalledOnce()
    expect(handleTransactions).toHaveBeenCalledWith(mockAccounts[0])
    expect(handleTransactions).not.toHaveBeenCalledWith(mockAccounts[1])
  })
})
