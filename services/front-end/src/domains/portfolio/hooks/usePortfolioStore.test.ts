import { describe, it, expect, beforeEach } from 'vitest'
import { act } from '@testing-library/react'
import { usePortfolioStore } from './usePortfolioStore'

describe('usePortfolioStore', () => {
  beforeEach(() => {
    act(() => usePortfolioStore.setState({ selectedAccountId: null }))
  })

  it('usePortfolioStore - initial state - selectedAccountId is null', () => {
    const { selectedAccountId } = usePortfolioStore.getState()
    expect(selectedAccountId).toBeNull()
  })

  it('usePortfolioStore - setSelectedAccountId - updates selectedAccountId', () => {
    act(() => usePortfolioStore.getState().setSelectedAccountId('acc-123'))
    expect(usePortfolioStore.getState().selectedAccountId).toBe('acc-123')
  })
})
