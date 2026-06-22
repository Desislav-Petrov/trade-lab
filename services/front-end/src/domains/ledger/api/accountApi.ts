import axiosInstance from '../../../shared/api/axiosInstance'
import type { OpenAccountRequest, AccountResponse, AccountListResponse } from '../types/account'

export const ACCOUNTS_QUERY_KEY = 'accounts'

export async function createAccount(request: OpenAccountRequest): Promise<AccountResponse> {
  const response = await axiosInstance.post<AccountResponse>('/v1/accounts', request)
  return response.data
}

export async function fetchAccounts(userId: string): Promise<AccountListResponse> {
  const response = await axiosInstance.get<AccountListResponse>('/v1/accounts', {
    params: { userId },
  })
  return response.data
}
