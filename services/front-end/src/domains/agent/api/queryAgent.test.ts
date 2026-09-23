import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SESSION_STORAGE_KEY } from '../../user/types/user'
import { AGENT_QUERY_KEY, queryAgent } from './queryAgent'

describe('queryAgent', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.unstubAllGlobals()
  })

  it('queryAgent - streamed response - emits streamed tokens and completes', async () => {
    const onToken = vi.fn()
    const onError = vi.fn()
    const onDone = vi.fn()
    const encoder = new TextEncoder()

    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ accessToken: 'stored-token' }))

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          new ReadableStream({
            start(controller) {
              controller.enqueue(encoder.encode('data: Hel'))
              controller.enqueue(encoder.encode('lo\n\n'))
              controller.close()
            },
          }),
          {
            status: 200,
            headers: { 'Content-Type': 'text/event-stream' },
          },
        ),
      ),
    )

    await queryAgent(
      { accountId: 'acc-1', conversationId: 'conv-1', message: 'Hello' },
      onToken,
      onError,
      onDone,
    )

    const [url, init] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('/api/v1/agent/query')
    expect(init).toMatchObject({
      method: 'POST',
      body: JSON.stringify({ accountId: 'acc-1', conversationId: 'conv-1', message: 'Hello' }),
    })

    const headers = (init as RequestInit).headers as Record<string, string>
    expect(headers.Accept).toBe('text/event-stream, application/json')
    expect(headers.Authorization.startsWith('Bearer ')).toBe(true)
    expect(headers.Authorization.endsWith('stored-token')).toBe(true)
    expect(headers['Content-Type']).toBe('application/json')
    expect(onToken).toHaveBeenCalledTimes(1)
    expect(onToken).toHaveBeenCalledWith('Hello')
    expect(onDone).toHaveBeenCalledTimes(1)
    expect(onError).not.toHaveBeenCalled()
  })

  it('queryAgent - buffered fallback - emits buffered reply and completes', async () => {
    const onToken = vi.fn()
    const onError = vi.fn()
    const onDone = vi.fn()

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ conversationId: 'conv-1', reply: 'Buffered reply' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )

    await queryAgent(
      { accountId: 'acc-1', conversationId: 'conv-1', message: 'Hello' },
      onToken,
      onError,
      onDone,
    )

    expect(onToken).toHaveBeenCalledWith('Buffered reply')
    expect(onDone).toHaveBeenCalledTimes(1)
    expect(onError).not.toHaveBeenCalled()
  })

  it('queryAgent - request failure - propagates error', async () => {
    const onToken = vi.fn()
    const onError = vi.fn()
    const onDone = vi.fn()

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ error: 'Assistant unavailable.' }), {
          status: 503,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )

    await expect(
      queryAgent(
        { accountId: 'acc-1', conversationId: 'conv-1', message: 'Hello' },
        onToken,
        onError,
        onDone,
      ),
    ).rejects.toThrow('Assistant unavailable.')

    expect(onError).toHaveBeenCalledWith(expect.any(Error))
    expect(onDone).not.toHaveBeenCalled()
    expect(onToken).not.toHaveBeenCalled()
  })
})

describe('AGENT_QUERY_KEY', () => {
  it('AGENT_QUERY_KEY - is defined as expected string', () => {
    expect(AGENT_QUERY_KEY).toBe('agentQuery')
  })
})
