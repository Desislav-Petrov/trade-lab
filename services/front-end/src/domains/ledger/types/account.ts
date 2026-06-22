export interface OpenAccountRequest {
  userId: string
  currency: 'USD' | 'GBP' | 'EUR'
  name?: string
}

export interface AccountResponse {
  accountId: string
  name: string
  currency: string
  balance: number
  status: string
  createdAt: string
}

export interface AccountListResponse {
  accounts: AccountResponse[]
}
