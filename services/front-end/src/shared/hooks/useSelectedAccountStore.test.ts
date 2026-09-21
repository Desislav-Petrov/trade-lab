import { beforeEach, describe, expect, it } from 'vitest'
import { act } from '@testing-library/react'
import { useSelectedAccountStore } from './useSelectedAccountStore'

describe('useSelectedAccountStore', () => {
  beforeEach(() => {
    act(() => {
      useSelectedAccountStore.getState().clearSelectedAccountId()
    })
  })

  it('useSelectedAccountStore - initial state - selectedAccountId is null', () => {
    expect(useSelectedAccountStore.getState().selectedAccountId).toBeNull()
  })

  it('useSelectedAccountStore - setSelectedAccountId - updates selectedAccountId', () => {
    act(() => {
      useSelectedAccountStore.getState().setSelectedAccountId('acc-123')
    })

    expect(useSelectedAccountStore.getState().selectedAccountId).toBe('acc-123')
  })

  it('useSelectedAccountStore - clearSelectedAccountId - resets selectedAccountId', () => {
    act(() => {
      useSelectedAccountStore.getState().setSelectedAccountId('acc-123')
      useSelectedAccountStore.getState().clearSelectedAccountId()
    })

    expect(useSelectedAccountStore.getState().selectedAccountId).toBeNull()
  })
})
