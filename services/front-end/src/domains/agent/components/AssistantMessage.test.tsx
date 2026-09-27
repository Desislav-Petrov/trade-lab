import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
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

  it('AssistantMessage - completed assistant turn - renders markdown', () => {
    render(
      <AssistantMessage
        turn={{
          id: '2',
          role: 'assistant',
          text: '### Portfolio summary\n\n**Strong buy**\n\n| Holding | Weight |\n| --- | --- |\n| AAPL | 45% |',
          isStreaming: false,
        }}
      />,
    )

    const heading = screen.getByRole('heading', { name: 'Portfolio summary', level: 3 })
    const table = screen.getByRole('table')
    const strong = screen.getByText('Strong buy')

    expect(heading).toBeInTheDocument()
    expect(table).toBeInTheDocument()
    expect(strong.tagName).toBe('STRONG')
    expect(within(table).getByText('AAPL')).toBeInTheDocument()
    expect(screen.queryByText('Streaming…')).not.toBeInTheDocument()
  })

  it('AssistantMessage - assistant turn with embedded HTML - does not render raw HTML', () => {
    const { container } = render(
      <AssistantMessage
        turn={{
          id: '3',
          role: 'assistant',
          text: 'Hello <img src=x onerror=alert(1)> world',
          isStreaming: false,
        }}
      />,
    )

    expect(screen.getByText('Hello <img src=x onerror=alert(1)> world')).toBeInTheDocument()
    expect(container.querySelector('img')).not.toBeInTheDocument()
  })

  it('AssistantMessage - streaming assistant turn - renders streaming indicator', () => {
    render(
      <AssistantMessage turn={{ id: '4', role: 'assistant', text: '', isStreaming: true }} />,
    )

    expect(screen.getByText('Streaming reply…')).toBeInTheDocument()
    expect(screen.getByText('Streaming…')).toBeInTheDocument()
  })
})
