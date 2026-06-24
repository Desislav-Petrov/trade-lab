import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { TopUpModal } from './TopUpModal'
import type { AccountResponse } from '../types/account'

const mockAccount: AccountResponse = {
  accountId: 'acc-123',
  name: 'Test Account',
  currency: 'USD',
  balance: 1000,
  status: 'ACTIVE',
  createdAt: '2024-01-01T00:00:00Z',
}

function renderModal(props: Partial<React.ComponentProps<typeof TopUpModal>> = {}) {
  const defaults = {
    account: mockAccount,
    onConfirm: vi.fn(),
    onClose: vi.fn(),
    isLoading: false,
    isSuccess: false,
  }
  return render(<TopUpModal {...defaults} {...props} />)
}

describe('TopUpModal', () => {
  beforeEach(() => vi.clearAllMocks())

  it('TopUpModal - renders - shows account name and currency', () => {
    renderModal()
    expect(screen.getByText('Test Account')).toBeInTheDocument()
    expect(screen.getByText('USD')).toBeInTheDocument()
  })

  it('TopUpModal - decimal input "1.5" - shows inline error "Amount must be a whole number."', () => {
    renderModal()
    fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: '1.5' } })
    expect(screen.getByRole('alert')).toHaveTextContent('Amount must be a whole number.')
  })

  it('TopUpModal - input "0" - shows inline error "Amount must be at least 1."', () => {
    renderModal()
    fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: '0' } })
    expect(screen.getByRole('alert')).toHaveTextContent('Amount must be at least 1.')
  })

  it('TopUpModal - input "-5" - shows inline error "Amount must be at least 1."', () => {
    renderModal()
    fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: '-5' } })
    expect(screen.getByRole('alert')).toHaveTextContent('Amount must be at least 1.')
  })

  it('TopUpModal - input "10000001" - shows inline error "Amount must not exceed 10,000,000."', () => {
    renderModal()
    fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: '10000001' } })
    expect(screen.getByRole('alert')).toHaveTextContent('Amount must not exceed 10,000,000.')
  })

  it('TopUpModal - valid input "500" - calls onConfirm with 500 and no error shown', () => {
    const onConfirm = vi.fn()
    renderModal({ onConfirm })
    fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: '500' } })
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /confirm/i }))
    expect(onConfirm).toHaveBeenCalledWith(500)
  })

  it('TopUpModal - isLoading true - Confirm button is disabled', () => {
    renderModal({ isLoading: true })
    expect(screen.getByRole('button', { name: /confirming/i })).toBeDisabled()
  })

  it('TopUpModal - isSuccess true - shows "Top up successful" and hides the form', () => {
    renderModal({ isSuccess: true })
    expect(screen.getByText('Top up successful')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /confirm/i })).not.toBeInTheDocument()
  })

  it('TopUpModal - error prop set - renders error message', () => {
    renderModal({ error: 'Something went wrong' })
    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong')
  })

  it('TopUpModal - Cancel clicked - calls onClose', () => {
    const onClose = vi.fn()
    renderModal({ onClose })
    fireEvent.click(screen.getByRole('button', { name: /cancel/i }))
    expect(onClose).toHaveBeenCalledOnce()
  })
})
