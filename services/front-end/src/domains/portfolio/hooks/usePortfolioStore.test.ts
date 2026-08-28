import { describe, it, expect, beforeEach } from 'vitest'
import { act } from '@testing-library/react'
import { usePortfolioStore } from './usePortfolioStore'

describe('usePortfolioStore', () => {
  beforeEach(() => {
    act(() => {
      usePortfolioStore.setState({ selectedAccountId: null, hiddenSymbols: new Set() })
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
