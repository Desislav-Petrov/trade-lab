import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AssistantMessage } from './AssistantMessage'

describe('AssistantMessage', () => {
  it('AssistantMessage - user turn - renders the user message', () => {
    render(
      <AssistantMessage
        turn={{ id: '1', role: 'user', text: 'Hello assistant', isStreaming: false }}
      />,
    )

    expect(screen.getByText('Hello assistant')).toBeInTheDocument()
  })

  it('AssistantMessage - completed assistant turn - renders assistant reply', () => {
    render(
      <AssistantMessage
        turn={{ id: '2', role: 'assistant', text: 'Hello trader', isStreaming: false }}
      />,
    )

    expect(screen.getByText('Hello trader')).toBeInTheDocument()
    expect(screen.queryByText('Streaming…')).not.toBeInTheDocument()
  })

  it('AssistantMessage - streaming assistant turn - renders streaming indicator', () => {
    render(
      <AssistantMessage turn={{ id: '3', role: 'assistant', text: '', isStreaming: true }} />,
    )

    expect(screen.getByText('Streaming reply…')).toBeInTheDocument()
    expect(screen.getByText('Streaming…')).toBeInTheDocument()
  })
})
