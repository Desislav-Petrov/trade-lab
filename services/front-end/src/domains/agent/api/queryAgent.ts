import { getSessionAuthorizationHeader } from '../../../shared/api/sessionAuth'
import type { QueryAgentRequest, QueryAgentResponse } from '../types/agent'

export const AGENT_QUERY_KEY = 'agentQuery'

type TokenHandler = (token: string) => void
type ErrorHandler = (error: Error) => void
type DoneHandler = () => void

function extractToken(data: string): string {
  const trimmed = data.trim()
  if (trimmed.length === 0 || trimmed === '[DONE]') return ''

  try {
    const parsed = JSON.parse(trimmed) as
      | string
      | {
          token?: string
          content?: string
          reply?: string
          message?: string
          delta?: { content?: string }
        }

    if (typeof parsed === 'string') return parsed
    if (typeof parsed.token === 'string') return parsed.token
    if (typeof parsed.content === 'string') return parsed.content
    if (typeof parsed.reply === 'string') return parsed.reply
    if (typeof parsed.message === 'string') return parsed.message
    if (typeof parsed.delta?.content === 'string') return parsed.delta.content
  } catch {
    return trimmed
  }

  return ''
}

function flushSseBuffer(buffer: string, onToken: TokenHandler, flushAll: boolean = false): string {
  const normalized = buffer.replace(/\r\n/g, '\n')
  const parts = normalized.split('\n\n')
  const completeEvents = flushAll ? parts : parts.slice(0, -1)
  const remainder = flushAll ? '' : (parts[parts.length - 1] ?? '')

  completeEvents.forEach((eventBlock) => {
    const data = eventBlock
      .split('\n')
      .map((line) => line.trimStart())
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trimStart())
      .join('\n')

    const token = extractToken(data)
    if (token.length > 0) onToken(token)
  })

  return remainder
}

function parseBufferedReply(text: string): string {
  try {
    const parsed = JSON.parse(text) as QueryAgentResponse
    return parsed.reply
  } catch {
    return text
  }
}

async function toResponseError(response: Response): Promise<Error> {
  const text = await response.text()
  if (text.length === 0) return new Error('Assistant unavailable.')

  try {
    const parsed = JSON.parse(text) as { error?: string }
    return new Error(parsed.error ?? 'Assistant unavailable.')
  } catch {
    return new Error(text)
  }
}

function toError(error: unknown): Error {
  return error instanceof Error ? error : new Error('Assistant unavailable.')
}

export async function queryAgent(
  request: QueryAgentRequest,
  onToken: TokenHandler,
  onError: ErrorHandler,
  onDone: DoneHandler,
): Promise<void> {
  try {
    const headers: Record<string, string> = {
      Accept: 'text/event-stream, application/json',
      'Content-Type': 'application/json',
    }

    const authorizationHeader = getSessionAuthorizationHeader()
    if (authorizationHeader) {
      headers.Authorization = authorizationHeader
    }

    const response = await fetch('/api/v1/agent/query', {
      method: 'POST',
      headers,
      body: JSON.stringify(request),
    })

    if (!response.ok) {
      throw await toResponseError(response)
    }

    const contentType = response.headers.get('content-type')?.toLowerCase() ?? ''
    if (contentType.includes('text/event-stream')) {
      if (!response.body) {
        onDone()
        return
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { value, done } = await reader.read()
        if (done) break

        buffer = flushSseBuffer(buffer + decoder.decode(value, { stream: true }), onToken)
      }

      buffer += decoder.decode()
      flushSseBuffer(buffer, onToken, true)
      onDone()
      return
    }

    const reply = parseBufferedReply(await response.text())
    if (reply.length > 0) onToken(reply)
    onDone()
  } catch (error) {
    const agentError = toError(error)
    onError(agentError)
    throw agentError
  }
}
