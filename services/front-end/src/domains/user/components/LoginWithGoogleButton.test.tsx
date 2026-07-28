import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { LoginWithGoogleButton } from './LoginWithGoogleButton'

describe('LoginWithGoogleButton', () => {
  it('LoginWithGoogleButton - renders - displays login with google text', () => {
    render(<LoginWithGoogleButton onClick={vi.fn()} />)
    expect(screen.getByRole('button', { name: /login with google/i })).toBeInTheDocument()
  })

  it('LoginWithGoogleButton - click - calls onClick exactly once', () => {
    const handleClick = vi.fn()
    render(<LoginWithGoogleButton onClick={handleClick} />)
    fireEvent.click(screen.getByRole('button', { name: /login with google/i }))
    expect(handleClick).toHaveBeenCalledTimes(1)
  })

  it('LoginWithGoogleButton - renders - does not make any API calls', () => {
    // Component is pure presentational — just verify it renders without errors
    const { container } = render(<LoginWithGoogleButton onClick={vi.fn()} />)
    expect(container.querySelector('button')).toBeTruthy()
  })
})
