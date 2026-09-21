import { describe, it, expect, beforeEach } from 'vitest'
import { act } from '@testing-library/react'
import { usePortfolioStore } from './usePortfolioStore'
import { useSelectedAccountStore } from '../../../shared/hooks/useSelectedAccountStore'

describe('usePortfolioStore', () => {
  beforeEach(() => {
    act(() => {
      usePortfolioStore.setState({ selectedAccountId: null, hiddenSymbols: new Set() })
      useSelectedAccountStore.getState().clearSelectedAccountId()
    })
  })

  it('usePortfolioStore - initial state - selectedAccountId is null', () => {
    const state = usePortfolioStore.getState()
    expect(state.selectedAccountId).toBeNull()
    expect(state.hiddenSymbols).toEqual(new Set())
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

  it('usePortfolioStore - setSelectedAccountId - syncs selectedAccountId to shared store', () => {
    act(() => {
      usePortfolioStore.getState().setSelectedAccountId('acc-123')
    })

    expect(useSelectedAccountStore.getState().selectedAccountId).toBe('acc-123')
  })

  it('usePortfolioStore - shared store update - syncs selectedAccountId back to portfolio store', () => {
    act(() => {
      useSelectedAccountStore.getState().setSelectedAccountId('acc-456')
    })

    expect(usePortfolioStore.getState().selectedAccountId).toBe('acc-456')
  })

  it('usePortfolioStore - toggleSymbolVisibility - adds ticker when visible', () => {
    act(() => {
      usePortfolioStore.getState().toggleSymbolVisibility('AAPL')
    })

    expect(usePortfolioStore.getState().hiddenSymbols).toEqual(new Set(['AAPL']))
  })

  it('usePortfolioStore - toggleSymbolVisibility called twice - removes ticker', () => {
    act(() => {
      usePortfolioStore.getState().toggleSymbolVisibility('AAPL')
      usePortfolioStore.getState().toggleSymbolVisibility('AAPL')
    })

    expect(usePortfolioStore.getState().hiddenSymbols).toEqual(new Set())
  })

  it('usePortfolioStore - resetSymbolVisibility - clears hidden symbols', () => {
    act(() => {
      usePortfolioStore.getState().toggleSymbolVisibility('AAPL')
      usePortfolioStore.getState().toggleSymbolVisibility('MSFT')
      usePortfolioStore.getState().resetSymbolVisibility()
    })

    expect(usePortfolioStore.getState().hiddenSymbols).toEqual(new Set())
  })
})
