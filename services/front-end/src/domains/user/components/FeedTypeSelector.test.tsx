import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { FeedTypeSelector } from './FeedTypeSelector'

describe('FeedTypeSelector', () => {
  it('FeedTypeSelector - renders current value selected', () => {
    render(
      <FeedTypeSelector
        currentFeedType="SYNTHETIC"
        onFeedTypeChange={vi.fn()}
        isPending={false}
        isError={false}
      />,
    )
    const select = screen.getByRole('combobox')
    expect((select as HTMLSelectElement).value).toBe('SYNTHETIC')
  })

  it('FeedTypeSelector - renders both options with correct labels', () => {
    render(
      <FeedTypeSelector
        currentFeedType="REAL"
        onFeedTypeChange={vi.fn()}
        isPending={false}
        isError={false}
      />,
    )
    expect(screen.getByRole('option', { name: 'Synthetic Feed' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Real Market Feed' })).toBeInTheDocument()
  })

  it('FeedTypeSelector - calls onFeedTypeChange on selection', async () => {
    const onFeedTypeChange = vi.fn()
    render(
      <FeedTypeSelector
        currentFeedType="SYNTHETIC"
        onFeedTypeChange={onFeedTypeChange}
        isPending={false}
        isError={false}
      />,
    )
    const user = userEvent.setup()
    await user.selectOptions(screen.getByRole('combobox'), 'REAL')
    expect(onFeedTypeChange).toHaveBeenCalledWith('REAL')
  })

  it('FeedTypeSelector - is disabled when isPending is true', () => {
    render(
      <FeedTypeSelector
        currentFeedType="SYNTHETIC"
        onFeedTypeChange={vi.fn()}
        isPending={true}
        isError={false}
      />,
    )
    expect(screen.getByRole('combobox')).toBeDisabled()
  })

  it('FeedTypeSelector - shows loading indicator when isPending is true', () => {
    render(
      <FeedTypeSelector
        currentFeedType="SYNTHETIC"
        onFeedTypeChange={vi.fn()}
        isPending={true}
        isError={false}
      />,
    )
    expect(screen.getByLabelText('Saving')).toBeInTheDocument()
  })

  it('FeedTypeSelector - shows error message when isError is true', () => {
    render(
      <FeedTypeSelector
        currentFeedType="SYNTHETIC"
        onFeedTypeChange={vi.fn()}
        isPending={false}
        isError={true}
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('Failed to save. Please try again.')
  })

  it('FeedTypeSelector - does not show error message when isError is false', () => {
    render(
      <FeedTypeSelector
        currentFeedType="SYNTHETIC"
        onFeedTypeChange={vi.fn()}
        isPending={false}
        isError={false}
      />,
    )
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
