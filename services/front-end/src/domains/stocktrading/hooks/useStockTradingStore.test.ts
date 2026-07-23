import { describe, it, expect, beforeEach } from 'vitest'
import { act } from '@testing-library/react'
import { useStockTradingStore } from './useStockTradingStore'

describe('useStockTradingStore', () => {
  beforeEach(() => {
    act(() => useStockTradingStore.getState().clearSelectedAccountId())
  })

  it('useStockTradingStore - initial state - selectedAccountId is null', () => {
    const { selectedAccountId } = useStockTradingStore.getState()
    expect(selectedAccountId).toBeNull()
  })

  it('useStockTradingStore - setSelectedAccountId - updates selectedAccountId', () => {
    act(() => useStockTradingStore.getState().setSelectedAccountId('acc-123'))
    expect(useStockTradingStore.getState().selectedAccountId).toBe('acc-123')
  })

  it('useStockTradingStore - clearSelectedAccountId - resets selectedAccountId to null', () => {
    act(() => useStockTradingStore.getState().setSelectedAccountId('acc-123'))
    act(() => useStockTradingStore.getState().clearSelectedAccountId())
    expect(useStockTradingStore.getState().selectedAccountId).toBeNull()
  })
})
