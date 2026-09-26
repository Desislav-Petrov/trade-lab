import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { AccountSelectionBootstrap } from './AccountSelectionBootstrap'

const useDefaultAccountSelectionMock = vi.fn()

vi.mock('../hooks/useDefaultAccountSelection', () => ({
  useDefaultAccountSelection: () => useDefaultAccountSelectionMock(),
}))

describe('AccountSelectionBootstrap', () => {
  it('AccountSelectionBootstrap - mounted - invokes default account selection and renders nothing', () => {
    const { container } = render(<AccountSelectionBootstrap />)

    expect(useDefaultAccountSelectionMock).toHaveBeenCalled()
    expect(container.firstChild).toBeNull()
  })
})
