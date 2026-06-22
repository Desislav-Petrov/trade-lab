import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AccountList } from './AccountList'
import type { AccountResponse } from '../types/account'

const mockAccounts: AccountResponse[] = [
  {
    accountId: 'acc-1',
    name: 'Trading Account',
    currency: 'USD',
    balance: 1500.5,
    status: 'active',
    createdAt: '2026-01-15T10:00:00Z',
  },
  {
    accountId: 'acc-2',
    name: 'Savings',
    currency: 'GBP',
    balance: 0,
    status: 'active',
    createdAt: '2026-02-01T08:30:00Z',
  },
]

describe('AccountList', () => {
  it('AccountList - empty accounts - shows empty state message', () => {
    render(<AccountList accounts={[]} />)
    expect(screen.getByText(/no accounts yet/i)).toBeInTheDocument()
  })

  it('AccountList - with accounts - renders account names', () => {
    render(<AccountList accounts={mockAccounts} />)
    expect(screen.getByText('Trading Account')).toBeInTheDocument()
    expect(screen.getByText('Savings')).toBeInTheDocument()
  })

  it('AccountList - with accounts - renders currencies', () => {
    render(<AccountList accounts={mockAccounts} />)
    expect(screen.getByText('USD')).toBeInTheDocument()
    expect(screen.getByText('GBP')).toBeInTheDocument()
  })

  it('AccountList - with accounts - renders balance to 2 decimal places', () => {
    render(<AccountList accounts={mockAccounts} />)
    expect(screen.getByText('1500.50')).toBeInTheDocument()
    expect(screen.getByText('0.00')).toBeInTheDocument()
  })

  it('AccountList - with accounts - renders status', () => {
    render(<AccountList accounts={mockAccounts} />)
    const statusCells = screen.getAllByText('active')
    expect(statusCells).toHaveLength(2)
  })

  it('AccountList - with accounts - renders createdAt as local date', () => {
    render(<AccountList accounts={mockAccounts} />)
    const date = new Date('2026-01-15T10:00:00Z').toLocaleDateString()
    expect(screen.getByText(date)).toBeInTheDocument()
  })

  it('AccountList - empty accounts - does not render list', () => {
    render(<AccountList accounts={[]} />)
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })
})
