import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TopUpModal } from './TopUpModal'
import type { AccountResponse } from '../types/account'

const mockAccount: AccountResponse = {
  id: 'acc-123',
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
  return {
    user: userEvent.setup(),
    ...render(<TopUpModal {...defaults} {...props} />),
    onConfirm: (props.onConfirm ?? defaults.onConfirm) as ReturnType<typeof vi.fn>,
    onClose: (props.onClose ?? defaults.onClose) as ReturnType<typeof vi.fn>,
  }
}

describe('TopUpModal', () => {
  beforeEach(() => vi.clearAllMocks())

  it('TopUpModal - renders - shows account name and currency', () => {
    renderModal()
    expect(screen.getByText('Test Account')).toBeInTheDocument()
    expect(screen.getByText('USD')).toBeInTheDocument()
  })

  it('TopUpModal - decimal input "1.5" - shows inline error "Amount must be a whole number."', async () => {
    const { user } = renderModal()
    await user.type(screen.getByLabelText(/amount/i), '1.5')
    expect(screen.getByRole('alert')).toHaveTextContent('Amount must be a whole number.')
  })

  it('TopUpModal - input "0" - shows inline error "Amount must be at least 1."', async () => {
    const { user } = renderModal()
    await user.type(screen.getByLabelText(/amount/i), '0')
    expect(screen.getByRole('alert')).toHaveTextContent('Amount must be at least 1.')
  })

  it('TopUpModal - input "-5" - shows inline error "Amount must be at least 1."', async () => {
    const { user } = renderModal()
    await user.type(screen.getByLabelText(/amount/i), '-5')
    expect(screen.getByRole('alert')).toHaveTextContent('Amount must be at least 1.')
  })

  it('TopUpModal - input "10000001" - shows inline error "Amount must not exceed 10,000,000."', async () => {
    const { user } = renderModal()
    await user.type(screen.getByLabelText(/amount/i), '10000001')
    expect(screen.getByRole('alert')).toHaveTextContent('Amount must not exceed 10,000,000.')
  })

  it('TopUpModal - valid input "500" - Confirm button enabled and calls onConfirm with 500', async () => {
    const onConfirm = vi.fn()
    const { user } = renderModal({ onConfirm })
    const input = screen.getByLabelText(/amount/i)
    const confirmBtn = screen.getByRole('button', { name: /confirm/i })

    await user.type(input, '500')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(confirmBtn).not.toBeDisabled()
    await user.click(confirmBtn)
    expect(onConfirm).toHaveBeenCalledWith(500)
  })

  it('TopUpModal - valid input "1000" - Confirm button enabled and calls onConfirm with 1000', async () => {
    const onConfirm = vi.fn()
    const { user } = renderModal({ onConfirm })
    const input = screen.getByLabelText(/amount/i)
    const confirmBtn = screen.getByRole('button', { name: /confirm/i })

    await user.type(input, '1000')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(confirmBtn).not.toBeDisabled()
    await user.click(confirmBtn)
    expect(onConfirm).toHaveBeenCalledWith(1000)
  })

  it('TopUpModal - Confirm button disabled when field is empty', () => {
    renderModal()
    expect(screen.getByRole('button', { name: /confirm/i })).toBeDisabled()
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

  it('TopUpModal - Cancel clicked - calls onClose', async () => {
    const onClose = vi.fn()
    const { user } = renderModal({ onClose })
    await user.click(screen.getByRole('button', { name: /cancel/i }))
    expect(onClose).toHaveBeenCalledOnce()
  })
})
