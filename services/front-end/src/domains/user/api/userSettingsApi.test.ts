import { describe, it, expect, vi, beforeEach } from 'vitest'
import axiosInstance from '../../../shared/api/axiosInstance'
import { patchUserSettings } from './userSettingsApi'

vi.mock('../../../shared/api/axiosInstance', () => ({
  default: { patch: vi.fn() },
}))

const mockPatch = vi.mocked(axiosInstance.patch)

describe('patchUserSettings', () => {
  beforeEach(() => vi.clearAllMocks())

  it('patchUserSettings - success - returns UserSettingsResponse', async () => {
    const responseData = { feedType: 'REAL', updatedAt: '2026-06-01T00:00:00Z' }
    mockPatch.mockResolvedValueOnce({ data: responseData })

    const result = await patchUserSettings('u1', { feedType: 'REAL' })

    expect(result).toEqual(responseData)
    expect(mockPatch).toHaveBeenCalledWith('/v1/users/u1/settings', { feedType: 'REAL' })
  })

  it('patchUserSettings - 400 error - throws with error response', async () => {
    const error = Object.assign(new Error('Bad Request'), {
      isAxiosError: true,
      response: { status: 400 },
    })
    mockPatch.mockRejectedValueOnce(error)

    await expect(patchUserSettings('u1', { feedType: 'REAL' })).rejects.toMatchObject({
      response: { status: 400 },
    })
  })

  it('patchUserSettings - 404 error - throws with error response', async () => {
    const error = Object.assign(new Error('Not Found'), {
      isAxiosError: true,
      response: { status: 404 },
    })
    mockPatch.mockRejectedValueOnce(error)

    await expect(patchUserSettings('u1', { feedType: 'SYNTHETIC' })).rejects.toMatchObject({
      response: { status: 404 },
    })
  })
})
