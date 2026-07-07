import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { AccountSelector } from './AccountSelector'
import type { AccountResponse } from '../../ledger/types/account'

const mockAccounts: AccountResponse[] = [
  {
    id: 'acc-1',
    name: 'My USD Account',
    currency: 'USD',
    balance: 100,
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 'acc-2',
    name: 'My GBP Account',
    currency: 'GBP',
    balance: 200,
    status: 'ACTIVE',
    createdAt: '2026-01-02T00:00:00Z',
  },
]

describe('AccountSelector', () => {
  it('AccountSelector - isLoading true - renders loading indicator', () => {
    render(
      <AccountSelector
        accounts={[]}
        selectedAccountId={null}
        onSelect={vi.fn()}
        isLoading={true}
        isError={false}
      />,
    )
    expect(screen.getByText(/loading accounts/i)).toBeInTheDocument()
  })

  it('AccountSelector - isError true - renders role alert error message', () => {
    render(
      <AccountSelector
        accounts={[]}
        selectedAccountId={null}
        onSelect={vi.fn()}
        isLoading={false}
        isError={true}
      />,
    )
    const alert = screen.getByRole('alert')
    expect(alert).toBeInTheDocument()
    expect(alert.textContent).toBe('Could not load accounts.')
  })

  it('AccountSelector - empty accounts not loading or error - renders empty state message', () => {
    render(
      <AccountSelector
        accounts={[]}
        selectedAccountId={null}
        onSelect={vi.fn()}
        isLoading={false}
        isError={false}
      />,
    )
    expect(
      screen.getByText('No accounts available. Open an account first.'),
    ).toBeInTheDocument()
  })

  it('AccountSelector - accounts provided - renders select with one option per account', () => {
    render(
      <AccountSelector
        accounts={mockAccounts}
        selectedAccountId="acc-1"
        onSelect={vi.fn()}
        isLoading={false}
        isError={false}
      />,
    )
    expect(screen.getByRole('combobox')).toBeInTheDocument()
    expect(screen.getByText('My USD Account (USD)')).toBeInTheDocument()
    expect(screen.getByText('My GBP Account (GBP)')).toBeInTheDocument()
  })

  it('AccountSelector - accounts provided - select reflects selectedAccountId', () => {
    render(
      <AccountSelector
        accounts={mockAccounts}
        selectedAccountId="acc-2"
        onSelect={vi.fn()}
        isLoading={false}
        isError={false}
      />,
    )
    const select = screen.getByRole('combobox') as HTMLSelectElement
    expect(select.value).toBe('acc-2')
  })

  it('AccountSelector - select value null - select value is empty string', () => {
    render(
      <AccountSelector
        accounts={mockAccounts}
        selectedAccountId={null}
        onSelect={vi.fn()}
        isLoading={false}
        isError={false}
      />,
    )
    const select = screen.getByRole('combobox') as HTMLSelectElement
    expect(select.value).toBe('')
  })

  it('AccountSelector - user changes selection - calls onSelect with chosen account id', () => {
    const onSelect = vi.fn()
    render(
      <AccountSelector
        accounts={mockAccounts}
        selectedAccountId="acc-1"
        onSelect={onSelect}
        isLoading={false}
        isError={false}
      />,
    )
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'acc-2' } })
    expect(onSelect).toHaveBeenCalledWith('acc-2')
  })

  it('AccountSelector - isLoading and isError both false with accounts - does not render alert', () => {
    render(
      <AccountSelector
        accounts={mockAccounts}
        selectedAccountId="acc-1"
        onSelect={vi.fn()}
        isLoading={false}
        isError={false}
      />,
    )
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
