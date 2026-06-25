import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RemoveTickerBar } from './RemoveTickerBar'

function renderBar(props: Partial<React.ComponentProps<typeof RemoveTickerBar>> = {}) {
  const defaults = {
    selectedCount: 0,
    onRemove: vi.fn(),
    isLoading: false,
  }
  return render(<RemoveTickerBar {...defaults} {...props} />)
}

describe('RemoveTickerBar', () => {
  beforeEach(() => vi.clearAllMocks())

  it('RemoveTickerBar - selectedCount is 0 - button is disabled', () => {
    renderBar({ selectedCount: 0 })
    expect(screen.getByRole('button', { name: /remove selected/i })).toBeDisabled()
  })

  it('RemoveTickerBar - selectedCount greater than 0 - button is enabled', () => {
    renderBar({ selectedCount: 3 })
    expect(screen.getByRole('button', { name: /remove selected/i })).toBeEnabled()
  })

  it('RemoveTickerBar - clicking enabled button - calls onRemove', async () => {
    const user = userEvent.setup()
    const onRemove = vi.fn()
    renderBar({ selectedCount: 2, onRemove })

    await user.click(screen.getByRole('button', { name: /remove selected/i }))

    expect(onRemove).toHaveBeenCalledOnce()
  })

  it('RemoveTickerBar - isLoading true with selectedCount greater than 0 - button is disabled', () => {
    renderBar({ selectedCount: 2, isLoading: true })
    expect(screen.getByRole('button', { name: /removing/i })).toBeDisabled()
  })
})
