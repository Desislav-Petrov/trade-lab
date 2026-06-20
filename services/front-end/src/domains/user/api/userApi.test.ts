import { describe, it, expect, vi, beforeEach } from 'vitest'
import axiosInstance from '../../../shared/api/axiosInstance'
import { getActiveUserEmails, loginUser, fetchUserById } from './userApi'

vi.mock('../../../shared/api/axiosInstance', () => ({
  default: { post: vi.fn(), get: vi.fn() },
}))

const mockGet = vi.mocked(axiosInstance.get)
const mockPost = vi.mocked(axiosInstance.post)

describe('getActiveUserEmails', () => {
  beforeEach(() => vi.clearAllMocks())

  it('getActiveUserEmails - success - returns emails array', async () => {
    const emails = ['a@example.com', 'b@example.com']
    mockGet.mockResolvedValueOnce({ data: { emails } })

    const result = await getActiveUserEmails()

    expect(result).toEqual({ emails })
    expect(mockGet).toHaveBeenCalledWith('/v1/users/emails')
  })

  it('getActiveUserEmails - empty list - returns empty array', async () => {
    mockGet.mockResolvedValueOnce({ data: { emails: [] } })

    const result = await getActiveUserEmails()

    expect(result).toEqual({ emails: [] })
  })
})

describe('loginUser', () => {
  beforeEach(() => vi.clearAllMocks())

  it('loginUser - valid email - returns LoginResponse', async () => {
    const payload = { userId: '550e8400-e29b-41d4-a716-446655440000', email: 'a@example.com' }
    mockPost.mockResolvedValueOnce({ data: payload })

    const result = await loginUser({ email: 'a@example.com' })

    expect(result).toEqual(payload)
    expect(mockPost).toHaveBeenCalledWith('/v1/users/login', { email: 'a@example.com' })
  })

  it('loginUser - 404 response - throws AxiosError with status 404', async () => {
    const error = Object.assign(new Error('Not Found'), {
      isAxiosError: true,
      response: { status: 404 },
    })
    mockPost.mockRejectedValueOnce(error)

    await expect(loginUser({ email: 'ghost@example.com' })).rejects.toMatchObject({
      response: { status: 404 },
    })
  })

  it('loginUser - 403 response - throws AxiosError with status 403', async () => {
    const error = Object.assign(new Error('Forbidden'), {
      isAxiosError: true,
      response: { status: 403 },
    })
    mockPost.mockRejectedValueOnce(error)

    await expect(loginUser({ email: 'closed@example.com' })).rejects.toMatchObject({
      response: { status: 403 },
    })
  })
})

describe('fetchUserById', () => {
  beforeEach(() => vi.clearAllMocks())

  it('fetchUserById - success - returns UserProfile', async () => {
    const profile = {
      userId: 'u1',
      firstName: 'Jane',
      lastName: 'Doe',
      address: '123 Main St',
      email: 'jane@example.com',
      status: 'active',
      createdAt: '2026-01-01T00:00:00Z',
    }
    mockGet.mockResolvedValueOnce({ data: profile })

    const result = await fetchUserById('u1')

    expect(result).toEqual(profile)
    expect(mockGet).toHaveBeenCalledWith('/v1/users/u1')
  })

  it('fetchUserById - 404 - throws AxiosError', async () => {
    const error = Object.assign(new Error('Not Found'), {
      isAxiosError: true,
      response: { status: 404 },
    })
    mockGet.mockRejectedValueOnce(error)

    await expect(fetchUserById('unknown')).rejects.toMatchObject({
      response: { status: 404 },
    })
  })
})
