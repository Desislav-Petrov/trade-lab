import { describe, it, expect, vi, beforeEach } from 'vitest'
import axiosInstance from '../../../shared/api/axiosInstance'
import { createAccount, fetchAccounts } from './accountApi'

vi.mock('../../../shared/api/axiosInstance', () => ({
  default: { post: vi.fn(), get: vi.fn() },
}))

const mockGet = vi.mocked(axiosInstance.get)
const mockPost = vi.mocked(axiosInstance.post)

describe('createAccount', () => {
  beforeEach(() => vi.clearAllMocks())

  it('createAccount - success - returns AccountResponse', async () => {
    const response = {
      accountId: 'acc-1',
      name: 'My Account',
      currency: 'USD',
      balance: 0,
      status: 'active',
      createdAt: '2026-01-01T00:00:00Z',
    }
    mockPost.mockResolvedValueOnce({ data: response })

    const result = await createAccount({ userId: 'u1', currency: 'USD', name: 'My Account' })

    expect(result).toEqual(response)
    expect(mockPost).toHaveBeenCalledWith('/v1/accounts', {
      userId: 'u1',
      currency: 'USD',
      name: 'My Account',
    })
  })

  it('createAccount - without name - posts without name field', async () => {
    const response = {
      accountId: 'acc-2',
      name: 'USD Account',
      currency: 'USD',
      balance: 0,
      status: 'active',
      createdAt: '2026-01-01T00:00:00Z',
    }
    mockPost.mockResolvedValueOnce({ data: response })

    const result = await createAccount({ userId: 'u1', currency: 'USD' })

    expect(result).toEqual(response)
    expect(mockPost).toHaveBeenCalledWith('/v1/accounts', { userId: 'u1', currency: 'USD' })
  })

  it('createAccount - 400 response - throws AxiosError with status 400', async () => {
    const error = Object.assign(new Error('Bad Request'), {
      isAxiosError: true,
      response: { status: 400 },
    })
    mockPost.mockRejectedValueOnce(error)

    await expect(createAccount({ userId: 'u1', currency: 'USD' })).rejects.toMatchObject({
      response: { status: 400 },
    })
  })
})

describe('fetchAccounts', () => {
  beforeEach(() => vi.clearAllMocks())

  it('fetchAccounts - success - returns AccountListResponse', async () => {
    const accounts = [
      {
        accountId: 'acc-1',
        name: 'My Account',
        currency: 'USD',
        balance: 100,
        status: 'active',
        createdAt: '2026-01-01T00:00:00Z',
      },
    ]
    mockGet.mockResolvedValueOnce({ data: { accounts } })

    const result = await fetchAccounts('u1')

    expect(result).toEqual({ accounts })
    expect(mockGet).toHaveBeenCalledWith('/v1/accounts', { params: { userId: 'u1' } })
  })

  it('fetchAccounts - empty list - returns empty array', async () => {
    mockGet.mockResolvedValueOnce({ data: { accounts: [] } })

    const result = await fetchAccounts('u1')

    expect(result).toEqual({ accounts: [] })
  })

  it('fetchAccounts - 401 response - throws AxiosError with status 401', async () => {
    const error = Object.assign(new Error('Unauthorized'), {
      isAxiosError: true,
      response: { status: 401 },
    })
    mockGet.mockRejectedValueOnce(error)

    await expect(fetchAccounts('u1')).rejects.toMatchObject({
      response: { status: 401 },
    })
  })
})
