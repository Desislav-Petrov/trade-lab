import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import type { AccountListResponse } from '../../domains/ledger/types/account'
import { useDefaultAccountSelection } from './useDefaultAccountSelection'
import { useSelectedAccountStore } from './useSelectedAccountStore'

vi.mock('../../domains/ledger/hooks/useLedger', () => ({
  useActiveAccounts: vi.fn(),
}))

import { useActiveAccounts } from '../../domains/ledger/hooks/useLedger'

const mockUseActiveAccounts = vi.mocked(useActiveAccounts)

function makeAccount(id: string): AccountListResponse['accounts'][number] {
  return {
    id,
    name: `Account ${id}`,
    currency: 'USD',
    balance: 1000,
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00Z',
  }
}

function mockAccounts(data: AccountListResponse | undefined): void {
  mockUseActiveAccounts.mockReturnValue({ data } as ReturnType<typeof useActiveAccounts>)
}

describe('useDefaultAccountSelection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useSelectedAccountStore.getState().clearSelectedAccountId()
  })

  it('useDefaultAccountSelection - no account selected and accounts available - seeds first active account', () => {
    mockAccounts({ accounts: [makeAccount('acc-1'), makeAccount('acc-2')] })

    renderHook(() => useDefaultAccountSelection())

    expect(useSelectedAccountStore.getState().selectedAccountId).toBe('acc-1')
  })

  it('useDefaultAccountSelection - account already selected - does not override selection', () => {
    useSelectedAccountStore.getState().setSelectedAccountId('acc-existing')
    mockAccounts({ accounts: [makeAccount('acc-1')] })

    renderHook(() => useDefaultAccountSelection())

    expect(useSelectedAccountStore.getState().selectedAccountId).toBe('acc-existing')
  })

  it('useDefaultAccountSelection - no accounts available - leaves selection null', () => {
    mockAccounts({ accounts: [] })

    renderHook(() => useDefaultAccountSelection())

    expect(useSelectedAccountStore.getState().selectedAccountId).toBeNull()
  })

  it('useDefaultAccountSelection - accounts still loading - leaves selection null', () => {
    mockAccounts(undefined)

    renderHook(() => useDefaultAccountSelection())

    expect(useSelectedAccountStore.getState().selectedAccountId).toBeNull()
  })
})
