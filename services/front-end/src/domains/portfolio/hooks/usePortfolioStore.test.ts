import { describe, it, expect, beforeEach } from 'vitest'
import { act } from '@testing-library/react'
import { usePortfolioStore } from './usePortfolioStore'

describe('usePortfolioStore', () => {
  beforeEach(() => {
    act(() => {
      usePortfolioStore.setState({ selectedAccountId: null })
    })
  })

  it('usePortfolioStore - initial state - selectedAccountId is null', () => {
    const state = usePortfolioStore.getState()
    expect(state.selectedAccountId).toBeNull()
  })

  it('usePortfolioStore - setSelectedAccountId - updates selectedAccountId correctly', () => {
    act(() => {
      usePortfolioStore.getState().setSelectedAccountId('acc-123')
    })
    expect(usePortfolioStore.getState().selectedAccountId).toBe('acc-123')
  })

  it('usePortfolioStore - setSelectedAccountId called twice - reflects latest value', () => {
    act(() => {
      usePortfolioStore.getState().setSelectedAccountId('acc-1')
    })
    act(() => {
      usePortfolioStore.getState().setSelectedAccountId('acc-2')
    })
    expect(usePortfolioStore.getState().selectedAccountId).toBe('acc-2')
  })
})
