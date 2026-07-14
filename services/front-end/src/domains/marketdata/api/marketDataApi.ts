import axiosInstance from '../../../shared/api/axiosInstance'

export interface IndicativePriceResponse {
  ticker: string
  indicativePrice: number
}

export async function fetchIndicativePrice(ticker: string): Promise<IndicativePriceResponse> {
  const response = await axiosInstance.get<IndicativePriceResponse>(
    '/v1/market-data/price',
    { params: { ticker } }
  )
  return response.data
}
