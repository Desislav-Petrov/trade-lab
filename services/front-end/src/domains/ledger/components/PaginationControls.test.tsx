import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PaginationControls } from './PaginationControls'

describe('PaginationControls', () => {
  it('PaginationControls - first page - Previous button is disabled', () => {
    render(<PaginationControls currentPage={0} totalPages={5} onPageChange={() => {}} />)
    expect(screen.getByRole('button', { name: /previous/i })).toBeDisabled()
  })

  it('PaginationControls - first page - Next button is enabled', () => {
    render(<PaginationControls currentPage={0} totalPages={5} onPageChange={() => {}} />)
    expect(screen.getByRole('button', { name: /next/i })).toBeEnabled()
  })

  it('PaginationControls - last page - Next button is disabled', () => {
    render(<PaginationControls currentPage={4} totalPages={5} onPageChange={() => {}} />)
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled()
  })

  it('PaginationControls - last page - Previous button is enabled', () => {
    render(<PaginationControls currentPage={4} totalPages={5} onPageChange={() => {}} />)
    expect(screen.getByRole('button', { name: /previous/i })).toBeEnabled()
  })

  it('PaginationControls - middle page - both buttons are enabled', () => {
    render(<PaginationControls currentPage={2} totalPages={5} onPageChange={() => {}} />)
    expect(screen.getByRole('button', { name: /previous/i })).toBeEnabled()
    expect(screen.getByRole('button', { name: /next/i })).toBeEnabled()
  })

  it('PaginationControls - zero total pages - renders nothing', () => {
    const { container } = render(
      <PaginationControls currentPage={0} totalPages={0} onPageChange={() => {}} />,
    )
    expect(container.firstChild).toBeNull()
  })

  it('PaginationControls - any page - displays 1-indexed page label', () => {
    render(<PaginationControls currentPage={1} totalPages={5} onPageChange={() => {}} />)
    expect(screen.getByText('Page 2 of 5')).toBeInTheDocument()
  })

  it('PaginationControls - Previous click - calls onPageChange with currentPage minus 1', async () => {
    const user = userEvent.setup()
    const handlePageChange = vi.fn()
    render(<PaginationControls currentPage={2} totalPages={5} onPageChange={handlePageChange} />)
    await user.click(screen.getByRole('button', { name: /previous/i }))
    expect(handlePageChange).toHaveBeenCalledOnce()
    expect(handlePageChange).toHaveBeenCalledWith(1)
  })

  it('PaginationControls - Next click - calls onPageChange with currentPage plus 1', async () => {
    const user = userEvent.setup()
    const handlePageChange = vi.fn()
    render(<PaginationControls currentPage={2} totalPages={5} onPageChange={handlePageChange} />)
    await user.click(screen.getByRole('button', { name: /next/i }))
    expect(handlePageChange).toHaveBeenCalledOnce()
    expect(handlePageChange).toHaveBeenCalledWith(3)
  })
})
