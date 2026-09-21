import { beforeEach, describe, expect, it } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { AssistantWidget } from './AssistantWidget'
import { useAgentStore } from '../hooks/useAgentStore'

describe('AssistantWidget', () => {
  beforeEach(() => {
    useAgentStore.getState().reset()
  })

  it('AssistantWidget - click launcher - toggles popup state', () => {
    render(<AssistantWidget />)

    const button = screen.getByRole('button', { name: 'Trade Lab AI Assistant' })
    expect(button).toBeInTheDocument()

    fireEvent.click(button)

    expect(useAgentStore.getState().isOpen).toBe(true)
  })
})
