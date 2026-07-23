import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { act, createElement } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { PlatformSettingsSection } from './PlatformSettingsSection'
import { useSessionStore } from '../hooks/useSessionStore'
import type { UserResponse } from '../types/user'

vi.mock('../hooks/usePatchUserSettings', () => ({
  usePatchUserSettings: vi.fn(),
}))

import { usePatchUserSettings } from '../hooks/usePatchUserSettings'
const mockUsePatchUserSettings = vi.mocked(usePatchUserSettings)

const mockUserResponse: UserResponse = {
  userId: 'u1',
  firstName: 'Jane',
  lastName: 'Doe',
  address: '123 Main St',
  email: 'jane@example.com',
  status: 'active',
  createdAt: '2026-01-01T00:00:00Z',
  settings: { feedType: 'SYNTHETIC', updatedAt: '2026-01-01T00:00:00Z' },
}

function renderSection() {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  })
  return render(
    createElement(
      QueryClientProvider,
      { client: queryClient },
      createElement(PlatformSettingsSection, {}),
    ),
  )
}

describe('PlatformSettingsSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    act(() => useSessionStore.getState().clearSession())
  })

  it('PlatformSettingsSection - renders with current feedType from session', () => {
    mockUsePatchUserSettings.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: false,
      isSuccess: false,
      error: null,
    })
    act(() => useSessionStore.getState().setSession(mockUserResponse))

    renderSection()

    expect(screen.getByText('General Platform Settings')).toBeInTheDocument()
    const select = screen.getByRole('combobox')
    expect((select as HTMLSelectElement).value).toBe('SYNTHETIC')
  })

  it('PlatformSettingsSection - calls patchUserSettings on dropdown change', async () => {
    const mockMutate = vi.fn()
    mockUsePatchUserSettings.mockReturnValue({
      mutate: mockMutate,
      isPending: false,
      isError: false,
      isSuccess: false,
      error: null,
    })
    act(() => useSessionStore.getState().setSession(mockUserResponse))

    renderSection()

    const user = userEvent.setup()
    await user.selectOptions(screen.getByRole('combobox'), 'REAL')

    expect(mockMutate).toHaveBeenCalledWith({ feedType: 'REAL' })
  })

  it('PlatformSettingsSection - shows saved confirmation on success', () => {
    mockUsePatchUserSettings.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: false,
      isSuccess: true,
      error: null,
    })
    act(() => useSessionStore.getState().setSession(mockUserResponse))

    renderSection()

    expect(screen.getByRole('status')).toHaveTextContent('Saved')
  })

  it('PlatformSettingsSection - renders nothing when settings is null', () => {
    mockUsePatchUserSettings.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: false,
      isSuccess: false,
      error: null,
    })

    const { container } = renderSection()

    expect(container.firstChild).toBeNull()
  })
})
