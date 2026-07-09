import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { PortfolioAccountSelector } from './PortfolioAccountSelector'
import type { AccountResponse } from '../../ledger/types/account'

const mockAccounts: AccountResponse[] = [
  {
    id: 'acc-1',
    name: 'My USD Account',
    currency: 'USD',
    balance: 1000,
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 'acc-2',
    name: 'My GBP Account',
    currency: 'GBP',
    balance: 500,
    status: 'ACTIVE',
    createdAt: '2026-01-02T00:00:00Z',
  },
]

describe('PortfolioAccountSelector', () => {
  it('PortfolioAccountSelector - accounts provided - renders account options with name and currency', () => {
    render(
      <PortfolioAccountSelector
        accounts={mockAccounts}
        selectedAccountId={null}
        onAccountChange={vi.fn()}
      />
    )

    expect(screen.getByRole('option', { name: 'My USD Account (USD)' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'My GBP Account (GBP)' })).toBeInTheDocument()
  })

  it('PortfolioAccountSelector - selectedAccountId set - shows correct selected option', () => {
    render(
      <PortfolioAccountSelector
        accounts={mockAccounts}
        selectedAccountId="acc-2"
        onAccountChange={vi.fn()}
      />
    )

    const select = screen.getByRole('combobox', { name: /select account/i })
    expect((select as HTMLSelectElement).value).toBe('acc-2')
  })

  it('PortfolioAccountSelector - user selects different account - calls onAccountChange with correct accountId', () => {
    const onAccountChange = vi.fn()
    render(
      <PortfolioAccountSelector
        accounts={mockAccounts}
        selectedAccountId="acc-1"
        onAccountChange={onAccountChange}
      />
    )

    const select = screen.getByRole('combobox', { name: /select account/i })
    fireEvent.change(select, { target: { value: 'acc-2' } })

    expect(onAccountChange).toHaveBeenCalledWith('acc-2')
  })

  it('PortfolioAccountSelector - accounts is empty - renders empty-state message', () => {
    render(
      <PortfolioAccountSelector
        accounts={[]}
        selectedAccountId={null}
        onAccountChange={vi.fn()}
      />
    )

    expect(
      screen.getByText('No accounts available. Open an account first.')
    ).toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  })

  it('PortfolioAccountSelector - accounts provided - preserves order from props', () => {
    render(
      <PortfolioAccountSelector
        accounts={mockAccounts}
        selectedAccountId={null}
        onAccountChange={vi.fn()}
      />
    )

    const options = screen.getAllByRole('option')
    // first option is the hidden disabled placeholder, then real accounts
    const visibleOptions = options.filter((opt) => !(opt as HTMLOptionElement).hidden)
    expect(visibleOptions[0]).toHaveTextContent('My USD Account (USD)')
    expect(visibleOptions[1]).toHaveTextContent('My GBP Account (GBP)')
  })
})
