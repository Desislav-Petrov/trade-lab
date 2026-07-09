import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PortfolioAccountSelector } from './PortfolioAccountSelector'
import type { AccountResponse } from '../../ledger/types/account'

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
    name: 'Savings Account',
    currency: 'GBP',
    balance: 2000.0,
    status: 'ACTIVE',
    createdAt: '2026-02-01T08:30:00Z',
  },
  {
    id: 'acc-3',
    name: 'Investment Account',
    currency: 'EUR',
    balance: 3000.0,
    status: 'ACTIVE',
    createdAt: '2026-03-10T12:00:00Z',
  },
]

describe('PortfolioAccountSelector', () => {
  it('PortfolioAccountSelector - empty accounts - renders empty-state message', () => {
    render(
      <PortfolioAccountSelector
        accounts={[]}
        selectedAccountId={null}
        onAccountChange={() => {}}
      />,
    )
    expect(screen.getByText('No accounts available. Open an account first.')).toBeInTheDocument()
  })

  it('PortfolioAccountSelector - empty accounts - does not render dropdown', () => {
    render(
      <PortfolioAccountSelector
        accounts={[]}
        selectedAccountId={null}
        onAccountChange={() => {}}
      />,
    )
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  })

  it('PortfolioAccountSelector - with accounts - renders dropdown', () => {
    render(
      <PortfolioAccountSelector
        accounts={mockAccounts}
        selectedAccountId="acc-1"
        onAccountChange={() => {}}
      />,
    )
    expect(screen.getByRole('combobox', { name: /select account/i })).toBeInTheDocument()
  })

  it('PortfolioAccountSelector - with accounts - renders all account options with name and currency', () => {
    render(
      <PortfolioAccountSelector
        accounts={mockAccounts}
        selectedAccountId="acc-1"
        onAccountChange={() => {}}
      />,
    )
    expect(screen.getByRole('option', { name: 'Trading Account (USD)' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Savings Account (GBP)' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Investment Account (EUR)' })).toBeInTheDocument()
  })

  it('PortfolioAccountSelector - with accounts - renders correct number of options', () => {
    render(
      <PortfolioAccountSelector
        accounts={mockAccounts}
        selectedAccountId="acc-1"
        onAccountChange={() => {}}
      />,
    )
    const options = screen.getAllByRole('option')
    expect(options).toHaveLength(3)
  })

  it('PortfolioAccountSelector - with accounts - selected account is active option', () => {
    render(
      <PortfolioAccountSelector
        accounts={mockAccounts}
        selectedAccountId="acc-2"
        onAccountChange={() => {}}
      />,
    )
    const select = screen.getByRole('combobox') as HTMLSelectElement
    expect(select.value).toBe('acc-2')
  })

  it('PortfolioAccountSelector - with accounts - null selectedAccountId defaults to first account', () => {
    render(
      <PortfolioAccountSelector
        accounts={mockAccounts}
        selectedAccountId={null}
        onAccountChange={() => {}}
      />,
    )
    const select = screen.getByRole('combobox') as HTMLSelectElement
    // Browser automatically selects the first option when value is empty string with no matching option
    expect(select.value).toBe('acc-1')
  })

  it('PortfolioAccountSelector - with accounts - changing selection calls onAccountChange with correct accountId', async () => {
    const user = userEvent.setup()
    const handleAccountChange = vi.fn()
    render(
      <PortfolioAccountSelector
        accounts={mockAccounts}
        selectedAccountId="acc-1"
        onAccountChange={handleAccountChange}
      />,
    )

    const select = screen.getByRole('combobox')
    await user.selectOptions(select, 'acc-2')

    expect(handleAccountChange).toHaveBeenCalledOnce()
    expect(handleAccountChange).toHaveBeenCalledWith('acc-2')
  })

  it('PortfolioAccountSelector - with accounts - changing to different account calls onAccountChange', async () => {
    const user = userEvent.setup()
    const handleAccountChange = vi.fn()
    render(
      <PortfolioAccountSelector
        accounts={mockAccounts}
        selectedAccountId="acc-1"
        onAccountChange={handleAccountChange}
      />,
    )

    const select = screen.getByRole('combobox')
    await user.selectOptions(select, 'acc-3')

    expect(handleAccountChange).toHaveBeenCalledOnce()
    expect(handleAccountChange).toHaveBeenCalledWith('acc-3')
  })

  it('PortfolioAccountSelector - with accounts - preserves account order from API response', () => {
    render(
      <PortfolioAccountSelector
        accounts={mockAccounts}
        selectedAccountId="acc-1"
        onAccountChange={() => {}}
      />,
    )
    const options = screen.getAllByRole('option')
    expect(options[0]).toHaveTextContent('Trading Account (USD)')
    expect(options[1]).toHaveTextContent('Savings Account (GBP)')
    expect(options[2]).toHaveTextContent('Investment Account (EUR)')
  })
})
