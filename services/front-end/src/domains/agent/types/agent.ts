export interface QueryAgentRequest {
  accountId: string
  conversationId: string
  message: string
}

export interface QueryAgentResponse {
  conversationId: string
  reply: string
}

export type TurnRole = 'user' | 'assistant'

export interface Turn {
  id: string
  role: TurnRole
  text: string
  isStreaming: boolean
}
