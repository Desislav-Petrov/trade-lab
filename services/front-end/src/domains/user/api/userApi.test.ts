import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import { createUser } from './userApi'

vi.mock('../../../shared/api/axiosInstance', () => ({
  default: {
    post: vi.fn(),
  },
}))

import axiosInstance from '../../../shared/api/axiosInstance'

const mockPost = vi.mocked(axiosInstance.post)

describe('createUser', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('createUser - valid request - returns RegisterUserResponse', async () => {
    const userId = '550e8400-e29b-41d4-a716-446655440000'
    mockPost.mockResolvedValueOnce({ data: { userId } })

    const result = await createUser({
      firstName: 'Jane',
      lastName: 'Doe',
      address: '123 Main St',
      email: 'jane@example.com',
    })

    expect(result).toEqual({ userId })
    expect(mockPost).toHaveBeenCalledWith('/v1/users', {
      firstName: 'Jane',
      lastName: 'Doe',
      address: '123 Main St',
      email: 'jane@example.com',
    })
  })

  it('createUser - server returns 409 - throws AxiosError with status 409', async () => {
    const error = Object.assign(new Error('Conflict'), {
      isAxiosError: true,
      response: { status: 409 },
    })
    mockPost.mockRejectedValueOnce(error)

    await expect(
      createUser({ firstName: 'Jane', lastName: 'Doe', address: '123 Main St', email: 'dupe@example.com' })
    ).rejects.toMatchObject({ response: { status: 409 } })
  })

  it('createUser - server returns 400 - throws AxiosError with status 400', async () => {
    const error = Object.assign(new Error('Bad Request'), {
      isAxiosError: true,
      response: { status: 400 },
    })
    mockPost.mockRejectedValueOnce(error)

    await expect(
      createUser({ firstName: '', lastName: 'Doe', address: '123 Main St', email: 'jane@example.com' })
    ).rejects.toMatchObject({ response: { status: 400 } })
  })
})
