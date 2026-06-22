import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { OpenAccountForm } from './OpenAccountForm'

function renderForm(props: Partial<React.ComponentProps<typeof OpenAccountForm>> = {}) {
  const defaults = {
    onSubmit: vi.fn(),
    isLoading: false,
    onCancel: vi.fn(),
  }
  return render(<OpenAccountForm {...defaults} {...props} />)
}

describe('OpenAccountForm', () => {
  beforeEach(() => vi.clearAllMocks())

  it('OpenAccountForm - renders - shows currency selector', () => {
    renderForm()
    expect(screen.getByRole('combobox', { name: /base currency/i })).toBeInTheDocument()
  })

  it('OpenAccountForm - renders - shows name input', () => {
    renderForm()
    expect(screen.getByRole('textbox', { name: /account name/i })).toBeInTheDocument()
  })

  it('OpenAccountForm - renders - shows submit and cancel buttons', () => {
    renderForm()
    expect(screen.getByRole('button', { name: /open account/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument()
  })

  it('OpenAccountForm - no currency selected - shows validation error and does not call onSubmit', () => {
    const onSubmit = vi.fn()
    renderForm({ onSubmit })

    fireEvent.click(screen.getByRole('button', { name: /open account/i }))

    expect(screen.getByText(/please select a base currency/i)).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('OpenAccountForm - currency selected - calls onSubmit with currency', () => {
    const onSubmit = vi.fn()
    renderForm({ onSubmit })

    fireEvent.change(screen.getByRole('combobox', { name: /base currency/i }), {
      target: { value: 'USD' },
    })
    fireEvent.click(screen.getByRole('button', { name: /open account/i }))

    expect(onSubmit).toHaveBeenCalledWith('USD', undefined)
  })

  it('OpenAccountForm - currency and name provided - calls onSubmit with both', () => {
    const onSubmit = vi.fn()
    renderForm({ onSubmit })

    fireEvent.change(screen.getByRole('combobox', { name: /base currency/i }), {
      target: { value: 'GBP' },
    })
    fireEvent.change(screen.getByRole('textbox', { name: /account name/i }), {
      target: { value: 'My Trading Account' },
    })
    fireEvent.click(screen.getByRole('button', { name: /open account/i }))

    expect(onSubmit).toHaveBeenCalledWith('GBP', 'My Trading Account')
  })

  it('OpenAccountForm - isLoading true - submit button is disabled and shows loading text', () => {
    renderForm({ isLoading: true })
    expect(screen.getByRole('button', { name: /opening/i })).toBeDisabled()
  })

  it('OpenAccountForm - error prop set - renders form-level error message', () => {
    renderForm({ error: 'Something went wrong.' })
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText('Something went wrong.')).toBeInTheDocument()
  })

  it('OpenAccountForm - cancel clicked - calls onCancel', () => {
    const onCancel = vi.fn()
    renderForm({ onCancel })

    fireEvent.click(screen.getByRole('button', { name: /cancel/i }))

    expect(onCancel).toHaveBeenCalledOnce()
  })
})
